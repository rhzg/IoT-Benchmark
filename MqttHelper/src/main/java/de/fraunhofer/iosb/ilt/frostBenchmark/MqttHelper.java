package de.fraunhofer.iosb.ilt.frostBenchmark;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import java.net.URISyntaxException;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

public class MqttHelper implements MqttCallback {

	public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MqttHelper.class);

	public enum STATE {
		BEGIN,
		CONNECTED,
		PUBLISHED,
		SUBSCRIBED,
		DISCONNECTED,
		FINISH,
		ERROR,
		DISCONNECT;
	}

	static final String BROKER = "BROKER";

	private STATE state = STATE.BEGIN;
	private MqttAsyncClient client;
	private MemoryPersistence persistence;
	private String clientId;
	private String brokerUrl;
	private MqttConnectOptions conOpt;
	private boolean clean;
	private Throwable ex = null;
	private final Object waiter = new Object();
	private boolean donext = false;

	/**
	 * Constructs an instance of the sample client wrapper
	 *
	 * @param brokerUrl the url to connect to
	 * @param clientId the client id to connect with
	 * @param cleanSession clear state at end of connection or not (durable or
	 * non-durable subscriptions)
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

			// using in memory persistence
			persistence = new MemoryPersistence();

			// Construct the MqttClient instance
			client = new MqttAsyncClient(brokerUrl, clientId, persistence);

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
		synchronized (waiter) {
			waiter.notify();
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
				LOGGER.debug("Sleeping...");
				try {
					waiter.wait(maxTTW);
				} catch (InterruptedException e) {
					LOGGER.error("timed out", e);
				}
				LOGGER.debug("Woken up.");
				if (ex != null) {
					throw (MqttException) ex;
				}
			}
			donext = false;
		}
	}

	/**
	 * Subscribe to a topic on an MQTT server Once subscribed this method waits
	 * for the messages to arrive from the server that match the subscription.
	 * It continues listening for messages until the enter key is pressed.
	 *
	 * @param topicName to subscribe to (can be wild carded)
	 * @param qos the maximum quality of service to receive messages at for this
	 * subscription
	 * @throws MqttException
	 */
	public void subscribe(String topicName, int qos) throws Throwable {
		if (client == null) {
			createClient();
		}
		// Use a state machine to decide which step to do next. State change occurs
		// when a notification is received that an MQTT action has completed
		while (state != STATE.FINISH) {
			LOGGER.debug("Handling state: {}", state);
			switch (state) {
				case BEGIN:
					// Connect using a non-blocking connect
					MqttConnector con = new MqttConnector();
					con.doConnect();
					break;

				case CONNECTED:
					// Subscribe using a non-blocking subscribe
					Subscriber sub = new Subscriber();
					sub.doSubscribe(topicName, qos);
					break;

				case SUBSCRIBED:
					// Block until Enter is pressed allowing messages to arrive
					LOGGER.trace("Subscribed");
					synchronized (waiter) {
						waiter.wait();
					}
					LOGGER.info("Disconnecting...");
					state = STATE.DISCONNECT;
					donext = true;
					break;

				case DISCONNECT:
					Disconnector disc = new Disconnector();
					disc.doDisconnect();
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
			waitForStateChange(10000);
		}
		LOGGER.info("Exiting main loop.");

	}

	/**
	 * @see MqttCallback#connectionLost(Throwable)
	 */
	@Override
	public void connectionLost(Throwable cause) {
		// Called when the connection to the server has been lost.
		// An application may choose to implement reconnection
		// logic at this point. This sample simply exits.
		LOGGER.error("Connection to " + brokerUrl + " lost!", cause);
		System.exit(1);
	}

	/**
	 * @see MqttCallback#deliveryComplete(IMqttDeliveryToken)
	 */
	@Override
	public void deliveryComplete(IMqttDeliveryToken token) {
		LOGGER.trace("Delivery complete callback: Publish Completed {}", token);
	}

	/**
	 * @throws org.eclipse.paho.client.mqttv3.MqttException
	 * @throws URISyntaxException
	 * @throws ServiceFailureException
	 * @see MqttCallback#messageArrived(String, MqttMessage)
	 */
	@Override
	public void messageArrived(String topic, MqttMessage message) throws MqttException, ServiceFailureException, URISyntaxException {
		JSONObject msg = new JSONObject(new String(message.getPayload()));
		LOGGER.trace("Message Arrived: {}", msg.toString());
	}

	/**
	 * *************************************************************
	 */
	/* End of MqttCallback methods */
	/**
	 * *************************************************************
	 */
	/**
	 * Connect in a non-blocking way and then sit back and wait to be notified
	 * that the action has completed.
	 */
	public class MqttConnector {

		public MqttConnector() {
		}

		public void doConnect() {
			// Connect to the server
			// Get a token and setup an asynchronous listener on the token which
			// will be notified once the connect completes
			LOGGER.trace("Connecting to {} with client ID {}", brokerUrl, client.getClientId());

			IMqttActionListener conListener = new IMqttActionListener() {
				@Override
				public void onSuccess(IMqttToken asyncActionToken) {
					LOGGER.trace("Connected");
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

				public void carryOn() {
					synchronized (waiter) {
						donext = true;
						waiter.notifyAll();
					}
				}
			};

			try {
				// Connect using a non-blocking connect
				client.connect(conOpt, "SensorCluster Connect for Command Stream", conListener);
			} catch (MqttException e) {
				// If though it is a non-blocking connect an exception can be
				// thrown if validation of parms fails or other checks such
				// as already connected fail.
				state = STATE.ERROR;
				donext = true;
				ex = e;
			}
		}
	}

	/**
	 * Subscribe in a non-blocking way and then sit back and wait to be notified
	 * that the action has completed.
	 */
	public class Subscriber {

		public void doSubscribe(String topicName, int qos) {
			// Make a subscription
			// Get a token and setup an asynchronous listener on the token which
			// will be notified once the subscription is in place.
			LOGGER.trace("Subscribing to topic \"" + topicName + "\" qos " + qos);

			IMqttActionListener subListener = new IMqttActionListener() {
				@Override
				public void onSuccess(IMqttToken asyncActionToken) {
					LOGGER.trace("Subscribe Completed");
					state = STATE.SUBSCRIBED;
					carryOn();
				}

				@Override
				public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
					ex = exception;
					state = STATE.ERROR;
					LOGGER.trace("Subscribe failed" + exception);
					carryOn();
				}

				public void carryOn() {
					synchronized (waiter) {
						donext = true;
						waiter.notifyAll();
					}
				}
			};

			try {
				client.subscribe(topicName, qos, "Subscribe sample context", subListener);
			} catch (MqttException e) {
				state = STATE.ERROR;
				donext = true;
				ex = e;
			}
		}
	}

	/**
	 * Disconnect in a non-blocking way and then sit back and wait to be
	 * notified that the action has completed.
	 */
	public class Disconnector {

		public void doDisconnect() {
			// Disconnect the client
			LOGGER.trace("Disconnecting");

			IMqttActionListener discListener = new IMqttActionListener() {
				@Override
				public void onSuccess(IMqttToken asyncActionToken) {
					LOGGER.trace("Disconnect Completed");
					state = STATE.DISCONNECTED;
					carryOn();
				}

				@Override
				public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
					ex = exception;
					state = STATE.ERROR;
					LOGGER.error("Disconnect failed", exception);
					carryOn();
				}

				public void carryOn() {
					synchronized (waiter) {
						donext = true;
						waiter.notifyAll();
					}
				}
			};

			try {
				client.disconnect("Disconnect sample context", discListener);
			} catch (MqttException exc) {
				LOGGER.error("Exception closing connection.", exc);
				state = STATE.ERROR;
				donext = true;
				ex = exc;
			}
			try {
				client.close();
			} catch (MqttException ex) {
				LOGGER.error("Exception closing connection.", ex);
			}
			System.exit(0);
		}
	}
}
