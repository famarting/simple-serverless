package io.famargon.k8s;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import io.famargon.k8s.resource.Serverless;

public class ServerlessStats {

    private Serverless serverless;
    private AtomicInteger desiredReplicas = new AtomicInteger(0);
    private Instant lastRequest;
    private AtomicInteger inFlightRequests = new AtomicInteger(0);


    public ServerlessStats(Serverless serverless) {
        this.serverless = serverless;
        this.lastRequest = Instant.now();
    }

    public Serverless getServerless() {
        return serverless;
    }
    public void setServerless(Serverless serverless) {
        this.serverless = serverless;
    }
    public Instant getLastRequest() {
        return lastRequest;
    }
    public void setLastRequest(Instant lastRequest) {
        this.lastRequest = lastRequest;
    }
    public AtomicInteger getInFlightRequests() {
        return inFlightRequests;
    }
    public void setInFlightRequests(AtomicInteger inFlightRequests) {
        this.inFlightRequests = inFlightRequests;
    }
    public AtomicInteger getDesiredReplicas() {
        return desiredReplicas;
    }
    public void setDesiredReplicas(AtomicInteger desiredReplicas) {
        this.desiredReplicas = desiredReplicas;
    }

}
