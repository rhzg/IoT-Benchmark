package frostBenchmark;

import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.jackson.ObjectMapperFactory;
import de.fraunhofer.iosb.ilt.sta.model.Observation;

public class ProcessorWorker extends MqttHelper implements Runnable {

	public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ProcessorWorker.class);

	String dataStreamTopic = null;

	static private long notificationsReceived = 0;

	public ProcessorWorker(String brokerUrl, String clientId, boolean cleanSession) throws MqttException {
		super(brokerUrl, clientId, cleanSession);
		// TODO Auto-generated constructor stub

	}

	public void run() {
		// TODO Auto-generated method stub
		try {
			subscribe(dataStreamTopic, Processor.qos);
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			LOGGER.error(e.toString());
			System.exit(1);
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
		processObservation(obs);
	}

	private void processObservation(Observation obs) {
		incNotificationsReceived();
	}

	public static synchronized long getNotificationsReceived() {
		return notificationsReceived;
	}

	public static synchronized void setNotificationsReceived(long notificationsReceived) {
		ProcessorWorker.notificationsReceived = notificationsReceived;
	}

	public static synchronized void incNotificationsReceived() {
		notificationsReceived++;
	}

}
