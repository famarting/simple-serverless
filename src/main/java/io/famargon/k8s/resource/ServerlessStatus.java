package io.famargon.k8s.resource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * ServerlessStatus
 */
@JsonDeserialize
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServerlessStatus {

    private String suid;
    private Boolean isReady;
    private String deploymentName;
    private String internalService;

    public ServerlessStatus() {
        super();
    }

    public ServerlessStatus(Boolean isReady) {
        this.isReady = isReady;
    }

    /**
     * @return the isReady
     */
    public Boolean getIsReady() {
        return isReady;
    }

    /**
     * @param isReady the isReady to set
     */
    public void setIsReady(Boolean isReady) {
        this.isReady = isReady;
    }

    /**
     * @return the internalService
     */
    public String getInternalService() {
        return internalService;
    }

    /**
     * @param internalService the internalService to set
     */
    public void setInternalService(String internalService) {
        this.internalService = internalService;
    }

    public String getSuid() {
        return suid;
    }

    public void setSuid(String suid) {
        this.suid = suid;
    }

    public String getDeploymentName() {
        return deploymentName;
    }

    public void setDeploymentName(String deploymentName) {
        this.deploymentName = deploymentName;
    }

    @Override
    public String toString() {
        return "ServerlessStatus [suid=" + suid + ", isReady=" + isReady + ", internalService=" + internalService + "]";
    }

}