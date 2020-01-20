package io.famargon.k8s.resource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.fabric8.kubernetes.client.CustomResource;

/**
 * Serverless
 */
@JsonDeserialize
@JsonIgnoreProperties(ignoreUnknown = true)
public class Serverless extends CustomResource{

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public static final String KIND = "Serverless";

    private ServerlessSpec spec = new ServerlessSpec();
    private ServerlessStatus status = new ServerlessStatus(false);

    public void setSpec(ServerlessSpec spec) {
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