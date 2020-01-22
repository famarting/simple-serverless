PROJECT_NAME = simple-serverless

DOCKER_TARGETS = docker_build docker_tag docker_push

container: build docker

build:
	mvn package

DOCKER_REGISTRY = quay.io
ORG_NAME = famargon
DOCKER          = docker
TAG             ?= latest
PROJECT_TAG_NAME = $(DOCKER_REGISTRY)/$(ORG_NAME)/$(PROJECT_NAME):$(TAG)

docker: $(DOCKER_TARGETS)

docker_build:
	if [ -f Dockerfile ]; then $(DOCKER) build -t $(ORG_NAME)-$(PROJECT_NAME) . ; fi
	if [ -f Dockerfile ]; then docker images | grep $(ORG_NAME)-$(PROJECT_NAME) ; fi

docker_tag:
	if [ -f Dockerfile ]; then $(DOCKER) tag $(ORG_NAME)-$(PROJECT_NAME) $(PROJECT_TAG_NAME) ; fi

docker_push:
	if [ -f Dockerfile ]; then $(DOCKER) push $(PROJECT_TAG_NAME); fi

debug:
	java -Xdebug -Xrunjdwp:transport=dt_socket,address=5000,server=y,suspend=y -jar target/simple-serverless-1.0-SNAPSHOT.jar

deploy_example:
	oc apply -f example/example-serverless.yaml

test_example:
	time curl -i --header "x-host:reverse-words" http://serverless-controller-route-serverless-infra.127.0.0.1.nip.io/api/reverse?text=palc
	# time curl -i --header "x-host:reverse-words" http://localhost:8080/api/reverse?text=palc

siege_example:
	siege --header "x-host:reverse-words" http://serverless-controller-route-serverless-infra.127.0.0.1.nip.io/api/reverse?text=palc
	# siege --header "x-host:reverse-words" http://localhost:8080/api/reverse?text=palc

clean_example:
	oc delete svl example-serverless
	oc delete svc example-serverless-loadbalancer
	oc delete deployment example-serverless-deployment



.PHONY: $(DOCKER_TARGETS) build container docker 
