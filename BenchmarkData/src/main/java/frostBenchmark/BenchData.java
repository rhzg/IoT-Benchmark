package frostBenchmark;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;

import org.geojson.Point;
import org.slf4j.LoggerFactory;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Datastream;
import de.fraunhofer.iosb.ilt.sta.model.Location;
import de.fraunhofer.iosb.ilt.sta.model.ObservedProperty;
import de.fraunhofer.iosb.ilt.sta.model.Sensor;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
import de.fraunhofer.iosb.ilt.sta.model.ext.EntityList;
import de.fraunhofer.iosb.ilt.sta.model.ext.UnitOfMeasurement;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;

public class BenchData {
	static final String BENCHMARK = "Benchmark";
	static final String SESSION = "SESSION";
	static final String BASE_URL = "BASE_URL";
	static final String BROKER = "BROKER";
	static final String PROXYHOST = "proxyhost";
	static final String PROXYPORT = "proxyport";
	static final String WORKERS = "WORKERS";
	static final String POSTDELAY = "POSTDELAY";

	static URL baseUri = null;
	static SensorThingsService service = null;
	static String sessionId;
	static Thing sessionThing = null;

	public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(BenchData.class);

	public static void initialize() {
		String baseUriStr = System.getenv(BenchData.BASE_URL).trim();
		sessionId = System.getenv(BenchData.SESSION).trim();
		
		try {
			LOGGER.debug("Creating SensorThingsService");
			baseUri = new URL(baseUriStr);
			service = new SensorThingsService(baseUri);
			LOGGER.debug("Creating SensorThingsService done");
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}
		LOGGER.trace("Initialized for: " + baseUriStr + " [SessionId = " + sessionId + "]");
	}

	public static Thing getBenchmarkThing() {
		// if sessionThing already found, just return it;
		if (sessionThing != null) {
			return sessionThing;
		}

		// check if service has been initialized
		if (service == null) {
			LOGGER.error("uninitialized service call");
			System.exit(1);
		}

		// find the Benchmark Thing to control the load generators
		Thing myThing = null;

		// search for the session thing
		EntityList<Thing> things;
		try {
			things = service.things().query().select("name", "id", "description").list();
			for (Thing thing : things) {
				LOGGER.trace(thing.toString());
				if (sessionId.equalsIgnoreCase(thing.getDescription())) { // found it
					myThing = service.things().find(thing.getId());
					break;
				}
			}
			if (myThing == null) {
				myThing = new Thing(BENCHMARK, sessionId);
				HashMap<String, Object> thingProperties = new HashMap<String, Object>();
				thingProperties.put("state", "stopped");
				thingProperties.put(SESSION, sessionId);
				myThing.setProperties(thingProperties);
				service.create(myThing);
			}
		} catch (ServiceFailureException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}

		sessionThing = myThing;
		return myThing;
	}

	/**
	 * Find a Datastream with given name @param within the Thing as defined in the initialized session context. 
	 * 
	 * @param name
	 * @return
	 */
	public static Datastream getDatastream(String name) {
		Datastream dataStream = null;
		LOGGER.debug("getSensor: " + name);

		try {
			dataStream = sessionThing.datastreams().query().filter("name eq '" + name + "'").first();

			if (dataStream == null) {
				dataStream = createDatastream(name);
			}
		} catch (ServiceFailureException e) {
			e.printStackTrace();
			System.exit(1);
		} catch (URISyntaxException e) {
			e.printStackTrace();
			System.exit(1);
		}

		return dataStream;
	}

	/**
	 * Creates a new Datastream with the given name along with associated Sensors and Locations 
	 * @param name
	 * @return
	 * @throws ServiceFailureException 
	 * @throws URISyntaxException 
	 */
	private static Datastream createDatastream(String name) throws ServiceFailureException, URISyntaxException {
		Datastream dataStream = null;
		
		Sensor sensor = new Sensor(name, "Sensor for creating benchmark data", "text", "Some metadata.");
		service.create(sensor);
		LOGGER.debug("Sensor new id " + String.valueOf(sensor.getId()));

		ObservedProperty obsProp1 = new ObservedProperty(name, new URI("http://ucom.org/temperature"), "random temperature");
		service.create(obsProp1);

		Location location = new Location(name, "Benchmark Random Location", "application/vnd.geo+json", new Point(8, 52));
		location.getThings().add(sessionThing);
		service.create(location);

		dataStream = new Datastream(name, "Benchmark Random Stream", name,
				new UnitOfMeasurement("observation rate", "observations per sec", "ucum:T"));
		dataStream.setThing(sessionThing);
		dataStream.setSensor(sensor);
		dataStream.setObservedProperty(obsProp1);
		service.create(dataStream);
		

		return dataStream;
	}
}
