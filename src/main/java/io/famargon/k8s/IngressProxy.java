package io.famargon.k8s;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.famargon.k8s.resource.Serverless;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;

/**
 * IngressProxy
 */
public class IngressProxy {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private Vertx vertx = Vertx.vertx();
    private HttpClient client = vertx.createHttpClient(new HttpClientOptions());
    private Map<String, CacheItem> cache;
    private KubernetesClient kubernetesClient;

    public IngressProxy(KubernetesClient kubernetesClient, Map<String, CacheItem> cache) {
        this.kubernetesClient = kubernetesClient;
        this.cache = cache;
    }

    public void startServer() {
        vertx.createHttpServer(new HttpServerOptions().setPort(8080)).requestHandler(this::proxyRequest)
                .listen(server -> {
                    if (server.succeeded()) {
                        logger.info("Server started");
                    } else {
                        logger.error("Error starting server", server.cause());
                    }
                });
    }

    private void proxyRequest(HttpServerRequest req) {
        logger.info("Request received");
        String targetHost = req.getHeader("x-host");
        if (targetHost == null || targetHost.isBlank()) {
            req.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end("missing x-host header");
            return;
        } else {
            var cacheItem = cache.get(targetHost);
            if (cacheItem == null) {
                req.response().setStatusCode(HttpResponseStatus.BAD_REQUEST.code()).end("Serverless target doesn't exists");
                return;
            }
            if (cacheItem.getPods() == 0) {
                scale(cacheItem);
            }
            doProxy(cacheItem.getServerless().getStatus().getInternalService(), cacheItem.getServerless().getSpec().getPort(), req);
        }
    }

    private void scale(CacheItem cacheItem) {
        logger.info("Scaling deployment");
        String namespace = cacheItem.getServerless().getMetadata().getNamespace();
        if (cacheItem.getPods() == 0) {
            kubernetesClient.apps().deployments()
                .inNamespace(namespace).withName(cacheItem.getServerless().getMetadata().getName()+ "-deployment")
                .scale(1);
        }
    }

    private void doProxy(String destination, int port, HttpServerRequest req) {
        logger.info("Proxying request: " + req.uri());
        HttpClientRequest c_req = client.request(req.method(), port, destination, req.uri(), c_res -> {
            logger.info("Proxying response: " + c_res.statusCode());
            req.response().setChunked(true);
            req.response().setStatusCode(c_res.statusCode());
            req.response().headers().setAll(c_res.headers());
            c_res.handler(data -> {
                logger.info("Proxying response body: " + data.toString("ISO-8859-1"));
                req.response().write(data);
            });
            c_res.endHandler((v) -> req.response().end());
        });
        c_req.setChunked(true);
        c_req.headers().setAll(req.headers());
        req.handler(data -> {
            logger.info("Proxying request body " + data.toString("ISO-8859-1"));
            c_req.write(data);
        });
        req.endHandler((v) -> c_req.end());
    }

}