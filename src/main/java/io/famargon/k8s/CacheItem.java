package io.famargon.k8s;

import io.famargon.k8s.resource.Serverless;

/**
 * CacheItem
 */
public class CacheItem {

    private Serverless serverless;
    private int pods;

    public CacheItem(){
        //
    }

    public CacheItem(Serverless s) {
        this.serverless = s;
        this.pods = 0;
    }

    /**
     * @return the serverless
     */
    public Serverless getServerless() {
        return serverless;
    }

    /**
     * @param serverless the serverless to set
     */
    public void setServerless(Serverless serverless) {
        this.serverless = serverless;
    }

    /**
     * @return the pods
     */
    public int getPods() {
        return pods;
    }

    /**
     * @param pods the pods to set
     */
    public void setPods(int pods) {
        this.pods = pods;
    }

    
}