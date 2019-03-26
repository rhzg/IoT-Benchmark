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

public class AnalyticsScheduler {

	public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(AnalyticsScheduler.class);

	private ScheduledExecutorService analyticsScheduler;
	private ScheduledExecutorService outputScheduler;
	private ScheduledFuture<?> outputTask;

	/**
	 * How many seconds between stats outputs.
	 */
	private int outputPeriod = 1;

	private List<AnalyticClient> dsList = new ArrayList<>();
	private long startTime = 0;
	private long stopTime = 0;

	BenchProperties settings;
	private boolean running = false;

	/**
	 * TODO pass in settings object instead of using static BenchProperties
	 */
	public AnalyticsScheduler() {
		//BenchData.initialize();
		settings = new BenchProperties().readFromEnvironment();
		analyticsScheduler = Executors.newScheduledThreadPool(settings.workers);
		outputScheduler = Executors.newSingleThreadScheduledExecutor();
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

	private void sendRateObservation (double rate) {
		try {
			Datastream ds = AnalyticsCluster.resultData.getDatastream(AnalyticsCluster.resultData.getEnv(BenchData.TAG_NAME, "AnalyticsCluster"));
			AnalyticsCluster.resultData.service.create(new Observation(rate, ds));
		} catch (ServiceFailureException exc) {
			LOGGER.error("Failed to send current rate observation.", exc);
		}
	}

	public synchronized void initWorkLoad(JsonNode updatedProperties) throws ServiceFailureException, URISyntaxException {
		if (running) {
			stopWorkLoad();
		}
		int oldWorkerCount = settings.workers;
		int oldPeriod = settings.period;
		int oldJitter = settings.jitter;
		int oldAnalyticLoops = settings.analyticLoops;
		int oldAnalyticJobs = settings.analyticJobs;
		settings.readFromJsonNode(updatedProperties);

		LOGGER.debug("Benchmark initializing, starting workers");
		logUpdates(BenchProperties.TAG_PERIOD, oldPeriod, settings.period);
		logUpdates(BenchProperties.TAG_JITTER, oldJitter, settings.jitter);
		logUpdates(BenchProperties.TAG_WORKERS, oldWorkerCount, settings.workers);
		logUpdates(BenchProperties.TAG_ANALYTIC_LOOPS, oldAnalyticLoops, settings.analyticLoops);
		logUpdates(BenchProperties.TAG_ANALYTIC_JOBS, oldAnalyticJobs, settings.analyticJobs);

		if (oldWorkerCount != settings.workers) {
			cleanupScheduler(false);
			analyticsScheduler = Executors.newScheduledThreadPool(settings.workers);
		}

		int haveCount = dsList.size();
		if (settings.analyticJobs != haveCount) {
			if (settings.analyticJobs > haveCount) {
				int toAdd = settings.analyticJobs - haveCount;
				LOGGER.info("Setting up {} analytics...", toAdd);
				for (int i = haveCount; i < settings.analyticJobs; i++) {
					String name = "Benchmark." + i;
					AnalyticClient sensor = new AnalyticClient(AnalyticsCluster.benchData).intialize(name, settings.analyticLoops);
					dsList.add(sensor);
					if ((i - haveCount) % 100 == 0) {
						LOGGER.info("... {}", i - haveCount);
					}
				}
			}
			if (settings.analyticJobs < haveCount) {
				int toRemove = haveCount - settings.analyticJobs;
				LOGGER.info("Taking down {} analytics...", toRemove);
				while (dsList.size() > settings.analyticJobs) {
					AnalyticClient ds = dsList.remove(dsList.size() - 1);
					ds.cancel();
				}
			}
			LOGGER.info("Done.");
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

		LOGGER.info("Starting workload: {} workers, {} analytics, {} delay, {} jitter.", settings.workers, settings.analyticLoops, settings.period, settings.jitter);
		double delayPerSensor = (double) settings.period;
		double currentDelay = 0;
		for (AnalyticClient sensor : dsList) {
			ScheduledFuture<?> handle = analyticsScheduler.scheduleAtFixedRate(sensor, (long) currentDelay, settings.period - settings.jitter / 2, TimeUnit.MILLISECONDS);
			sensor.setSchedulerHandle(handle);
			currentDelay += delayPerSensor;
		}

		if ((outputTask == null) && (outputPeriod > 0)) {
			outputTask = outputScheduler.scheduleAtFixedRate(this::printStats, outputPeriod, outputPeriod, TimeUnit.SECONDS);
		}
	}

	public synchronized void stopWorkLoad() {
		LOGGER.trace("Benchmark finishing");

		if (outputTask != null) {
			outputTask.cancel(true);
			outputTask = null;
		}

		for (AnalyticClient sensor : dsList) {
			sensor.cancel();
		}

		stopTime = System.currentTimeMillis();
		printStats();

		for (AnalyticClient sensor : dsList) {
			sensor.reset();
		}

		LOGGER.info("Benchmark finished");
		running = false;
	}

	public void printStats() {
		long curTime = System.currentTimeMillis();
		long analyticCycles = 0;
		for (AnalyticClient worker : dsList) {
			LOGGER.debug("raw {}, obs {}, cyl {}", analyticCycles, worker.getCreatedObsCount(),worker.getAnalyticCycles());
			analyticCycles += worker.getCreatedObsCount() * worker.getAnalyticCycles();
		}

		double rate = analyticCycles / ((curTime - startTime));
		LOGGER.info("Cycles done: {} [M/s]", String.format("%.2f", rate));

		sendRateObservation(rate);
	}

	private void cleanupScheduler(boolean all) {
		if (all) {
			outputScheduler.shutdown();
		}
		analyticsScheduler.shutdown();
		boolean allOk = true;
		try {
			allOk = analyticsScheduler.awaitTermination(2, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			LOGGER.trace("Woken up, wait done.", ex);
		}
		if (!allOk) {
			analyticsScheduler.shutdownNow();
		}
	}

	public synchronized void terminate() {
		stopWorkLoad();
		cleanupScheduler(true);
	}

	/**
	 * How many seconds between stats outputs.
	 *
	 * @return the outputPeriod
	 */
	public int getOutputPeriod() {
		return outputPeriod;
	}

	/**
	 * How many seconds between stats outputs.
	 *
	 * @param outputRate the outputPeriod to set
	 */
	public void setOutputPeriod(int outputRate) {
		this.outputPeriod = outputRate;
	}

}
