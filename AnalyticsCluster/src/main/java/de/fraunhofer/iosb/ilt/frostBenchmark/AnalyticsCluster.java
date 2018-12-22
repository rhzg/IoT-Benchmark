package de.fraunhofer.iosb.ilt.frostBenchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.fraunhofer.iosb.ilt.frostBenchmark.BenchProperties.STATUS;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
import java.io.IOException;
import java.net.URISyntaxException;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.LoggerFactory;

public class AnalyticsCluster extends MqttHelper implements TimeoutListener {

	public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(AnalyticsCluster.class);
	public static final int QOS = 2;

	private AnalyticsScheduler scheduler;
	private ObjectMapper parser;
	/**
	 * The name to use when reading properties.
	 */
	private String name = "properties";
	private TimeoutWatcher timeoutWatcher;

	public AnalyticsCluster(String name, String brokerUrl, String clientId, boolean cleanSession) throws MqttException {
		super(brokerUrl, clientId, cleanSession);
		this.parser = new ObjectMapper();
		this.name = name;
		timeoutWatcher = new TimeoutWatcher();
	}

	public void init(BenchProperties benchProperties) throws Throwable {
		timeoutWatcher.addTimeoutListener(this);
		scheduler = new AnalyticsScheduler();
		scheduler.setOutputPeriod(BenchData.outputPeriod);
		scheduler.initWorkLoad(null);

		Thing benchmarkThing = BenchData.getBenchmarkThing();
		String topic = "v1.0/Things(" + benchmarkThing.getId().toString() + ")/properties";
		subscribeAndWait(topic, QOS);
	}

	/**
	 * Check if the given properties has a duration, and set a timeout based on
	 * this.
	 *
	 * @param properties the properties to search a duration in.
	 * @param disable Disable the timeout if no duration is found.
	 */
	private void updateTimeout(JsonNode properties, boolean disable) {
		JsonNode durationNode = properties.get("duration");
		if (durationNode == null || !durationNode.isNumber()) {
			if (disable) {
				timeoutWatcher.setNextTimeout(0);
			}
			return;
		}
		long duration = durationNode.asLong() + 2000;
		timeoutWatcher.setNextTimeout(System.currentTimeMillis() + duration);
		LOGGER.debug("Timeout set to now plus {}ms", duration);
	}

	@Override
	public void timeoutReached() {
		LOGGER.warn("Timeout reached, stopping workload.");
		scheduler.stopWorkLoad();
	}

	/**
	 * @param topic The topic the message arrived on.
	 * @param message The message that arrived.
	 * @throws URISyntaxException If there is a problem with the Service URI.
	 * @throws ServiceFailureException If there is a problem communicating with
	 * the SensorThings service.
	 * @see MqttCallback#messageArrived(String, MqttMessage)
	 */
	@Override
	public void messageArrived(String topic, MqttMessage message) throws ServiceFailureException, URISyntaxException {
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
				// configure the client
				scheduler.initWorkLoad(myProperties);
				break;

			case RUNNING:
				// start the client
				scheduler.startWorkLoad(myProperties);
				updateTimeout(properties, true);
				break;

			case FINISHED:
				// get the results
				scheduler.stopWorkLoad();
				timeoutWatcher.setNextTimeout(0);
				break;

			case TERMINATE:
				LOGGER.info("Terminate Command received - exit process");
				setState(STATE.DISCONNECT);
				scheduler.terminate();
				timeoutWatcher.terminate();
				break;

			default:
				LOGGER.error("Unhandled state: {}", benchState);
		}
	}

	/**
	 * The main entry point of the sample.
	 *
	 * This method handles parsing the arguments specified on the command-line
	 * before performing the specified action.
	 *
	 * @param args ignored
	 * @throws URISyntaxException If there is a problem with the Service URI.
	 * @throws ServiceFailureException If there is a problem communicating with
	 * the SensorThings service.
	 */
	public static void main(String[] args) throws URISyntaxException, ServiceFailureException {

		String clientId = "BechmarkAnalyticCluster-" + System.currentTimeMillis();
		boolean cleanSession = true; // Non durable subscriptions

		BenchData.initialize();
		BenchProperties benchProperties = new BenchProperties().readFromEnvironment();

		LOGGER.info("Starting '{}' with {} Threads to simulate {} Analytic clients with {} msec post period",
				BenchData.name,
				benchProperties.workers,
				benchProperties.analytics,
				benchProperties.period
		);

		try {
			// Create an instance of the Sample client wrapper
			LOGGER.debug("using mqtt broker: {}", BenchData.broker);
			AnalyticsCluster analytics = new AnalyticsCluster(BenchData.name, BenchData.broker, clientId, cleanSession);
			analytics.init(benchProperties);
		} catch (MqttException me) {
			LOGGER.error("MQTT exception", me);
		} catch (Throwable me) {
			LOGGER.error("Something bad happened.", me);
		}
	}
}
