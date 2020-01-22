package io.famargon.k8s.resource;

import io.fabric8.kubernetes.client.CustomResourceList;

/**
 * ServerlessList
 */
public class ServerlessList extends CustomResourceList<Serverless> {

    private static final long serialVersionUID = 5992983952414617890L;

    public static final String KIND = "ServerlessList";

}