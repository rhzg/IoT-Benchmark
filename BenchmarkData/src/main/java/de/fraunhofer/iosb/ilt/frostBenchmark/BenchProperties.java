package de.fraunhofer.iosb.ilt.frostBenchmark;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BenchProperties {

	public static final String TAG_BENCHMARK = "Benchmark";
	public static final String TAG_SESSION = "SESSION";
	public static final String TAG_BASE_URL = "BASE_URL";
	public static final String TAG_PROXYHOST = "proxyhost";
	public static final String TAG_PROXYPORT = "proxyport";

	public static final String TAG_TIMEOUT = "timeout";
	public static final int DFLT_TIMEOUT = 10000;
	public static int timeout = 10000;

	public static final String TAG_POSTDELAY = "POSTDELAY";
	public static final int DFLT_POSTDELAY = 1000;
	public static int postdelay = 1000;

	public static final String TAG_COVERAGE = "COVERAGE";
	public static final int DFLT_COVERAGE = 100;
	public static int coverage = 100;

	public static final String TAG_PERIOD = "PERIOD";
	public static final int DFLT_PERIOD = 500;
	public static int period = DFLT_PERIOD;

	public static final String TAG_SENSORS = "SENSORS";
	public static final int DFLT_SENSORS = 20;
	public static int sensors = DFLT_SENSORS;

	public static final String TAG_WORKERS = "WORKERS";
	public static final int DFLT_WORKERS = 10;
	public static int workers = DFLT_PERIOD;

	public static final String TAG_STATUS = "state";

	public static enum STATUS {
		RUNNING,
		FINISHED,
		TERMINATE;
	}
	/**
	 * The logger for this class.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(BenchProperties.class);
	static private Map<String, Object> properties = null;

	public static Map<String, Object> getProperties() {
		return properties;
	}

	public static void setProperties(Map<String, Object> properties) {
		BenchProperties.properties = properties;
	}

	public static void setProperty(String key, Object value) {
		BenchProperties.properties.put(key, value);
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

	static public void intialize() {
		if (properties == null) {
			properties = new HashMap<>();

			workers = getEnv(TAG_WORKERS, DFLT_WORKERS);
			properties.put(TAG_WORKERS, workers);

			coverage = getEnv(TAG_COVERAGE, DFLT_COVERAGE);
			properties.put(TAG_COVERAGE, coverage);

			postdelay = getEnv(TAG_POSTDELAY, DFLT_POSTDELAY);
			properties.put(TAG_COVERAGE, postdelay);

			period = getEnv(TAG_PERIOD, DFLT_PERIOD);
			properties.put(TAG_PERIOD, period);

			sensors = getEnv(TAG_SENSORS, DFLT_SENSORS);
			properties.put(TAG_SENSORS, sensors);

			properties.put(TAG_STATUS, STATUS.FINISHED);

		}

	}

	@SuppressWarnings("unchecked")
	static public JSONObject mergeProperties(JSONObject base, JSONObject adds) {
		JSONObject combinedProperties = base;
		for (@SuppressWarnings("rawtypes") Iterator p = adds.keySet().iterator(); p.hasNext();) {
			String key = (String) p.next();
			combinedProperties.put(key, adds.get(key));
		}
		return combinedProperties;
	}

	static public void addProperties(JSONObject adds) {
		for (@SuppressWarnings("rawtypes") Iterator p = adds.keySet().iterator(); p.hasNext();) {
			String key = (String) p.next();
			properties.put(key, adds.get(key));
		}
	}

	static public String getProperty(JSONObject from, String name, String dflt) {
		Object value = from.get(name);
		if (value == null) {
			return dflt;
		}
		return value.toString();
	}

	static public int getProperty(JSONObject from, String name, int dflt) {
		Object value = from.get(name);
		if (value == null) {
			return dflt;
		}
		if (value instanceof Number) {
			return ((Number) value).intValue();
		}
		return dflt;
	}
}
