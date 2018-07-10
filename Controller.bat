REM URL to be used for creating, subscribing and reading data
set BASE_URL=http://localhost:8080/FROST-Server/v1.0/

REM Benchmark Session Identifier within Benchmark thing to be used
set SESSION=0815

java -jar .\BenchmarkController\target\BenchmarkController-*-SNAPSHOT-jar-with-dependencies.jar
pause
