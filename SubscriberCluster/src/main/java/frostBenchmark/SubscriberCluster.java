package frostBenchmark;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Random;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Datastream;
import de.fraunhofer.iosb.ilt.sta.model.Observation;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
import de.fraunhofer.iosb.ilt.sta.model.ext.EntityList;

public class SubscriberCluster extends MqttHelper {
	static int qos = 2;
	static int port = 1883;
	
	private static long startTime = 0;


	public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(SubscriberCluster.class);

	public SubscriberCluster(String brokerUrl, String clientId, boolean cleanSession) throws MqttException {
		super(brokerUrl, clientId, cleanSession);
	}

	public static void main(String[] args) throws IOException, URISyntaxException, ServiceFailureException {
		String broker;
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
					processor.dataStreamTopic = "v1.0/Datastreams(" + dataStream.getId().toString() + ")/Observations";
					new Thread(processor).start();
					nbProcessors++;
				}
			}
			LOGGER.info(nbProcessors + " created out of " + dataStreams.size() + " Datastreams (coverage="
					+ 100 * nbProcessors / dataStreams.size() + "[" + BenchProperties.coverage + "]");

			// subscribe for benchmark commands
			String topic = "v1.0/Things(" + benchmarkThing.getId().toString() + ")/properties";
			SubscriberCluster processor = new SubscriberCluster(url, clientId, cleanSession);
			processor.subscribe(topic, qos);

		} catch (MqttException me) {
			// Display full details of any exception that occurs
			LOGGER.error("reason " + me.getReasonCode());
			LOGGER.error("msg    " + me.getMessage());
			LOGGER.error("loc    " + me.getLocalizedMessage());
			LOGGER.error("cause  " + me.getCause());
			LOGGER.error("excep  " + me);
			me.printStackTrace();
		} catch (Throwable th) {
			LOGGER.error("Throwable caught " + th);
			th.printStackTrace();
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
		String benchState = p.getString("state");

		LOGGER.trace("Entering " + benchState + " mode");

		if (benchState.equalsIgnoreCase(RUNNING)) {
			// start the client
			LOGGER.info("Starting Processor Test");
			startTime = System.currentTimeMillis();
			ProcessorWorker.setNotificationsReceived(0);
		} else if (benchState.equalsIgnoreCase(FINISHED)) {
			// get the results
			long endTime = System.currentTimeMillis();
			
			Datastream ds = BenchData.getDatastream("SubsriberCluster");
			double rate = (1000 * ProcessorWorker.getNotificationsReceived()) / (endTime-startTime);
			try {
				BenchData.service.create(new Observation(rate, ds));
			} catch (ServiceFailureException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			LOGGER.info(ProcessorWorker.getNotificationsReceived() + " Notifications received");
			LOGGER.info((1000 * ProcessorWorker.getNotificationsReceived()) / (endTime-startTime) + " notifications per sec");
		} else if (benchState.equalsIgnoreCase(TERMINATE)) {
			LOGGER.info("Terminate Command received - exit process");
			state = DISCONNECT;
			this.waiter.notify();
			LOGGER.info("Terminate");
		}
	}

}
