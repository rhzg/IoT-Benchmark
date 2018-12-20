package de.fraunhofer.iosb.ilt.frostBenchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.fraunhofer.iosb.ilt.frostBenchmark.BenchProperties.STATUS;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Datastream;
import de.fraunhofer.iosb.ilt.sta.model.Observation;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.LoggerFactory;

public class SubscriberCluster extends MqttHelper {

	public static final int QOS = 2;
	private static final boolean CLEAN_SESSION = true;

	public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(SubscriberCluster.class);

	private ProcessorScheduler scheduler;
	private static long startTime = 0;
	private ObjectMapper parser;
	/**
	 * The name to use when reading properties.
	 */
	private String name = "properties";
	private String brokerUrl;
	private STATUS currentState;
	
	/**
	 * How many seconds between stats outputs.
	 */
	private int outputPeriod = 2;
	private ScheduledExecutorService outputScheduler;
	private ScheduledFuture<?> outputTask;

	
	public SubscriberCluster(String name, String brokerUrl, String clientId, boolean cleanSession) throws MqttException {
		super(brokerUrl, clientId, cleanSession);
		this.parser = new ObjectMapper();
		this.name = name;
		this.brokerUrl = brokerUrl;
		outputScheduler = Executors.newSingleThreadScheduledExecutor();
	}

	public void init() throws Throwable {
		scheduler = new ProcessorScheduler(brokerUrl);
		scheduler.updateSettings(null);

		Thing benchmarkThing = BenchData.getBenchmarkThing();
		String topic = "v1.0/Things(" + benchmarkThing.getId().toString() + ")/properties";
		subscribeAndWait(topic, QOS);
	}

	/**
	 * @param topic The topic the message arrived on.
	 * @param message The message that arrived
	 * @throws org.eclipse.paho.client.mqttv3.MqttException if MQTT is confused.
	 * @throws URISyntaxException If your URLs are bad.
	 * @throws ServiceFailureException If the SensorThings Service has a hickup.
	 */
	@Override
	public void messageArrived(String topic, MqttMessage message) throws MqttException, ServiceFailureException, URISyntaxException {
		JsonNode msg = null;
		try {
			msg = parser.readTree(new String(message.getPayload()));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			LOGGER.error("can not parse mqtt message", e);
			System.exit(1);
		}

		JsonNode properties = msg.get("properties");
		JsonNode myProperties = properties.get(name);

		STATUS benchState = STATUS.TERMINATE;
		String statusString = properties.get(BenchProperties.TAG_STATUS).asText();
		try {
			benchState = STATUS.valueOf(statusString.toUpperCase());
		} catch (IllegalArgumentException exc) {
			LOGGER.error("Received unknown status value: {}", statusString);
			LOGGER.trace("Exception: ", exc);
		}

		LOGGER.info("Entering {} mode", benchState);
		switch (benchState) {
			case INITIALIZE:
				scheduler.updateSettings(myProperties);
				break;

			case RUNNING:
				// start the client
				if (currentState == STATUS.RUNNING) {
					// settings update...
					printStats();
				}
				LOGGER.info("Starting Processor Test");
				startStats();
				scheduler.updateSettings(myProperties);
				break;

			case FINISHED:
				// stop reporter task
				if (outputTask != null) {
					outputTask.cancel(true);
					outputTask = null;
				}
				// get the results
				printStats();
				break;

			case TERMINATE:
				LOGGER.info("Terminate Command received - exit process");
				setState(STATE.DISCONNECT);
				LOGGER.info("Terminate");
				scheduler.terminate();
				break;

			default:
				LOGGER.error("Unhandled state: {}", benchState);
		}
		currentState = benchState;
	}

	private void startStats() {
		startTime = System.currentTimeMillis();
		scheduler.resetNotificationCount();
		if (outputTask == null) {
			outputTask = outputScheduler.scheduleAtFixedRate(this::printStats, outputPeriod, outputPeriod, TimeUnit.SECONDS);
		}
	}

	private void printStats() {
		long endTime = System.currentTimeMillis();

		long notificationCount = scheduler.getNotificationCount();
		double rate = (1000.0 * notificationCount) / (endTime - startTime);
		try {
			Datastream ds = BenchData.getDatastream("SubscriberCluster");
			BenchData.service.create(new Observation(rate, ds));
		} catch (ServiceFailureException e) {
			LOGGER.trace("Exception: ", e);
		}

		LOGGER.info("-=> {} Notifications received", notificationCount);
		LOGGER.info("-=> {} notifications per sec", String.format("%.2f", rate));
	}

	public static void main(String[] args) throws IOException, URISyntaxException, ServiceFailureException {
		String clientId = "BechmarkProcessor-" + System.currentTimeMillis();

		BenchData.initialize();
		BenchProperties benchProperties = new BenchProperties().readFromEnvironment();

		LOGGER.info("Starting '{}' with a coverage of {}.", BenchData.name, benchProperties.coverage);

		try {
			SubscriberCluster cluster = new SubscriberCluster(BenchData.name, BenchData.broker, clientId, CLEAN_SESSION);
			cluster.init();
		} catch (MqttException me) {
			LOGGER.error("MQTT exception", me);
		} catch (Throwable me) {
			LOGGER.error("Something bad happened.", me);
		}
	}

}
