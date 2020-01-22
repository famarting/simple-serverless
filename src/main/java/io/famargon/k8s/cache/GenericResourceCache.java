package io.famargon.k8s.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.vertx.core.Vertx;

public abstract class GenericResourceCache <T extends HasMetadata> extends GenericSubscriptionService<T> implements Watcher<T>{

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    protected Map<String, T> cache = new ConcurrentHashMap<>();

    protected KubernetesClient kubernetesClient;

    public GenericResourceCache(KubernetesClient kubernetesClient, Vertx vertx) {
        super(vertx);
        this.kubernetesClient = kubernetesClient;
    }

    public abstract void startWatch();

    @Override
    public void eventReceived(Action action, T resource) {
        switch (action) {
        case ERROR:
            handleError(resource);
            break;
        case ADDED:
        case MODIFIED:
            handleCreateOrUpdate(action, resource);
            break;
        case DELETED:
            handleDeletion(resource);
            break;
        default:
            logger.info("{} not implemented", action);
            break;
        }
    }

    private void handleDeletion(T resource) {
        cache.remove(resource.getMetadata().getName());
    }

    protected void handleCreateOrUpdate(Action action, T resource) {
        cache.put(resource.getMetadata().getName(), resource);
    }

    private void handleError(T resource) {
        logger.error("Error received", resource);
    }

    @Override
    public void onClose(KubernetesClientException cause) {
        logger.error("Error in serverless controller", cause);
    }

}
