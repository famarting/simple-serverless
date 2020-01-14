package io.famargon.k8s.resource;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * ServerlessStatus
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServerlessStatus {

    private String suid;
    private Boolean isReady;
    private String internalService;

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

    
}