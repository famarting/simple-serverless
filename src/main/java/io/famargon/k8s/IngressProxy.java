package io.famargon.k8s;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.famargon.k8s.resource.Serverless;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpServerRequest;

/**
 * IngressProxy
 */
public class IngressProxy {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private HttpClient client;
    private String destination;
    private int port;

    public IngressProxy(Vertx vertx, Serverless serverless) {
        this.client = vertx.createHttpClient(new HttpClientOptions());
        this.destination = serverless.getStatus().getInternalService();
        this.port = serverless.getSpec().getPort();
    }

    @SuppressWarnings("deprecation")
    public void doProxy(HttpServerRequest req, Handler<Void> beforeCallback, Handler<Void> afterCallback) {
        beforeCallback.handle(null);
        HttpClientRequest clientReq = client.request(req.method(), port, destination, req.uri(), clientRes -> {
            req.response().setChunked(true);
            req.response().setStatusCode(clientRes.statusCode());
            req.response().headers().setAll(clientRes.headers());
            clientRes.handler(data -> {
                req.response().write(data);
            });
            clientRes.endHandler((v) -> req.response().end());
            clientRes.exceptionHandler(e -> {
               logger.error("Error caught in response of request to serverless", e);
            });
            req.response().exceptionHandler(e -> {
               logger.error("Error caught in response to client", e);
            });
        });
        clientReq.setChunked(true);
        clientReq.headers().setAll(req.headers());

        if (req.isEnded()) {
            clientReq.end();
            afterCallback.handle(null);
        } else {
            req.handler(data -> {
                clientReq.write(data);
            });
            req.endHandler((v) -> {
                clientReq.end();
                afterCallback.handle(null);
            });
            clientReq.exceptionHandler(e -> {
                logger.error("Error caught in request to serverless", e);
                req.response().setStatusCode(500).putHeader("x-error", e.getMessage()).end();
            });
        }

        req.exceptionHandler(e -> {
           logger.error("Error caught in request from client", e);
        });

    }

}