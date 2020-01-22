package io.famargon.k8s;

import io.famargon.k8s.resource.Serverless;

public interface ServerlessService {

    void create(Serverless s);
    void delete(Serverless s);

}
