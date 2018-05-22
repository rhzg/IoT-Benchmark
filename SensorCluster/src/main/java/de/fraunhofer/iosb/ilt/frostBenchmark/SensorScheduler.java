package de.fraunhofer.iosb.ilt.frostBenchmark;

import com.fasterxml.jackson.databind.JsonNode;
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

	private List<DataSource> dsList = new ArrayList<>();
	private long startTime = 0;
	private long stopTime = 0;

	BenchProperties settings;
	private boolean running = false;

	/**
	 * TODO pass in settings object instead of using static BenchProperties
	 */
	public SensorScheduler() {
		BenchData.initialize();
		settings = new BenchProperties().readFromEnvironment();
		scheduler = Executors.newScheduledThreadPool(settings.workers);
	}

	private int logUpdates(String name, int oldVal, int newVal) {
		if (oldVal != newVal) {
			LOGGER.info("Updating value of {} from {} to {}.", name, oldVal, newVal);
		}
		return newVal;
	}

	private void warnIfChanged(String name, int oldVal, int newVal) {
		if (oldVal != newVal) {
			LOGGER.warn("Changing parameter {} is not supported, using old value {} instead of new value {}.", name, oldVal, newVal);
		}
	}

	public synchronized void initWorkLoad(JsonNode updatedProperties) throws ServiceFailureException, URISyntaxException {
		if (running) {
			stopWorkLoad();
		}
		int oldWorkerCount = settings.workers;
		int oldPeriod = settings.period;
		settings.readFromJsonNode(updatedProperties);

		LOGGER.debug("Benchmark initializing, starting workers");
		logUpdates(BenchProperties.TAG_PERIOD, oldPeriod, settings.period);
		logUpdates(BenchProperties.TAG_WORKERS, oldWorkerCount, settings.workers);

		if (oldWorkerCount != settings.workers) {
			cleanupScheduler();
			scheduler = Executors.newScheduledThreadPool(settings.workers);
		}

		int haveCount = dsList.size();
		if (settings.sensors != haveCount) {
			if (settings.sensors > haveCount) {
				int toAdd = settings.sensors - haveCount;
				LOGGER.info("Setting up {} new sensors.", toAdd);
				for (int i = haveCount; i < settings.sensors; i++) {
					String name = "Benchmark." + i;
					DataSource sensor = new DataSource(BenchData.service).intialize(name);
					dsList.add(sensor);
				}
			}
			if (settings.sensors < haveCount) {
				int toRemove = haveCount - settings.sensors;
				LOGGER.info("Taking down {} sensors.", toRemove);
				while (dsList.size() > settings.sensors) {
					DataSource ds = dsList.remove(dsList.size() - 1);
					ds.cancel();
				}
			}
		}

		LOGGER.trace("Benchmark initialized");
	}

	public synchronized void startWorkLoad(JsonNode properties) throws ServiceFailureException, URISyntaxException {
		if (running) {
			stopWorkLoad();
		}
		running = true;
		startTime = System.currentTimeMillis();

		int oldPeriod = settings.period;
		settings.readFromJsonNode(properties);

		if (properties != null) {
			logUpdates(BenchProperties.TAG_PERIOD, oldPeriod, settings.period);
		}

		LOGGER.info("Starting workload: {} workers, {} sensors, {} delay.", settings.workers, settings.sensors, settings.period);
		double delayPerSensor = ((double) settings.period) / settings.sensors;
		double currentDelay = 0;
		for (DataSource sensor : dsList) {
			ScheduledFuture<?> handle = scheduler.scheduleAtFixedRate(sensor, (long) currentDelay, settings.period, TimeUnit.MILLISECONDS);
			sensor.setSchedulerHandle(handle);
			currentDelay += delayPerSensor;
		}
	}

	public synchronized void stopWorkLoad() {
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
		running = false;
	}

	private void cleanupScheduler() {
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

	public synchronized void terminate() {
		stopWorkLoad();
		cleanupScheduler();
	}
}
