package io.famargon.k8s.cache;

import java.util.function.BiConsumer;

public interface SubscriptionService <T> {

    public String subscribe(String name, BiConsumer<String, T> callback);

    public void unsubscribe(String name, String subscriptionId);

}