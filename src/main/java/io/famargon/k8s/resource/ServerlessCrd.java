 package io.famargon.k8s.resource;

import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;

/**
 * ServerlessCrd
 */
public class ServerlessCrd {

    public static final String VERSION = "v1alpha1";
    public static final String GROUP = "serverless.keetle.io";
    public static final String API_VERSION = GROUP + "/" + VERSION;
    public static final String CRD_NAME = "serverlesses." + GROUP;

    public static final CustomResourceDefinitionContext CTX = new CustomResourceDefinitionContext.Builder()
            .withName(CRD_NAME)
            .withGroup(GROUP)
            .withScope("Namespaced")
            .withVersion(VERSION)
            .withPlural("serverlesses")
            .build();

    public static void registerCustomCrds() {
        KubernetesDeserializer.registerCustomKind(API_VERSION, Serverless.KIND, Serverless.class);
//        KubernetesDeserializer.registerCustomKind(API_VERSION, ServerlessList.KIND, ServerlessList.class);
    }

}