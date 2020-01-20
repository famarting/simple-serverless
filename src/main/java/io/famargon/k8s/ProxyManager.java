package io.famargon.k8s;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.famargon.k8s.cache.DeploymentsStatusCache;
import io.famargon.k8s.resource.Serverless;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;

public class ProxyManager {
    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private Vertx vertx;
    private Map<String, Serverless> cache;
    private KubernetesClient kubernetesClient;
    private DeploymentsStatusCache deploymentsCache;

    private Map<String, Queue<HttpServerRequest>> queues = new ConcurrentHashMap<>();
    private Map<String, Worker> workers = new ConcurrentHashMap<>();
    private Map<String, ReplicasChecker> replicasCheckers = new ConcurrentHashMap<>();


    public ProxyManager(KubernetesClient kubernetesClient, Map<String, Serverless> cache, Vertx vertx, DeploymentsStatusCache deploymentsCache) {
        this.kubernetesClient = kubernetesClient;
        this.cache = cache;
        this.vertx = vertx;
        this.deploymentsCache = deploymentsCache;
    }

    public void startServer() {
        vertx.createHttpServer(new HttpServerOptions().setPort(8080)).requestHandler(this::proxyRequest)
                .listen(server -> {
                    if (server.succeeded()) {
                        logger.info("Proxy server started on port 8080");
                    } else {
                        logger.error("Error starting server", server.cause());
                    }
                });
    }

    private void proxyRequest(HttpServerRequest req) {
        String targetHost = req.getHeader("x-host");
        if (targetHost == null || targetHost.isBlank()) {
            req.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end("missing x-host header");
            return;
        } else {
            var serverless = cache.get(targetHost);
            if (serverless == null) {
                req.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end("Serverless target doesn't exists");
                return;
            }
            req.pause();
            scale(serverless);
            enqueue(serverless, req);
            checkQueue(serverless);
        }
    }

    public void deleteServerless(Serverless serverless) {
        ReplicasChecker checker = replicasCheckers.remove(serverless.getMetadata().getName());
        if (checker!=null) {
            checker.stop();
        }
        queues.remove(serverless.getMetadata().getName());
        workers.remove(serverless.getMetadata().getName());
    }

    private void scale(Serverless serverless) {
        replicasCheckers.computeIfAbsent(serverless.getMetadata().getName(), k -> {
            return new ReplicasChecker(serverless);
        }).init();
    }

    private void enqueue(Serverless serverless, HttpServerRequest req) {
        queues.computeIfAbsent(serverless.getMetadata().getName(), k -> {
            return new ConcurrentLinkedQueue<HttpServerRequest>();
        }).offer(req);
    }

    private void checkQueue(Serverless serverless) {
        workers.computeIfAbsent(serverless.getMetadata().getName(), k -> {
            return new Worker(serverless);
        }).check();
    }

    private class Worker {
        private Serverless serverless;
        private Queue<HttpServerRequest> queue;
        private ReplicasChecker replicasChecker;
        private ServerlessStats stats;
        private IngressProxy proxy;

        private AtomicBoolean busy = new AtomicBoolean(false);

        public Worker(Serverless serverless) {
            this.serverless = serverless;
            this.queue = queues.get(serverless.getMetadata().getName());
            this.replicasChecker = replicasCheckers.get(serverless.getMetadata().getName());
            this.stats = this.replicasChecker.stats;
            this.proxy = new IngressProxy(vertx, serverless);
        }

        public void check() {
            if (busy.get()) {
                return;
            }
            busy.set(true);
            if (isDeployed()) {
                consumeQueue();
            } else {
                waitUntilIsDeployed()
                    .setHandler(ar -> {
                       if (ar.succeeded()) {
                           consumeQueue();
                       } else {
                           logger.error("", ar.cause());
                           busy.set(false);
                       }
                    });
            }
        }

        private void consumeQueue() {
            HttpServerRequest request = queue.poll();
            while (request!=null) {
                final HttpServerRequest req = request;
                vertx.runOnContext(e-> {
                    req.resume();
                    proxy.doProxy(req,
                            v -> {
                                stats.setLastRequest(Instant.now());
                                stats.getInFlightRequests().incrementAndGet();
                            },
                            v -> {
                                stats.getInFlightRequests().decrementAndGet();
                            });
                });

                request = queue.poll();
            }
            busy.set(false);
        }

