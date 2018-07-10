# URL to be used for creating, subscribing and reading data
export BASE_URL=http://localhost:8080/FROST-Server/v1.0/

# Benchmark Session Identifier within Benchmark thing to be used
export SESSION=0815

java -jar ./BenchmarkController/target/BenchmarkController-*-SNAPSHOT-jar-with-dependencies.jar
