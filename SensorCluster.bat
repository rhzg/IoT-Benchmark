set BASE_URL=http://localhost:8080/FROST-Server/v1.0/
set BROKER=10.1.9.185
# Benchmark Session Identifier
set SESSION=0815
# number of parallel Sensors data providers
set WORKERS=100
# msec delay between observations
set POSTDELAY=1
java -cp .\target\frostBenchmark-0.0.1-SNAPSHOT-jar-with-dependencies.jar frostBenchmark.SensorCluster
