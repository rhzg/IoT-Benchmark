package de.fraunhofer.iosb.ilt.frostBenchmark;

import de.fraunhofer.iosb.ilt.frostBenchmark.BenchData;
import de.fraunhofer.iosb.ilt.frostBenchmark.BenchProperties;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Datastream;
import de.fraunhofer.iosb.ilt.sta.model.Observation;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;

public class SensorScheduler {

	public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(SensorScheduler.class);

	private ScheduledExecutorService scheduler;

	private List<DataSource> dsList;
	private long startTime = 0;
	private long stopTime = 0;
	private int lapTime = 1000;

	private int workerCount;
	private int sensorCount;
	private int period;

	/**
	 * TODO pass in settings object instead of using static BenchProperties
	 */
	public SensorScheduler() {
		BenchData.initialize();
		workerCount = BenchProperties.workers;
		sensorCount = BenchProperties.sensors;
		period = BenchProperties.period;

		scheduler = Executors.newScheduledThreadPool(workerCount);
	}

	public void initWorkLoad() throws ServiceFailureException, URISyntaxException {
		LOGGER.trace("Benchmark initializing, starting workers");
		dsList = new ArrayList<>();
		for (int i = 0; i < sensorCount; i++) {
			String name = "Benchmark." + i;
			DataSource sensor = new DataSource(BenchData.service).intialize(name);
			dsList.add(sensor);
		}
		LOGGER.trace("Benchmark initialized");
	}

	public void startWorkLoad() throws ServiceFailureException, URISyntaxException {
		startTime = System.currentTimeMillis();
		double delayPerSensor = ((double) period) / sensorCount;
		double currentDelay = 0;
		for (DataSource sensor : dsList) {
			ScheduledFuture<?> handle = scheduler.scheduleAtFixedRate(sensor, (long) currentDelay, period, TimeUnit.MILLISECONDS);
			sensor.setSchedulerHandle(handle);
			currentDelay += delayPerSensor;
		}
	}

	public void stopWorkLoad() {
		LOGGER.trace("Benchmark finishing");

		for (DataSource sensor : dsList) {
			sensor.cancel();
		}

		stopTime = System.currentTimeMillis();
		int entries = 0;
		for (DataSource sensor : dsList) {
			entries += sensor.reset();
		}

		Datastream ds = BenchData.getDatastream("SensorCluster");
		double rate = 1000.0 * entries / (stopTime - startTime);
		try {
			BenchData.service.create(new Observation(rate, ds));
		} catch (ServiceFailureException exc) {
			LOGGER.error("Failed.", exc);
		}

		LOGGER.info(1000 * entries / (stopTime - startTime) + " entries created per sec");
		LOGGER.info("Benchmark finished");

	}

	public void terminate() {
		scheduler.shutdown();
		boolean allOk = true;
		try {
			allOk = scheduler.awaitTermination(2, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			LOGGER.trace("Woken up, wait done.", ex);
		}
		if (!allOk) {
			scheduler.shutdownNow();
		}
	}
}
