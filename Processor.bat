set BASE_URL=http://localhost:8080/FROST-Server/v1.0/
# Broker for session updates 
set BROKER=10.1.9.185
# Benchmark Session Identifier
set SESSION=0815
# Percentage of Datastreams covered
set COVERAGE=50
java -cp .\target\frostBenchmark-0.0.1-SNAPSHOT-jar-with-dependencies.jar frostBenchmark.Processor
