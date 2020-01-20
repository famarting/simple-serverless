package io.famargon.k8s;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.OwnerReferenceBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;
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

    private String namespace = SimpleServerlessOperator.NAMESPACE;
    private KubernetesClient kubernetesClient;
    private Map<String, Serverless> cache;

    public ServerlessController(KubernetesClient kubernetesClient, Map<String, Serverless> cache) {
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
        logger.info("Handling delete {} {}", resource.getMetadata().getName(), resource.getStatus());
        String suid = resource.getStatus().getSuid();
        kubernetesClient.apps().deployments().inNamespace(namespace).withLabel("suid", suid).delete();
        kubernetesClient.services().inNamespace(namespace).withLabel("suid", suid).delete();
        cache.remove(resource.getSpec().getHostname());
    }


    private void handleCreateOrUpdate(Action action, Serverless resource) {
        String suid;
        if(resource.getStatus().getSuid() == null) {
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
                .addToLabels("app", "serverless")
                .endMetadata()
                .withNewSpec()
                .withReplicas(0)
                .withNewSelector()
                .addToMatchLabels("suid", suid)
                .addToMatchLabels("app", "serverless")
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels("suid", suid)
                .addToLabels("app", "serverless")
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
        }

        if(kubernetesClient.services().inNamespace(namespace).withName(resource.getMetadata().getName()+"-loadbalancer").get() == null){
            Service loadBalancer =  new ServiceBuilder()
            .withNewMetadata()
            .withName(resource.getMetadata().getName()+"-loadbalancer")
            .addToLabels("suid", suid)
            .endMetadata()
            .withNewSpec()
//            .withType("LoadBalancer")
            .withType("ClusterIP")
            .addToSelector("suid", suid)
            .addNewPort()
            .withName("http")
            .withPort(resource.getSpec().getPort())
            .withProtocol("TCP")
            .endPort()
            .endSpec()
            .build();
            Service createdService = kubernetesClient.services().inNamespace(namespace).create(loadBalancer);
            logger.info("Loadbalancer {} created, cluster ip is {}", createdService.getMetadata().getName(), createdService.getSpec().getClusterIP());
        }

        if(resource.getStatus().getSuid() == null) {

            ServerlessStatus status = new ServerlessStatus();
            status.setSuid(suid);
            status.setIsReady(true);
//            status.setInternalService(resource.getMetadata().getName() + "-loadbalancer");
            String clusterIp = kubernetesClient.services().inNamespace(namespace).withName(resource.getMetadata().getName() + "-loadbalancer")
                .get().getSpec().getClusterIP();
            status.setInternalService(clusterIp);
            status.setDeploymentName(resource.getMetadata().getName() + "-deployment");
            resource.setStatus(status);

            getServerlessCrdClient()
                .createOrReplace(resource);

            cache.put(resource.getSpec().getHostname(), resource);
            logger.info("Serverless with host {} successfully {}", resource.getSpec().getHostname(), action);
        }

    }

    private void handleError(Serverless resource) {
        logger.error("Error received {}", resource);
    }

    @Override
    public void onClose(KubernetesClientException cause) {
        logger.error("Error in serverless controller", cause);
    }

    private NonNamespaceOperation<Serverless, ServerlessList, DoneableServerless, Resource<Serverless, DoneableServerless>> crdClient;

    private NonNamespaceOperation<Serverless, ServerlessList, DoneableServerless, Resource<Serverless, DoneableServerless>> getServerlessCrdClient() {


        if (crdClient == null) {

            KubernetesDeserializer.registerCustomKind(ServerlessCrd.API_VERSION, Serverless.KIND, Serverless.class);

            Optional<CustomResourceDefinition> crd = kubernetesClient
                    .customResourceDefinitions().list().getItems().stream()
                    .filter(c -> ServerlessCrd.CRD_NAME.equals(c.getMetadata().getName()))
                    .findFirst();
            if (crd.isEmpty()) {
                logger.error("CRD not found");
                System.exit(1);
            }

            crdClient = kubernetesClient
                    .customResources(crd.get(), Serverless.class, ServerlessList.class, DoneableServerless.class)
                    .inNamespace(namespace);

        }

        return crdClient;
    }
}