package io.famargon.k8s.resource;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.fabric8.kubernetes.api.model.PodSpec;

/**
 * ServerlessSpec
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServerlessSpec {

    private String hostname;
    private int port;
    private PodSpec podSpec;

    /**
     * @return the podSpec
     */
    public PodSpec getPodSpec() {
        return podSpec;
    }

    /**
     * @param podSpec the podSpec to set
     */
    public void setPodSpec(PodSpec podSpec) {
        this.podSpec = podSpec;
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * @param port the port to set
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * @return the hostname
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * @param hostname the hostname to set
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

}