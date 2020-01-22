package io.famargon.k8s;

import io.famargon.k8s.resource.Serverless;
import io.vertx.core.http.HttpServerRequest;

public class ServerlessRequest {

    private HttpServerRequest request;
    private long requestBegin;
    private Serverless serverless;

    public ServerlessRequest(HttpServerRequest request, Serverless serverless) {
        this.request = request;
        this.serverless = serverless;
        this.requestBegin = System.nanoTime();
    }

    public long getRequestBegin() {
        return requestBegin;
    }

    public HttpServerRequest request() {
        return this.request;
    }

    public Serverless serverless() {
        return this.serverless;
    }

}
