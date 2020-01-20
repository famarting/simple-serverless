package io.famargon.k8s.resource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import io.fabric8.kubernetes.api.model.PodSpec;

/**
 * ServerlessSpec
 */
@JsonDeserialize
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServerlessSpec {

    private String hostname;
    private int port;
    private PodSpec podSpec;
    @JsonProperty("scale-to-zero-grace-period")
    private String scaleToZeroGracePerdiod = "30s";
    @JsonProperty("in-flight-requests")
    private Integer inFlightRequestsPerPod = 100;

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

    public String getScaleToZeroGracePerdiod() {
        return scaleToZeroGracePerdiod;
    }

    public void setScaleToZeroGracePerdiod(String scaleToZeroGracePerdiod) {
        this.scaleToZeroGracePerdiod = scaleToZeroGracePerdiod;
    }

    public Integer getInFlightRequestsPerPod() {
        return inFlightRequestsPerPod;
    }

    public void setInFlightRequestsPerPod(Integer inFlightRequestsPerPod) {
        this.inFlightRequestsPerPod = inFlightRequestsPerPod;
    }

}