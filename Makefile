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

.PHONY: $(DOCKER_TARGETS) build container docker 
