package de.fraunhofer.iosb.ilt.frostBenchmark;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.jackson.ObjectMapperFactory;
import de.fraunhofer.iosb.ilt.sta.model.Observation;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.LoggerFactory;

public class ProcessorWorker extends MqttHelper implements Runnable {

	public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ProcessorWorker.class);

	private AtomicLong notificationsReceived = new AtomicLong();

	private String dataStreamTopic = null;
	private Thread thread;

	public ProcessorWorker(String brokerUrl, String clientId, boolean cleanSession) throws MqttException {
		super(brokerUrl, clientId, cleanSession);

	}

	public void start() {
		thread = new Thread(this);
		thread.start();
	}

	public void stop() {
		setState(STATE.DISCONNECT);
	}

	@Override
	public void run() {
		try {
			subscribeAndWait(dataStreamTopic, SubscriberCluster.QOS);
		} catch (Throwable e) {
			LOGGER.error("Exception: ", e);
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

		final ObjectMapper mapper = ObjectMapperFactory.get();
		Observation entity;
		try {
			entity = mapper.readValue(message.getPayload(), Observation.class);
			processObservation(entity);
		} catch (JsonParseException e) {
			LOGGER.error("Exception: ", e);
		} catch (JsonMappingException e) {
			LOGGER.error("Exception: ", e);
		} catch (IOException e) {
			LOGGER.error("Exception: ", e);
		}
	}

	private void processObservation(Observation obs) {
		incNotificationsReceived();
	}

	public String getDataStreamTopic() {
		return dataStreamTopic;
	}

	public void setDataStreamTopic(String dataStreamTopic) {
		this.dataStreamTopic = dataStreamTopic;
	}

	public long getNotificationsReceived() {
		return notificationsReceived.get();
	}

	public long resetNotificationsReceived() {
		return this.notificationsReceived.getAndSet(0);
	}

	public void incNotificationsReceived() {
		notificationsReceived.incrementAndGet();
	}

}
