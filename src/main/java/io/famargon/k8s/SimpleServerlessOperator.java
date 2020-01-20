package io.famargon.k8s;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.famargon.k8s.cache.DeploymentsStatusCache;
import io.famargon.k8s.resource.Serverless;
import io.vertx.core.Vertx;

public class SimpleServerlessOperator {

    public static final String NAMESPACE = Optional.ofNullable(System.getenv("KUBERNETES_NAMESPACE")).orElse("serverless-infra");

    public static void main(String[] args) {
        KubernetesClient kubernetesClient = new DefaultKubernetesClient().inNamespace(NAMESPACE);
        Map<String, Serverless> cache = new ConcurrentHashMap<>();
        Vertx vertx = Vertx.vertx();
        //
        new ServerlessController(kubernetesClient, cache).start();
        DeploymentsStatusCache deploymentsCache = new DeploymentsStatusCache(kubernetesClient);
        deploymentsCache.startWatch();
        new ProxyManager(kubernetesClient, cache, vertx, deploymentsCache).startServer();
    }
}
