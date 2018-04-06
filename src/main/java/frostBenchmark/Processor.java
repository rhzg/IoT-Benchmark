package frostBenchmark;

import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.jackson.ObjectMapperFactory;
import de.fraunhofer.iosb.ilt.sta.model.Datastream;
import de.fraunhofer.iosb.ilt.sta.model.Observation;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
import de.fraunhofer.iosb.ilt.sta.model.ext.EntityList;

public class Processor extends MqttHelper implements Runnable {
	static int qos = 2;
	static int port = 1883;

	String dataStreamTopic = null;

	public Processor(String brokerUrl, String clientId, boolean cleanSession) throws MqttException {
		super(brokerUrl, clientId, cleanSession);
		// TODO Auto-generated constructor stub

	}

	public void run() {
		// TODO Auto-generated method stub
		try {
			subscribe(dataStreamTopic, qos);
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static void main(String[] args) throws IOException, URISyntaxException, ServiceFailureException {
		String broker;
		String clientId = "BechmarkProcessor-" + System.currentTimeMillis();
		boolean cleanSession = true; // Non durable subscriptions
		String protocol = "tcp://";

		broker = System.getenv(Run.BROKER);
		if (broker == null) {
			broker = "localhost";
		}
		String url = protocol + broker + ":" + port;

		BenchData.initialize(System.getenv(BenchData.BASE_URL), System.getenv(BenchData.SESSION));
		Thing benchmarkThing = BenchData.getBenchmarkThing();

		try {
			// create processors for each Datastream

			EntityList<Datastream> dataStreams = benchmarkThing.datastreams().query().list();
			for (Datastream dataStream : dataStreams) {
				Processor processor = new Processor(url, clientId + "-" + dataStream.getId().toString(), cleanSession);
				processor.dataStreamTopic = "v1.0/Datastreams(" + dataStream.getId().toString() + ")/Observations";
				new Thread(processor).start();
			}

		} catch (MqttException me) {
			// Display full details of any exception that occurs
			Run.LOGGER.error("reason " + me.getReasonCode());
			Run.LOGGER.error("msg " + me.getMessage());
			Run.LOGGER.error("loc " + me.getLocalizedMessage());
			Run.LOGGER.error("cause " + me.getCause());
			Run.LOGGER.error("excep " + me);
			me.printStackTrace();
		} catch (Throwable th) {
			Run.LOGGER.error("Throwable caught " + th);
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

		// Object p = msg.get("phenomenonTime");
		// String o = msg.get("@iot.selfLink").toString();
		String id = msg.get("@iot.id").toString();
		long longId = Long.parseLong(id);

		final ObjectMapper mapper = ObjectMapperFactory.get();
		Observation entity;
		try {
			entity = mapper.readValue(message.getPayload(), Observation.class);
			entity.setService(Run.service);
		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		Observation obs = BenchData.service.observations().find(longId);

		// Run.LOGGER.info("Update received, phenomenonTime = " + p.toString() + ", " +
		// o.toString());
		// Run.LOGGER.info(msg.toString());
		System.out.print('.');

		processObservation(obs);
	}

	private void processObservation(Observation obs) {

	}

}
