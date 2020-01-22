package io.famargon.k8s.cache;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.famargon.k8s.SimpleServerlessOperator;
import io.vertx.core.Vertx;

public class DeploymentsCache extends GenericResourceCache<Deployment> {

    public DeploymentsCache(KubernetesClient kubernetesClient, Vertx vertx) {
        super(kubernetesClient, vertx);
    }

    @Override
    public void startWatch() {
        kubernetesClient.apps().deployments().inNamespace(SimpleServerlessOperator.NAMESPACE).withLabel("app", "serverless").watch(this);
    }

    @Override
    protected void handleCreateOrUpdate(Action action, Deployment resource) {
        super.handleCreateOrUpdate(action, resource);
        super.trigger(resource.getMetadata().getName(), resource);
    }

    public int getAvailablePods(String deploymentName) {
        Deployment d = cache.get(deploymentName);
        Integer r = d.getStatus().getAvailableReplicas();
        if (r==null) {
            return 0;
        }
        return r;
    }

}
