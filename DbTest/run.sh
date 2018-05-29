#!/bin/sh

export repeats=10
export threads=15
export loops=1000
export db_driver=org.postgresql.Driver
export db_url=jdbc:postgresql://localhost:5432/sensorthingsTest
export db_username=sensorthings
export db_password=ChangeMe
export dsStart=1
export dsEnd=10
export featureStart=1
export featureEnd=10
export feature=1
export maxTotal=90
export maxIdle=10
export minIdle=5

java -jar target/DbTest-*-SNAPSHOT-jar-with-dependencies.jar
