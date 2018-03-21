package frostBenchmark;

import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.paho.client.mqttv3.MqttException;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;

public class Processor extends MqttHelper {

	public Processor(String brokerUrl, String clientId, boolean cleanSession) throws MqttException {
		super(brokerUrl, clientId, cleanSession);
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) throws IOException, URISyntaxException, ServiceFailureException {
		String topic = null;
		int qos = 2;
		String broker;
		int port = 1883;
		String clientId = "BechmarkProcessor-" + System.currentTimeMillis();
		boolean cleanSession = true; // Non durable subscriptions
		String protocol = "tcp://";

		// SensorThings Server settings
		Run.initializeSerice();
		Run.initWorkLoad();

		topic = "v1.0/Things(" + DataSource.thingId.toString() + ")/properties";

		try {
			// Create an instance of the Sample client wrapper
			broker = Run.props.getProperty(Run.BROKER);
			if (broker == null) {
				broker = "localhost";
			}
			String url = protocol + broker + ":" + port;
			SensorCluster sensors = new SensorCluster(url, clientId, cleanSession);

			sensors.subscribe(topic, qos);

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

}
