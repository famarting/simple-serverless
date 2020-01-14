package io.famargon.k8s.resource;

import io.fabric8.kubernetes.client.CustomResourceList;

/**
 * ServerlessList
 */
public class ServerlessList extends CustomResourceList<Serverless> {

    public static final String KIND = "ServerlessList";

}