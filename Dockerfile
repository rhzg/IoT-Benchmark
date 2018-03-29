FROM openjdk:8-jre
MAINTAINER Reinhard Herzog <reinhard.herzog@siosb.fraunhofer.de>


ADD target/frostBenchmark-0.0.1-SNAPSHOT-jar-with-dependencies.jar ./frostbenchmark.jar
CMD ["/usr/bin/java", "-cp", "./frostbenchmark.jar", "frostBenchmark.SensorCluster"]
