FROM openjdk:8-jre
MAINTAINER Reinhard Herzog <reinhard.herzog@iosb.fraunhofer.de>

ENV NAME Listener_1
ENV BASE_URL http://localhost:8080/FROST-Server/v1.0/
ENV BROKER localhost
ENV SESSION 0815
ENV WORKERS 10
ENV COVERAGE 50
ENV POSTDELAY 1

ADD target/SubscriberCluster-0.0.1-SNAPSHOT-jar-with-dependencies.jar ./frostbenchmark.jar
CMD ["/usr/bin/java", "-jar", "./frostbenchmark.jar"]
