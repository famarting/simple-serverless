package io.famargon.k8s.resource;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;

/**
 * DoneableServerless
 */
public class DoneableServerless extends CustomResourceDoneable<Serverless> {

    public DoneableServerless(Serverless resource, Function<Serverless, Serverless> function) {
        super(resource, function);
    }

}