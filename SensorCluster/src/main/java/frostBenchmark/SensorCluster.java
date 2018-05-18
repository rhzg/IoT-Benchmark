package frostBenchmark;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
import frostBenchmark.BenchProperties.STATUS;
import java.net.URISyntaxException;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

public class SensorCluster extends MqttHelper {

	public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(SensorCluster.class);
	public static final int QOS = 2;
	public static final int PORT = 1883;

	private SensorScheduler scheduler;

	public SensorCluster(String brokerUrl, String clientId, boolean cleanSession) throws MqttException {
		super(brokerUrl, clientId, cleanSession);
	}

	public void init() throws Throwable {
		scheduler = new SensorScheduler();
		scheduler.initWorkLoad();

		Thing benchmarkThing = BenchData.getBenchmarkThing();
		String topic = "v1.0/Things(" + benchmarkThing.getId().toString() + ")/properties";
		subscribeAndWait(topic, QOS);
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

		String clientId = "BechmarkSensorCluster-" + System.currentTimeMillis();
		boolean cleanSession = true; // Non durable subscriptions
		String protocol = "tcp://";

		BenchData.initialize();

		LOGGER.info("Starting {} Threads to simulate {} Sensor Data Generators with {} msec post period",
				BenchProperties.workers,
				BenchProperties.sensors,
				BenchProperties.period
		);

		try {
			// Create an instance of the Sample client wrapper
			LOGGER.trace("using mqtt broker: " + BenchData.broker);
			String url = protocol + BenchData.broker + ":" + PORT;
			SensorCluster sensors = new SensorCluster(url, clientId, cleanSession);
			sensors.init();
		} catch (MqttException me) {
			LOGGER.error("MQTT exception", me);
		} catch (Throwable me) {
			LOGGER.error("Something bad happened.", me);
		}
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

		JSONObject msg = new JSONObject(new String(message.getPayload()));
		JSONObject p = (JSONObject) msg.get("properties");

		STATUS benchState = STATUS.TERMINATE;
		String statusString = p.getString(BenchProperties.TAG_STATUS);
		try {
			benchState = STATUS.valueOf(statusString.toUpperCase());
		} catch (IllegalArgumentException exc) {
			LOGGER.error("Received unknown status value: {}", statusString);
			LOGGER.trace("Exception: ", exc);
		}

		LOGGER.info("Entering {} mode", benchState);
		switch (benchState) {
			case RUNNING:
				// start the client
				scheduler.startWorkLoad();
				break;

			case FINISHED:
				// get the results
				scheduler.stopWorkLoad();
				break;

			case TERMINATE:
				LOGGER.info("Terminate Command received - exit process");
				setState(STATE.DISCONNECT);
				scheduler.terminate();
				break;

			default:
				LOGGER.error("Unhandled state: {}", benchState);
		}
	}
}
