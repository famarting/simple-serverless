package io.famargon.k8s;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.famargon.k8s.resource.DoneableServerless;
import io.famargon.k8s.resource.Serverless;
import io.famargon.k8s.resource.ServerlessCrd;
import io.famargon.k8s.resource.ServerlessList;
import io.famargon.k8s.resource.ServerlessStatus;

/**
 * ServerlessController
 */
public class ServerlessController implements Watcher<Serverless> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    private String namespace = System.getenv("KUBERNETES_NAMESPACE");
    private KubernetesClient kubernetesClient;
    private Map<String, CacheItem> cache;

    public ServerlessController(KubernetesClient kubernetesClient, Map<String, CacheItem> cache) {
        this.kubernetesClient = kubernetesClient;
        this.cache = cache;
    }

    public void start() {

        getServerlessCrdClient().watch(this);

    }

    @Override
    public void eventReceived(Action action, Serverless resource) {
        switch (action) {
        case ERROR:
            handleError(resource);
            break;
        case ADDED:
        case MODIFIED:
            handleCreateOrUpdate(action, resource);
            break;
        case DELETED:
            handleDeletion(resource);
            break;
        default:
            logger.info("{} not implemented", action);
            break;
        }
    }

    private void handleDeletion(Serverless resource) {
        logger.info("Handling delete {}", resource.getMetadata().getName());
        String suid = resource.getStatus().getSuid();
        kubernetesClient.apps().deployments().inNamespace(namespace).withLabel("suid", suid).delete();
        kubernetesClient.services().inNamespace(namespace).withLabel("suid", suid).delete();
    }


    private void handleCreateOrUpdate(Action action, Serverless resource) {
        logger.info("Handling resource {} {}", resource.getMetadata().getName(), action);

        String suid;
        if(resource.getStatus() == null) {
            suid = UUID.randomUUID().toString();
        }else{
            suid = resource.getStatus().getSuid();
        }

        if(kubernetesClient.apps().deployments().inNamespace(namespace).withName(resource.getMetadata().getName() + "-deployment").get() == null){
            Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withNamespace(namespace)
                .withName(resource.getMetadata().getName() + "-deployment")
                .addToLabels("suid", suid)
                .endMetadata()
                .withNewSpec()
                .withReplicas(0)
                .withNewSelector()
                .addToMatchLabels("suid", suid)
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels("suid", suid)
                .addToOwnerReferences(new OwnerReferenceBuilder()
                .withApiVersion(resource.getApiVersion())
                .withKind(resource.getKind())
                .withName(resource.getMetadata().getName())
                .withUid(resource.getMetadata().getUid()).build())
                .endMetadata()
                .withSpec(resource.getSpec().getPodSpec())
                .endTemplate()
                .endSpec()
                .build();
            
            Deployment depRes = kubernetesClient.apps().deployments().inNamespace(namespace).create(deployment);
            logger.info("Deployment {} created", depRes.getMetadata().getName());
        }else{
            logger.info("Deployment {} already exists", resource.getMetadata().getName() + "-deployment");
        }

        if(kubernetesClient.services().inNamespace(namespace).withName(resource.getMetadata().getName()+"-loadbalancer").get() == null){
            Service loadBalancer =  new ServiceBuilder()
            .withNewMetadata()
            .withName(resource.getMetadata().getName()+"-loadbalancer")
            .addToLabels("suid", suid)
            .endMetadata()
            .withNewSpec()
            .withType("LoadBalancer")
            .addToSelector("suid", suid)
            .addNewPort()
            .withName("http")
            .withPort(resource.getSpec().getPort())
            .withProtocol("TCP")
            .endPort()
            .endSpec()
            .build();
            Service createdService = kubernetesClient.services().inNamespace(namespace).create(loadBalancer);
            logger.info("Loadbalancer {} created", createdService.getMetadata().getName());
        }else{
            logger.info("LoadBalancer {} already exists", resource.getMetadata().getName()+"-loadbalancer");
        }

        if(resource.getStatus() == null && action == Action.ADDED) {

            ServerlessStatus status = new ServerlessStatus();
            status.setSuid(suid);
            status.setIsReady(true);
            status.setInternalService(resource.getMetadata().getName()+"-loadbalancer");
            resource.setStatus(status);

            getServerlessCrdClient()
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getMetadata().getName())
                .replace(resource);

            cache.put(resource.getSpec().getHostname(), new CacheItem(resource));
        }

        logger.info("Serverless with host {} successfully {}", resource.getSpec().getHostname(), action);
    }

    private void handleError(Serverless resource) {
        logger.error("Error received {}", resource);
    }

    @Override
    public void onClose(KubernetesClientException cause) {
        logger.error("Error in serverless controller", cause);
    }

    private MixedOperation<Serverless, ServerlessList, DoneableServerless, Resource<Serverless, DoneableServerless>> getServerlessCrdClient() {
        return (MixedOperation<Serverless, ServerlessList, DoneableServerless, Resource<Serverless, DoneableServerless>>) kubernetesClient
                .customResources(ServerlessCrd.serverless(), Serverless.class, ServerlessList.class,
                        DoneableServerless.class)
                .inNamespace(namespace);
    }
}