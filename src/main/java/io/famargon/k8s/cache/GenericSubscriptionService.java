package io.famargon.k8s.cache;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import io.vertx.core.Vertx;

public abstract class GenericSubscriptionService <T> implements SubscriptionService<T>{

    private Vertx vertx;
    private Map<String, Map<String, BiConsumer<String, T>>> topicsSubscriptions = new ConcurrentHashMap<>();

    public GenericSubscriptionService(Vertx vertx) {
        this.vertx = vertx;
    }

    protected void trigger(String name, T resource) {
        if (topicsSubscriptions.containsKey(name)) {
            topicsSubscriptions.get(name).entrySet().forEach(e -> vertx.runOnContext(v -> e.getValue().accept(e.getKey(), resource)));
        }
    }

    @Override
    public String subscribe(String name, BiConsumer<String, T> callback) {
        String uuid = UUID.randomUUID().toString();
        topicsSubscriptions.computeIfAbsent(name, k -> new ConcurrentHashMap<>()).put(uuid, callback);
        return uuid;
    }

    @Override
    public void unsubscribe(String name, String subscriptionId) {
        if (topicsSubscriptions.containsKey(name)) {
            topicsSubscriptions.get(name).remove(subscriptionId);
        }
    }

}
