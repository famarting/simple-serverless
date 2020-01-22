package io.famargon.k8s;

import java.util.Optional;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.famargon.k8s.cache.DeploymentsCache;
import io.vertx.core.Vertx;

public class SimpleServerlessOperator {

    public static final String NAMESPACE = Optional.ofNullable(System.getenv("KUBERNETES_NAMESPACE")).orElse("serverless-infra");

    public static void main(String[] args) {
        KubernetesClient kubernetesClient = new DefaultKubernetesClient().inNamespace(NAMESPACE);
        Vertx vertx = Vertx.vertx();
        //
        DeploymentsCache deploymentsCache = new DeploymentsCache(kubernetesClient, vertx);
        deploymentsCache.startWatch();
        ProxyManager proxyManager = new ProxyManager(kubernetesClient, vertx, deploymentsCache);
        ServerlessController controller = new ServerlessController(kubernetesClient, vertx, proxyManager);
        controller.start();
        proxyManager.startServer();
    }
}
