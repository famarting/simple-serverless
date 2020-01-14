FROM openjdk:11-jre-slim
COPY target/simple-serverless-1.0-SNAPSHOT.jar /deployments/app.jar
ENTRYPOINT [ "java", "-jar", "/deployments/app.jar" ]