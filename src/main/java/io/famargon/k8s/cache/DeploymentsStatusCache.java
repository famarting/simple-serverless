package io.famargon.k8s.cache;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.famargon.k8s.SimpleServerlessOperator;

public class DeploymentsStatusCache extends GenericResourceCache<Deployment> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private Map<String, Map<String, BiConsumer<String, Deployment>>> subscriptions = new ConcurrentHashMap<>();

    public DeploymentsStatusCache(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    public void startWatch() {
        kubernetesClient.apps().deployments().inNamespace(SimpleServerlessOperator.NAMESPACE).withLabel("app", "serverless").watch(this);
    }

    @Override
    protected void handleCreateOrUpdate(Action action, Deployment resource) {
        super.handleCreateOrUpdate(action, resource);
        if (subscriptions.containsKey(resource.getMetadata().getName())) {
            subscriptions.get(resource.getMetadata().getName()).entrySet().forEach(e -> e.getValue().accept(e.getKey(), resource));
        }
    }

    public int getAvailablePods(String deploymentName) {
        Deployment d = cache.get(deploymentName);
        Integer r = d.getStatus().getAvailableReplicas();
        if (r==null) {
            return 0;
        }
        return r;
    }

    public String subscribe(String deploymentName, BiConsumer<String, Deployment> callback) {
        String uuid = UUID.randomUUID().toString();
        subscriptions.computeIfAbsent(deploymentName, k -> new ConcurrentHashMap<>()).put(uuid, callback);
        return uuid;
    }

    public void unsubscribe(String deploymentName, String uuid) {
        if (subscriptions.containsKey(deploymentName)) {
            subscriptions.get(deploymentName).remove(uuid);
        }
    }

}
