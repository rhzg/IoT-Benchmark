package de.fraunhofer.iosb.ilt.frostBenchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BenchProperties {

	public static final String TAG_SESSION = "SESSION";
	public static final String TAG_BASE_URL = "BASE_URL";
	public static final String TAG_PROXYHOST = "proxyhost";
	public static final String TAG_PROXYPORT = "proxyport";

	public static final String TAG_TIMEOUT = "timeout";
	public static final int DFLT_TIMEOUT = 10000;

	public static final String TAG_POSTDELAY = "POSTDELAY";
	public static final int DFLT_POSTDELAY = 1000;

	public static final String TAG_COVERAGE = "COVERAGE";
	public static final int DFLT_COVERAGE = 100;

	public static final String TAG_PERIOD = "PERIOD";
	public static final int DFLT_PERIOD = 500;

	public static final String TAG_SENSORS = "SENSORS";
	public static final int DFLT_SENSORS = 20;

	public static final String TAG_WORKERS = "WORKERS";
	public static final int DFLT_WORKERS = 10;

	public static final String TAG_STATUS = "status";

	public static enum STATUS {
		INITIALIZE,
		RUNNING,
		FINISHED,
		TERMINATE;
	}
	/**
	 * The logger for this class.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(BenchProperties.class);

	public int timeout = 10000;
	public int postdelay = 1000;
	public int coverage = 100;
	public int period = DFLT_PERIOD;
	public int sensors = DFLT_SENSORS;
	public int workers = DFLT_WORKERS;

	public BenchProperties readFromEnvironment() {
		workers = getEnv(TAG_WORKERS, DFLT_WORKERS);
		coverage = getEnv(TAG_COVERAGE, DFLT_COVERAGE);
		postdelay = getEnv(TAG_POSTDELAY, DFLT_POSTDELAY);
		period = getEnv(TAG_PERIOD, DFLT_PERIOD);
		sensors = getEnv(TAG_SENSORS, DFLT_SENSORS);
		return this;
	}

	public BenchProperties readFromJsonNode(JsonNode adds) {
		if (adds == null) {
			return this;
		}
		workers = getProperty(adds, TAG_WORKERS, workers);
		coverage = getProperty(adds, TAG_COVERAGE, coverage);
		postdelay = getProperty(adds, TAG_POSTDELAY, postdelay);
		period = getProperty(adds, TAG_PERIOD, period);
		sensors = getProperty(adds, TAG_SENSORS, sensors);
		return this;
	}

	@SuppressWarnings("unchecked")
	static public JsonNode mergeProperties(JsonNode base, JsonNode adds, boolean recursive) {
		JsonNode combinedProperties = base;
		for (Iterator<Map.Entry<String, JsonNode>> fields = adds.fields(); fields.hasNext();) {
			Map.Entry<String, JsonNode> entry = fields.next();
			String key = entry.getKey();
			JsonNode newValue = entry.getValue();
			JsonNode origValue = base.get(key);
			if (recursive && newValue.isObject() && origValue.isObject()) {
				mergeProperties(origValue, newValue, true);
			} else {
				((ObjectNode) combinedProperties).set(key, newValue);
			}
		}
		return combinedProperties;
	}

	static public String getProperty(JsonNode from, String name, String dflt) {
		JsonNode value = from.get(name);
		if (value == null || !value.isTextual()) {
			return dflt;
		}
		return value.asText();
	}

	static public int getProperty(JsonNode from, String name, int dflt) {
		JsonNode value = from.get(name);
		if (value == null || !value.isNumber()) {
			return dflt;
		}
		return value.intValue();
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
}
