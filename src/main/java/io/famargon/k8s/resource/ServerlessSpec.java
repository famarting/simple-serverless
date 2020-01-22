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
    @JsonProperty("container-concurrency-target")
    private Integer concurrencyTarget = 100;
    @JsonProperty("panic-window")
    private String panicWindow = "6s";
    @JsonProperty("stable-window")
    private String stableWindow = "60s";
    @JsonProperty("scale-to-zero-grace-period")
    private String scaleToZeroGracePerdiod = "30s";
    @JsonProperty("tick-interval")
    private String tickInterval = "2s";

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

    public Integer getConcurrencyTarget() {
        return concurrencyTarget;
    }

    public void setConcurrencyTarget(Integer concurrencyTarget) {
        this.concurrencyTarget = concurrencyTarget;
    }

    public String getPanicWindow() {
        return panicWindow;
    }

    public void setPanicWindow(String panicWindow) {
        this.panicWindow = panicWindow;
    }

    public String getStableWindow() {
        return stableWindow;
    }

    public void setStableWindow(String stableWindow) {
        this.stableWindow = stableWindow;
    }

    public String getTickInterval() {
        return tickInterval;
    }

    public void setTickInterval(String tickInterval) {
        this.tickInterval = tickInterval;
    }

}