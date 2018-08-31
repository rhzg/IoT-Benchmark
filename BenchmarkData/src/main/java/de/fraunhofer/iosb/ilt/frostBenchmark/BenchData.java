package de.fraunhofer.iosb.ilt.frostBenchmark;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.Utils;
import de.fraunhofer.iosb.ilt.sta.model.Datastream;
import de.fraunhofer.iosb.ilt.sta.model.Location;
import de.fraunhofer.iosb.ilt.sta.model.ObservedProperty;
import de.fraunhofer.iosb.ilt.sta.model.Sensor;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
import de.fraunhofer.iosb.ilt.sta.model.ext.UnitOfMeasurement;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.geojson.Point;
import org.slf4j.LoggerFactory;

public class BenchData {

	public static final String TAG_NAME = "NAME";
	public static final String DFLT_NAME = "properties";

	public static final String TAG_OUTPUT_PERIOD = "outputPeriod";
	public static final int DFLT_OUTPUT_PERIOD = 5;

	public static final int DFLT_PORT = 1883;

	public static final String TAG_SESSION = "SESSION";
	public static final String TAG_DURATION = "duration";
	public static final String TAG_TYPE = "type";
	public static final String VALUE_TYPE_CONTROL = "control";
	public static final String VALUE_TYPE_SENSOR = "sensor";

	public static final String BENCHMARK = "Benchmark";
	public static final String SESSION = TAG_SESSION;
	public static final String BASE_URL = "BASE_URL";
	public static final String BROKER = "BROKER";
	public static final String PROXYHOST = "proxyhost";
	public static final String PROXYPORT = "proxyport";

	public static String name = DFLT_NAME;
	public static URL baseUri = null;
	public static SensorThingsService service = null;
	public static String sessionId;
	public static String broker;
	public static int outputPeriod;

	private static Thing sessionThing = null;
	private static final Object lock = new Object();

	public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(BenchData.class);

	public static void initialize() {
		String baseUriStr = getEnv(BenchData.BASE_URL, "http://localhost:8080/FROST-Server/v1.0/").trim();
		LOGGER.info("Using SensorThings Service at {}", baseUriStr);

		name = getEnv(TAG_NAME, DFLT_NAME);
		sessionId = getEnv(BenchData.SESSION, "0815").trim();
		outputPeriod = getEnv(TAG_OUTPUT_PERIOD, DFLT_OUTPUT_PERIOD);

		broker = getEnv(BROKER, "localhost").trim();
		if (!broker.contains(":")) {
			broker = "tcp://" + BenchData.broker + ":" + DFLT_PORT;
		}

		try {
			LOGGER.debug("Creating SensorThingsService");
			baseUri = new URL(baseUriStr);
			service = new SensorThingsService(baseUri);

			PoolingHttpClientConnectionManager conManager = new PoolingHttpClientConnectionManager();
			conManager.setMaxTotal(500);
			conManager.setDefaultMaxPerRoute(200);
			CloseableHttpClient httpClient = HttpClients.custom()
					.useSystemProperties()
					.setConnectionManager(conManager)
					.build();
			service.setClient(httpClient);

			LOGGER.debug("Creating SensorThingsService done");
		} catch (MalformedURLException | URISyntaxException e) {
			LOGGER.error("Exception:", e);
		}
		LOGGER.trace("Initialized for: {} [SessionId = {}]", baseUriStr, sessionId);
	}

	public static String getEnv(String name, String deflt) {
		String value = System.getenv(name);
		if (value == null) {
			return deflt;
		}
		return value;
	}

	public static int getEnv(String name, int deflt) {
		String value = System.getenv(name);
		if (value == null) {
			return deflt;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ex) {
			LOGGER.trace("Failed to parse parameter to int.", ex);
			LOGGER.info("Value for {} ({}) was not an Integer", name, value);
			return deflt;
		}
	}

	public static Thing getBenchmarkThing() {
		// if sessionThing already found, just return it;
		if (sessionThing != null) {
			return sessionThing;
		}
		synchronized (lock) {
			// check if service has been initialized
			if (service == null) {
				LOGGER.error("uninitialized service call");
				return null;
			}

			// find the Benchmark Thing to control the load generators
			Thing myThing = null;

			// search for the session thing
			try {
				myThing = service.things().query()
						.select("name", "id", "description")
						.filter("properties/" + TAG_TYPE + " eq '" + VALUE_TYPE_CONTROL + "' and properties/" + SESSION + " eq '" + sessionId + "'")
						.first();
				if (myThing == null) {
					myThing = new Thing(BENCHMARK, sessionId);
					Map<String, Object> thingProperties = new HashMap<>();
					thingProperties.put(BenchProperties.TAG_STATUS, BenchProperties.STATUS.FINISHED);
					thingProperties.put(TAG_TYPE, VALUE_TYPE_CONTROL);
					thingProperties.put(SESSION, sessionId);
					myThing.setProperties(thingProperties);
					service.create(myThing);

					Location location = new Location("BenchmarkThing", "The location of the benchmark thing.", "application/geo+json", new Point(8, 52));
					location.getThings().add(myThing.withOnlyId());
					service.create(location);
					LOGGER.info("Created main benchmark Thing: {}", myThing);
				} else {
					LOGGER.info("Using existing main benchmark Thing: {}", myThing);
				}
			} catch (ServiceFailureException e) {
				LOGGER.error("Exception:", e);
			}

			sessionThing = myThing;
			return myThing;
		}
	}

	/**
	 * Find a Datastream with given name @param within the Thing as defined in
	 * the initialized session context.
	 *
	 * @param name
	 * @return
	 * @throws de.fraunhofer.iosb.ilt.sta.ServiceFailureException
	 */
	public static Datastream getDatastream(String name) throws ServiceFailureException {
		Datastream dataStream;
		LOGGER.debug("getSensor: " + name);

		dataStream = service.datastreams().query()
				.filter("name eq '" + Utils.escapeForStringConstant(name) + "' and properties/" + TAG_SESSION + " eq '" + sessionId + "'")
				.first();

		if (dataStream == null) {
			dataStream = createDatastream(name);
		}
		return dataStream;
	}

	/**
	 * Creates a new Datastream with the given name along with associated
	 * Sensors and Locations
	 *
	 * @param name
	 * @return
	 * @throws ServiceFailureException
	 */
	private static Datastream createDatastream(String name) throws ServiceFailureException {
		Datastream dataStream;

		Sensor sensor = new Sensor(name, "Sensor for creating benchmark data", "text", "Some metadata.");
		service.create(sensor);
		LOGGER.debug("Sensor new id " + String.valueOf(sensor.getId()));

		ObservedProperty obsProp1 = new ObservedProperty(name, URI.create("http://ucom.org/temperature"), "observation rate");
		service.create(obsProp1);

		Thing thing = new Thing(name, "Benchmark Thing");
		Map<String, Object> thingProps = new HashMap<>();
		thingProps.put(TAG_TYPE, VALUE_TYPE_SENSOR);
		thingProps.put(TAG_SESSION, sessionId);

		Location location = new Location(name, "The location of a benchmark thing.", "application/geo+json", new Point(8, 52));
		thing.getLocations().add(location);

		dataStream = new Datastream(name, "Benchmark Random Stream", name,
				new UnitOfMeasurement("observation rate", "observations per sec", ""));
		dataStream.setThing(thing);
		dataStream.setSensor(sensor);
		dataStream.setObservedProperty(obsProp1);
		Map<String, Object> dsProps = new HashMap<>();
		dsProps.put(SESSION, sessionId);
		dataStream.setProperties(dsProps);
		service.create(dataStream);

		return dataStream;
	}
}
