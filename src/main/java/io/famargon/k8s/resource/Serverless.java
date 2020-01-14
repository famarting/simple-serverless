package io.famargon.k8s.resource;

import io.fabric8.kubernetes.client.CustomResource;

/**
 * Serverless
 */
public class Serverless extends CustomResource{

    public static final String KIND = "Serverless";

    private ServerlessSpec spec;
    private ServerlessStatus status;

    public void setSpec(final ServerlessSpec spec) {
        this.spec = spec;
    }

    public ServerlessSpec getSpec() {
        return this.spec;
    }

    public ServerlessStatus getStatus() {
        return status;
    }

    public void setStatus(ServerlessStatus status) {
        this.status = status;
    }
    
}