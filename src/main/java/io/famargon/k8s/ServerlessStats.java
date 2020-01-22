package io.famargon.k8s;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import io.famargon.k8s.resource.Serverless;

public class ServerlessStats {

    private Serverless serverless;
    private AtomicInteger desiredReplicas = new AtomicInteger(0);
    private AtomicInteger concurrentRequests = new AtomicInteger(0);
    private List<Integer> lastConcurrencyValues = new CopyOnWriteArrayList<>();
    private LinkedList<Double> concurrencyValues = new LinkedList<>();
    private Instant lastScaling;

    public ServerlessStats(Serverless serverless) {
        this.serverless = serverless;
    }

    public Serverless getServerless() {
        return serverless;
    }

    public AtomicInteger getConcurrentRequests() {
        return concurrentRequests;
    }

    public AtomicInteger getDesiredReplicas() {
        return desiredReplicas;
    }

    public List<Integer> getLastConcurrencyValues() {
        return lastConcurrencyValues;
    }

    public LinkedList<Double> getConcurrencyValues() {
        return concurrencyValues;
    }

    public Instant getLastScaling() {
        return lastScaling;
    }

    public void setLastScaling(Instant lastScaling) {
        this.lastScaling = lastScaling;
    }

}
