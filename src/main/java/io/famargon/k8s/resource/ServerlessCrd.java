 package io.famargon.k8s.resource;

import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
import io.famargon.k8s.resource.Serverless;
import io.famargon.k8s.resource.ServerlessList;

/**
 * ServerlessCrd
 */
public class ServerlessCrd {

    public static final String VERSION = "v1alpha1";
    public static final String GROUP = "serverless.keetle.io";
    public static final String API_VERSION = GROUP + "/" + VERSION;

    private static final CustomResourceDefinition SERVERLESS_CRD;

    static {
        SERVERLESS_CRD = createCustomResource(GROUP, VERSION, Serverless.KIND);
    }

    public static void registerCustomCrds() {
        KubernetesDeserializer.registerCustomKind(API_VERSION, Serverless.KIND, Serverless.class);
        KubernetesDeserializer.registerCustomKind(API_VERSION, ServerlessList.KIND, ServerlessList.class);
    }

    public static CustomResourceDefinition serverless() {
        return SERVERLESS_CRD;
    }

    private static CustomResourceDefinition createCustomResource(final String group, final String version, final String kind) {
        String singular = kind.toLowerCase();
        String listKind = kind + "List";
        String plural = singular + "s";
        if (singular.endsWith("s")) {
            plural = singular + "es";
        } else if (singular.endsWith("y")) {
            plural = singular.substring(0, singular.length() - 1) + "ies";
        }
        return new CustomResourceDefinitionBuilder()
                        .editOrNewMetadata()
                        .withName(plural + "." + group)
                        .addToLabels("infra", "kettle")
                        .endMetadata()
                        .editOrNewSpec()
                        .withGroup(group)
                        .withVersion(version)
                        .withScope("Namespaced")
                        .editOrNewNames()
                        .withKind(kind)
                        .withListKind(listKind)
                        .withPlural(plural)
                        .withSingular(singular)
                        .endNames()
                        .endSpec()
                        .build();
    }

}