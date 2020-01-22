package io.famargon.k8s;

import static io.famargon.k8s.Utils.parseToSeconds;
import static io.famargon.k8s.Utils.tail;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.famargon.k8s.cache.DeploymentsCache;
import io.famargon.k8s.resource.Serverless;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;

public class ProxyManager implements ServerlessService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private Vertx vertx;
    private KubernetesClient kubernetesClient;
    private DeploymentsCache deploymentsCache;

    private Map<String, Serverless> cache = new ConcurrentHashMap<>();
    private Map<String, Queue<ServerlessRequest>> queues = new ConcurrentHashMap<>();
    private Map<String, Worker> workers = new ConcurrentHashMap<>();
    private Map<String, Autoscaler> autoscalers = new ConcurrentHashMap<>();

    public ProxyManager(KubernetesClient kubernetesClient, Vertx vertx, DeploymentsCache deploymentsCache) {
        this.kubernetesClient = kubernetesClient;
        this.vertx = vertx;
        this.deploymentsCache = deploymentsCache;
    }

    @Override
    public void create(Serverless serverless) {
        cache.put(serverless.getSpec().getHostname(), serverless);
    }

    @Override
    public void delete(Serverless serverless) {
        Autoscaler autoscaler = autoscalers.remove(serverless.getMetadata().getName());
        if (autoscaler!=null) {
            autoscaler.stop();
        }
        queues.remove(serverless.getMetadata().getName());
        workers.remove(serverless.getMetadata().getName());
        cache.remove(serverless.getSpec().getHostname());
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
            ServerlessRequest srequest = new ServerlessRequest(req, serverless);
            scale(serverless);
            enqueue(srequest);
            checkQueue(srequest);
        }
    }

    private void scale(Serverless serverless) {
        autoscalers.computeIfAbsent(serverless.getMetadata().getName(), k -> {
            return new Autoscaler(serverless);
        }).init();
    }

    private void enqueue(ServerlessRequest srequest) {
        queues.computeIfAbsent(srequest.serverless().getMetadata().getName(), k -> {
            return new ConcurrentLinkedQueue<ServerlessRequest>();
        }).offer(srequest);
    }

    private void checkQueue(ServerlessRequest srequest) {
        workers.computeIfAbsent(srequest.serverless().getMetadata().getName(), k -> {
            return new Worker(srequest.serverless());
        }).check();
    }

    private class Worker {
        private Serverless serverless;
        private Queue<ServerlessRequest> queue;
        private Autoscaler replicasChecker;
        private ServerlessStats stats;
        private HttpProxy proxy;

        private AtomicBoolean busy = new AtomicBoolean(false);

        public Worker(Serverless serverless) {
            this.serverless = serverless;
            this.queue = queues.get(serverless.getMetadata().getName());
            this.replicasChecker = autoscalers.get(serverless.getMetadata().getName());
            this.stats = this.replicasChecker.stats;
            this.proxy = new HttpProxy(vertx, serverless);
        }

        public void check() {
            if (busy.get()) {
                logger.info("Worker busy");
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
            ServerlessRequest request = queue.poll();
            while (request!=null) {
                final var sreq = request;
                vertx.runOnContext(e-> {
                    sreq.request().resume();
                    proxy.doProxy(sreq.request(),
                            v -> {
                                stats.getConcurrentRequests().incrementAndGet();
                            },
                            v -> {
                                stats.getConcurrentRequests().decrementAndGet();
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
            logger.info("Waiting until serverless is deployed");
            Promise<Void> result = Promise.promise();
            String uuid = deploymentsCache.subscribe(serverless.getStatus().getDeploymentName(), (id, d) -> {
                Integer r = d.getStatus().getAvailableReplicas();
                if (r != null && r > 0) {
                    logger.info("Serverless deployed");
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

    private class Autoscaler {

        //two modes of working stable and panic
        //4 times every second get in-flight requests to calculate average concurrent requests per second
        //store the averages calculated for the last 60s (duration of stable window)
        //every 2s (tick interval) compare with the concurrencyTarget or 2xconcurrencyTarget to determine which working mode is enabled
        //adjust the size of the deployment using the stable window or the panic window
        //in panic mode scaling up is the only option
        //to come back to stable mode a stable window of time has to pass since the last scaling up in panic mode
        //being in stable mode and after having 0 average of concurrent requests for scale-to-zero-grace-period the deployment is scaled to 0

        private AutoscalerWorkingMode mode = AutoscalerWorkingMode.STABLE;

        private Serverless serverless;
        private ServerlessStats stats;

        private List<Long> jobsId;

        public Autoscaler(Serverless serverless) {
            this.serverless = serverless;
            this.stats = new ServerlessStats(serverless);
            this.jobsId = Arrays.asList(
                    vertx.setPeriodic(250, v -> generateStats()),
                    vertx.setPeriodic(1000, v -> updateStats()),
                    vertx.setPeriodic(parseToSeconds(serverless.getSpec().getTickInterval()) * 1000, v -> tick()));
        }

        public void init() {
            if (stats.getDesiredReplicas().compareAndSet(0, 1)) {
                stats.setLastScaling(Instant.now());
                scale(1);
                mode = AutoscalerWorkingMode.PANIC;
                logger.info("Starting in {} mode", mode);
            }
        }

        private void tick() {
            var now = Instant.now();
            double concurrencyTarget = serverless.getSpec().getConcurrencyTarget();
            double stableWindowAverageConcurrency = stats.getConcurrencyValues().stream().mapToDouble(v->v).average().orElse(0);
            double panicWindowAverageConcurrency = tail(stats.getConcurrencyValues(), parseToSeconds(serverless.getSpec().getPanicWindow()))
                    .stream().mapToDouble(v->v).average().orElse(0);

            if (mode == AutoscalerWorkingMode.STABLE) {
                if (concurrencyTarget*2 <= panicWindowAverageConcurrency) {
                    mode = AutoscalerWorkingMode.PANIC;
                    logger.info("Switching to {} mode", mode);
                    logger.info("Stable window concurrency {} Panic window concurrency {}", stableWindowAverageConcurrency, panicWindowAverageConcurrency);
                }
            } else {
                if (ChronoUnit.SECONDS.between(stats.getLastScaling(), now) >= parseToSeconds(serverless.getSpec().getStableWindow())) {
                    mode = AutoscalerWorkingMode.STABLE;
                    logger.info("Switching to {} mode", mode);
                    logger.info("Stable window concurrency {} Panic window concurrency {}", stableWindowAverageConcurrency, panicWindowAverageConcurrency);
                }
            }

            if (mode == AutoscalerWorkingMode.PANIC) {
                int desiredPods = (int) Math.ceil(panicWindowAverageConcurrency / concurrencyTarget);
                int currentPods = stats.getDesiredReplicas().get();
                if (desiredPods > currentPods) {
                    logger.info("Scaling to {} pods, concurrency {} ", desiredPods, panicWindowAverageConcurrency);
                    stats.setLastScaling(now);
                    stats.getDesiredReplicas().set(desiredPods);
                    scale(desiredPods);
                }
            } else {
                if (stableWindowAverageConcurrency == 0) {
                    double scaleToZeroWindowAverage = tail(stats.getConcurrencyValues(), parseToSeconds(serverless.getSpec().getScaleToZeroGracePerdiod()))
                            .stream().mapToDouble(v->v).average().orElse(0);
                    if (scaleToZeroWindowAverage == 0) {
                        int oldValue = stats.getDesiredReplicas().getAndSet(0);
                        if (oldValue != 0) {
                            logger.info("Grace period expired, scaling down");
                            stats.getDesiredReplicas().set(0);
                            scale(0);
                        }
                    }
                } else {
                    int desiredPods = (int) Math.ceil(stableWindowAverageConcurrency / concurrencyTarget);
                    int currentPods = stats.getDesiredReplicas().get();
                    if (desiredPods != currentPods) {
                        logger.info("Scaling to {} pods, concurrency {} ", desiredPods, stableWindowAverageConcurrency);
                        stats.getDesiredReplicas().set(desiredPods);
                        scale(desiredPods);
                    }
                }
            }

        }

        private void updateStats() {
            double average = stats.getLastConcurrencyValues().stream().mapToInt(v -> v).average().orElse(0);
            stats.getLastConcurrencyValues().clear();
            stats.getConcurrencyValues().add(average);
            if (stats.getConcurrencyValues().size()>parseToSeconds(serverless.getSpec().getStableWindow())) {
                stats.getConcurrencyValues().remove();
            }
        }

        private void generateStats() {
            stats.getLastConcurrencyValues().add(stats.getConcurrentRequests().get());
        }

        public void scale(int requiredPods) {
            vertx.runOnContext(a -> {
                kubernetesClient.apps().deployments()
                    .inNamespace(serverless.getMetadata().getNamespace())
                    .withName(serverless.getStatus().getDeploymentName())
                    .scale(requiredPods);
            });
        }

        public void stop() {
            jobsId.forEach(vertx::cancelTimer);
        }

    }

    private enum AutoscalerWorkingMode {
        STABLE,
        PANIC;
    }

}