        private boolean isDeployed() {
            return deploymentsCache.getAvailablePods(serverless.getStatus().getDeploymentName()) > 0;
        }

        private Future<Void> waitUntilIsDeployed() {
            Promise<Void> result = Promise.promise();
            String uuid = deploymentsCache.subscribe(serverless.getStatus().getDeploymentName(), (id, d) -> {
                Integer r = d.getStatus().getAvailableReplicas();
                if (r != null && r > 0) {
                    deploymentsCache.unsubscribe(serverless.getStatus().getDeploymentName(), id);
                    result.complete();
                }
            });
            vertx.setTimer(45000, a -> {
                deploymentsCache.unsubscribe(serverless.getStatus().getDeploymentName(), uuid);
                result.tryFail("Time out wait for deployment exceeded");
            });
            return result.future();
        }

    }

    private class ReplicasChecker {
        private Serverless serverless;
        private ServerlessStats stats;

        private long jobId;
        private AtomicBoolean busy = new AtomicBoolean(false);

        public ReplicasChecker(Serverless serverless) {
            this.serverless = serverless;
            this.stats = new ServerlessStats(serverless);
            this.jobId = vertx.setPeriodic(1000, v -> check());
        }

        public void init() {
            if (stats.getDesiredReplicas().compareAndSet(0, 1)) {
                scale(1);
            }
            stats.setLastRequest(Instant.now());
        }

        public void check() {
            if (busy.get()) {
                return;
            }
            busy.set(true);

            checkReplicas();

            busy.set(false);
        }

        private void checkReplicas() {
            int totalInFlightRequests = stats.getInFlightRequests().get();
            if (totalInFlightRequests>0 && totalInFlightRequests>=serverless.getSpec().getInFlightRequestsPerPod()) {
                int requiredPods = totalInFlightRequests / serverless.getSpec().getInFlightRequestsPerPod();
                int currentPods = stats.getDesiredReplicas().get();
                if (currentPods != requiredPods) {
                    int availablePods = deploymentsCache.getAvailablePods(serverless.getStatus().getDeploymentName());
                    if (stats.getDesiredReplicas().compareAndSet(availablePods, requiredPods)) {
                        logger.info("Scaling to {} pods, total in-flight reqs {} ", requiredPods, totalInFlightRequests);
                        scale(requiredPods);
                    } else {
                        logger.info("Available pods is not equal to desired replicas, dissmising scaling to {} pods, total in-flight {}", requiredPods, totalInFlightRequests);
                    }
                }
            } else if (totalInFlightRequests == 0) {
                var lastRequest = stats.getLastRequest();
                var now = Instant.now();
                var gracePeriod = parseDurationToSeconds(serverless.getSpec().getScaleToZeroGracePerdiod());
                if (ChronoUnit.SECONDS.between(lastRequest, now) >= gracePeriod) {
                    int oldValue = stats.getDesiredReplicas().getAndSet(0);
                    if (oldValue != 0) {
                        logger.info("Grace period expired, scaling down");
                        scale(0);
                    }
                }
            }
        }

        public void scale(int requiredPods) {
            vertx.runOnContext(a -> {
//                logger.info("Scaling deployment {} to {} required pods", serverless.getStatus().getDeploymentName(), requiredPods);
                kubernetesClient.apps().deployments()
                    .inNamespace(serverless.getMetadata().getNamespace())
                    .withName(serverless.getStatus().getDeploymentName())
                    .scale(requiredPods);
            });
        }

        public void stop() {
            vertx.cancelTimer(jobId);
        }

        private long parseDurationToSeconds(String duration) {
            Matcher digitsMatcher = Pattern.compile("\\d+").matcher(duration);
            String digit;
            if (digitsMatcher.find()) {
                digit = digitsMatcher.group();
            } else {
                throw new IllegalArgumentException("Invalid value, no digits");
            }

            Matcher unitMatcher = Pattern.compile("[a-z]").matcher(duration);
            String unit;
            if (unitMatcher.find()) {
                unit = unitMatcher.group();
            } else {
                throw new IllegalArgumentException("Invalid value, no unit");
            }

            long totalSeconds = 0;

            if (unit.equals("s")) {
                totalSeconds = Integer.parseInt(digit);
            } else if (unit.equals("ms")){
                totalSeconds = Integer.parseInt(digit) / 1000;
            } else {
                throw new IllegalArgumentException("Invalid value, unsupported unit");
            }

            return totalSeconds;
        }

    }

}
