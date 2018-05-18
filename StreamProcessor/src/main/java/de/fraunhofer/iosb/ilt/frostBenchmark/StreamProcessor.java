package de.fraunhofer.iosb.ilt.frostBenchmark;

import de.fraunhofer.iosb.ilt.frostBenchmark.BenchData;
import de.fraunhofer.iosb.ilt.frostBenchmark.BenchProperties;
import de.fraunhofer.iosb.ilt.frostBenchmark.MqttHelper;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Datastream;
import de.fraunhofer.iosb.ilt.sta.model.Observation;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
import de.fraunhofer.iosb.ilt.sta.model.ext.EntityList;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Random;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

public class StreamProcessor extends MqttHelper {

	private static long startTime = 0;
	public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(StreamProcessor.class);
	static int qos = 2;
	static int port = 1883;

	public StreamProcessor(String brokerUrl, String clientId, boolean cleanSession) throws MqttException {
		super(brokerUrl, clientId, cleanSession);
	}

	public static void main(String[] args) throws IOException, URISyntaxException, ServiceFailureException {
		String clientId = "BechmarkProcessor-" + System.currentTimeMillis();
		boolean cleanSession = true; // Non durable subscriptions
		String protocol = "tcp://";

		BenchData.initialize();
		String url = protocol + BenchData.broker + ":" + port;
		Thing benchmarkThing = BenchData.getBenchmarkThing();

		try {
			// create processors for Datastream according to coverage
			Random random = new Random();
			int nbProcessors = 0;
			EntityList<Datastream> dataStreams = benchmarkThing.datastreams().query().list();
			for (Datastream dataStream : dataStreams) {
				if (random.nextInt(100) < BenchProperties.coverage) {
					ProcessorWorker processor = new ProcessorWorker(url, clientId + "-" + dataStream.getId().toString(),
							cleanSession);
					processor.setDataStreamTopic("v1.0/Datastreams(" + dataStream.getId().toString() + ")/Observations");
					new Thread(processor).start();
					nbProcessors++;
				}
			}
			LOGGER.info(nbProcessors + " created out of " + dataStreams.size() + " Datastreams (coverage="
					+ 100 * nbProcessors / dataStreams.size() + "[" + BenchProperties.coverage + "]");

			// subscribe for benchmark commands
			String topic = "v1.0/Things(" + benchmarkThing.getId().toString() + ")/properties";
			StreamProcessor processor = new StreamProcessor(url, clientId, cleanSession);
			processor.subscribe(topic, qos);

		} catch (MqttException me) {
			LOGGER.error("MQTT exception", me);
		} catch (Throwable me) {
			LOGGER.error("Something bad happened.", me);
		}
	}

	@Override
	/**
	 * @throws URISyntaxException
	 * @throws ServiceFailureException
	 * @see MqttCallback#messageArrived(String, MqttMessage)
	 */
	public void messageArrived(String topic, MqttMessage message)
			throws MqttException, ServiceFailureException, URISyntaxException {

		JSONObject msg = new JSONObject(new String(message.getPayload()));
		JSONObject p = (JSONObject) msg.get("properties");

		BenchProperties.STATUS benchState = BenchProperties.STATUS.TERMINATE;
		String statusString = p.getString(BenchProperties.TAG_STATUS);
		try {
			benchState = BenchProperties.STATUS.valueOf(statusString.toUpperCase());
		} catch (IllegalArgumentException exc) {
			LOGGER.error("Received unknown status value: {}", statusString);
			LOGGER.trace("Exception: ", exc);
		}

		LOGGER.info("Entering {} mode", benchState);
		switch (benchState) {
			case RUNNING:
				// start the client
				LOGGER.info("Starting Processor Test");
				startTime = System.currentTimeMillis();
				ProcessorWorker.setNotificationsReceived(0);
				break;

			case FINISHED:
				// get the results
				long endTime = System.currentTimeMillis();

				Datastream ds = BenchData.getDatastream("SubsriberCluster");
				double rate = (1000 * ProcessorWorker.getNotificationsReceived()) / (endTime - startTime);
				try {
					BenchData.service.create(new Observation(rate, ds));
				} catch (ServiceFailureException e) {
					LOGGER.trace("Exception: ", e);
				}

				LOGGER.info(ProcessorWorker.getNotificationsReceived() + " Notifications received");
				LOGGER.info((1000 * ProcessorWorker.getNotificationsReceived()) / (endTime - startTime) + " notifications per sec");
				break;

			case TERMINATE:
				LOGGER.info("Terminate Command received - exit process");
				setState(STATE.DISCONNECT);
				LOGGER.info("Terminate");
				break;

			default:
				LOGGER.error("Unhandled state: {}", benchState);
		}
	}

}
