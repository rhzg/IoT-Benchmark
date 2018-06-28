FROM openjdk:8-jre
MAINTAINER Reinhard Herzog <reinhard.herzog@iosb.fraunhofer.de>

ENV BASE_URL http://10.1.9.185:8080/FROST-Server/v1.0/
ENV BROKER 10.1.9.185
ENV SESSION 0815
ENV WORKERS 10
ENV POSTDELAY 1
      

ADD target/BenchmarkController-0.0.1-SNAPSHOT-jar-with-dependencies.jar ./frostbenchmark.jar
CMD ["/usr/bin/java", "-jar", "./frostbenchmark.jar"]
