package de.fraunhofer.iosb.ilt.frostBenchmark;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.LoggerFactory;

public abstract class MqttHelper implements MqttCallback {

	public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MqttHelper.class);

	public enum STATE {
		BEGIN, CONNECTED, PUBLISHED, SUBSCRIBED, DISCONNECT, DISCONNECTED, FINISH, ERROR;
	}

	static final String BROKER = "BROKER";

	private STATE state = STATE.BEGIN;
	private MqttAsyncClient client;
	private String clientId;
	private String brokerUrl;
	private MqttConnectOptions conOpt;
	private boolean clean;
	private Throwable ex = null;
	private final Object waiter = new Object();
	private boolean donext = false;
	private final List<String> subscriptions = new ArrayList<>();
	private Subscriber sub;

	/**
	 * Constructs an instance of the sample client wrapper
	 *
	 * @param brokerUrl    the url to connect to
	 * @param clientId     the client id to connect with
	 * @param cleanSession clear state at end of connection or not (durable or
	 *                     non-durable subscriptions)
	 * @throws MqttException
	 */
	public MqttHelper(String brokerUrl, String clientId, boolean cleanSession) throws MqttException {
		this.brokerUrl = brokerUrl;
		this.clientId = clientId;
		this.clean = cleanSession;
	}

	private void createClient() {
		try {
			// Construct the object that contains connection parameters
			// such as cleanSession and LWT
			conOpt = new MqttConnectOptions();
			conOpt.setCleanSession(clean);
			conOpt.setKeepAliveInterval(30);
			conOpt.setAutomaticReconnect(true);

			// Construct the MqttClient instance
			client = new MqttAsyncClient(brokerUrl, clientId, new MemoryPersistence());

			// Set this wrapper as the callback handler
			client.setCallback(this);

		} catch (MqttException exc) {
			LOGGER.error("Unable to set up client: ", exc);
			System.exit(1);
		}
	}

	public STATE getState() {
		return state;
	}

	public void setState(STATE state) {
		this.state = state;
		carryOn();
	}

	public void carryOn() {
		synchronized (waiter) {
			donext = true;
			waiter.notifyAll();
		}
	}

	/**
	 * Wait for a maximum amount of time for a state change event to occur
	 *
	 * @param maxTTW maximum time to wait in milliseconds
	 * @throws MqttException
	 */
	private void waitForStateChange(int maxTTW) throws MqttException {
		synchronized (waiter) {
			if (!donext) {
				LOGGER.debug("Sleeping max {}s...", maxTTW / 1000);
				try {
					waiter.wait(maxTTW);
				} catch (InterruptedException e) {
					LOGGER.error("Interrupted", e);
				}
				LOGGER.debug("Woken up.");
			}
			donext = false;
		}
	}

	/**
	 * Subscribe to a topic on an MQTT server Once subscribed this method waits for
	 * the messages to arrive from the server that match the subscription. It
	 * continues listening for messages until interrupted.
	 *
	 * @param topicName to subscribeAndWait to (can be wild carded)
	 * @param qos       the maximum quality of service to receive messages at for
	 *                  this subscription
	 * @throws MqttException
	 */
	public void subscribeAndWait(String topicName, int qos) throws Throwable {
		if (client == null) {
			createClient();
		}
		// Use a state machine to decide which step to do next. State change occurs
		// when a notification is received that an MQTT action has completed
		while (state != STATE.FINISH) {
			LOGGER.debug("Handling state: {}", state);
			int waitTime = 10000;
			switch (state) {
			case BEGIN:
				MqttConnector con = new MqttConnector();
				con.doConnect();
				break;

			case CONNECTED:
				if (sub == null) {
					sub = new Subscriber(topicName, qos);
					sub.doSubscribe();
				}
				break;

			case SUBSCRIBED:
				LOGGER.trace("Subscribed");
				// Now we sleep until interrupted.
				waitTime = 0;
				break;

			case DISCONNECT:
				Disconnector disc = new Disconnector();
				disc.doDisconnect();
				waitTime = 30000;
				break;

			case ERROR:
				throw ex;

			case DISCONNECTED:
				state = STATE.FINISH;
				donext = true;
				break;

			default:
				LOGGER.error("Unhandled state: {}", state);
			}
			waitForStateChange(waitTime);
		}
		LOGGER.debug("Exiting main loop.");

	}

	/**
	 * @param cause
	 * @see MqttCallback#connectionLost(Throwable)
	 */
	@Override
	public void connectionLost(Throwable cause) {
		LOGGER.error("Connection to {} lost: {}", brokerUrl, cause.getMessage());
		LOGGER.info("Exception:", cause);
		/* not sure what to do after connectionLost
		 * with the conOpt.setAutomaticReconnect(true) option, this should 
		 * happen automatically
		try {
			client.reconnect();
		} catch (MqttException exc) {
			LOGGER.error("Failed to reconnect.", exc);
			ex = cause;
		}
		 */

		LOGGER.error("got connectionLost - exist with 1");
		System.exit(1);
	}

	/**
	 * @param token
	 * @see MqttCallback#deliveryComplete(IMqttDeliveryToken)
	 */
	@Override
	public void deliveryComplete(IMqttDeliveryToken token) {
		LOGGER.trace("Delivery complete callback: Publish Completed {}", token);
	}

	private void closeClient() {
		try {
			client.close();
		} catch (MqttException exc) {
			ex = exc;
			LOGGER.error("Disconnect failed", exc);
			setState(STATE.ERROR);
		}
	}

	/**
	 * Connect in a non-blocking way and then sit back and wait to be notified that
	 * the action has completed.
	 */
	public class MqttConnector {

		public MqttConnector() {
		}

		public void doConnect() {
			LOGGER.trace("Connecting to {} with client ID {}", brokerUrl, client.getClientId());

			IMqttActionListener conListener = new IMqttActionListener() {
				@Override
				public void onSuccess(IMqttToken asyncActionToken) {
					LOGGER.info("Connected");
					state = STATE.CONNECTED;
					carryOn();
				}

				@Override
				public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
					ex = exception;
					state = STATE.ERROR;
					LOGGER.error("connect failed", exception);
					carryOn();
				}

			};

			try {
				client.connect(conOpt, "SensorCluster Connect for Command Stream", conListener);
			} catch (MqttException e) {
				ex = e;
				state = STATE.ERROR;
				LOGGER.error("connect failed", e);
				carryOn();
			}
		}
	}

	/**
	 * Subscribe in a non-blocking way and then sit back and wait to be notified
	 * that the action has completed.
	 */
	private class Subscriber {
		private String topicName;
		private int qos;

		public Subscriber(String topicName, int qos) {
			this.topicName = topicName;
			this.qos = qos;
		}

		public void doSubscribe() {
			LOGGER.trace("Subscribing to topic '{}' qos {}", topicName, qos);

			IMqttActionListener subListener = new IMqttActionListener() {
				@Override
				public void onSuccess(IMqttToken asyncActionToken) {
					LOGGER.info("Subscribe Completed");
					setState(STATE.SUBSCRIBED);
				}

				@Override
				public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
					LOGGER.warn("Subscribe failed", exception);
					ex = exception;
					setState(STATE.ERROR);
				}
			};

			try {
				client.subscribe(topicName, qos, "Subscribe sample context", subListener);
				subscriptions.add(topicName);
			} catch (MqttException e) {
				LOGGER.warn("Subscribe failed", e);
				ex = e;
				setState(STATE.ERROR);
			}
		}
	}

	/**
	 * Disconnect in a non-blocking way and then sit back and wait to be notified
	 * that the action has completed.
	 */
	public class Disconnector {

		public void doDisconnect() {
			// Disconnect the client
			LOGGER.debug("Disconnecting");

			IMqttActionListener discListener = new IMqttActionListener() {
				@Override
				public void onSuccess(IMqttToken asyncActionToken) {
					LOGGER.info("Disconnect Completed");
					closeClient();
					setState(STATE.DISCONNECTED);
				}

				@Override
				public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
					ex = exception;
					LOGGER.error("Disconnect failed", exception);
					closeClient();
					setState(STATE.ERROR);
				}
			};

			try {
				client.unsubscribe(subscriptions.toArray(new String[subscriptions.size()]));
				client.disconnect(null, discListener);
			} catch (MqttException exc) {
				ex = exc;
				LOGGER.error("Disconnect failed", exc);
				setState(STATE.ERROR);
			}
		}
	}
}
