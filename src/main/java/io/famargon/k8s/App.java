package io.famargon.k8s;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.famargon.k8s.resource.ServerlessCrd;

/**
 * Hello world!
 *
 */
public class App {

    static {
        try {
            ServerlessCrd.registerCustomCrds();
        } catch (RuntimeException t) {
            t.printStackTrace();
            throw new ExceptionInInitializerError(t);
        }
    }

    public static void main(String[] args) {
        KubernetesClient kubernetesClient = new DefaultKubernetesClient();
        Map<String, CacheItem> cache = new ConcurrentHashMap<>();
        new ServerlessController(kubernetesClient, cache).start();
        new IngressProxy(kubernetesClient, cache).startServer();
    }
}
