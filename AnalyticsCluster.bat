REM The name to use for finding our settings
set NAME=Analytics_1

REM URL to be used for creating, subscribing and reading data
set BASE_URL=http://localhost:8080/FROST-Server/v1.0/

REM mqtt broker address
REM set BROKER=192.168.99.100
set BROKER=localhost

REM Benchmark Session Identifier within Benchmark thing to be used
set SESSION=0815

REM number of parallel Sensors data providers
set WORKERS=10

REM msec delay between observations
set PERIOD=50

set SENSORS=10

java -jar .\AnalyticsCluster\target\AnalyticsCluster-0.0.1-SNAPSHOT-jar-with-dependencies.jar
