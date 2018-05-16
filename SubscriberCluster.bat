REM URL to be used for creating, subscribing and reading data
set BASE_URL=http://localhost:8080/FROST-Server/v1.0/

REM mqtt broker address
REM set BROKER=192.168.99.100
set BROKER=localhost

REM Benchmark Session Identifier within Benchmark thing to be used
set SESSION=0815

REM Percentage of Datastreams covered by mqtt subsribers
set COVERAGE=50


java -cp .\SubscriberCluster\target\SubscriberCluster-0.0.1-SNAPSHOT-jar-with-dependencies.jar frostBenchmark.SubscriberCluster
