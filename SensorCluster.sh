# The name to use for finding our settings
export NAME=Sensor_1

# URL to be used for creating, subscribing and reading data
export BASE_URL=http://localhost:8080/FROST-Server/v1.0/

# mqtt broker address
# set BROKER=192.168.99.100
export BROKER=localhost

# Benchmark Session Identifier within Benchmark thing to be used
export SESSION=0815

# number of parallel Threads
export WORKERS=10

# The number of sensors to simulate
export SENSORS=20

# msec delay between observations
export PERIOD=100

java -jar ./SensorCluster/target/SensorCluster-*-SNAPSHOT-jar-with-dependencies.jar
