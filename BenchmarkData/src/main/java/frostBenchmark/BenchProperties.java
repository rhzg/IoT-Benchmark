package frostBenchmark;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.simple.JSONObject;

public class BenchProperties {
	static final String BENCHMARK = "Benchmark";
	static final String SESSION = "SESSION";
	static final String BASE_URL = "BASE_URL";
	static final String PROXYHOST = "proxyhost";
	static final String PROXYPORT = "proxyport";

	static final String TIMEOUT = "timeout";
	static final String TIMEOUTdefault = "10000";
	static       int    timeout = 10000;

	static final String POSTDELAY = "POSTDELAY";
	static final String POSTDELAYdefault = "1000";
	static       int    postdelay = 1000;

	static final String COVERAGE = "COVERAGE";
	static final String COVERAGEdefault = "100";
	static       int    coverage = 100;

	static final String PERIOD = "PERIOD";
	static final String PERIODdefault = "10";
	static       int    period = 100;

	static final String WORKERS = "WORKERS";
	static final String WORKERSdefault = "1";
	static       int    workers = 1;

	static final String STATUS = "status";
	static final String RUNNING = "running";
	static final String FINISHED = "finished";
	static final String TERMINATE = "terminate";

	static private Map<String, Object> properties = null;

	public static Map<String, Object> getProperties() {
		return properties;
	}
	
	public static void setProperties(Map<String, Object> properties) {
		BenchProperties.properties = properties;
	}

	static public void intialize() {
		if (properties == null) {
			properties = new HashMap<String, Object>();
			
			String workersStr = System.getenv(WORKERS);
			if (workersStr == null)
				workersStr = WORKERSdefault;
			properties.put(WORKERS, workersStr);
			workers = Integer.parseInt(workersStr);

			String coverageStr = System.getenv(COVERAGE);
			if (coverageStr == null)
				coverageStr = COVERAGEdefault;
			properties.put(COVERAGE, coverageStr);
			coverage = Integer.parseInt(coverageStr);

			String rateStr = System.getenv(PERIOD);
			if (rateStr == null)
				rateStr = PERIODdefault;
			properties.put(PERIOD, rateStr);
			period = Integer.parseInt(rateStr);

			String postdelayStr = System.getenv(POSTDELAY);
			if (postdelayStr == null)
				postdelayStr = POSTDELAYdefault;
			properties.put(POSTDELAY, postdelayStr);
			postdelay = Integer.parseInt(postdelayStr);

			String timeoutStr = System.getenv(TIMEOUT);
			if (timeoutStr == null)
				timeoutStr = TIMEOUTdefault;
			properties.put(TIMEOUT, timeoutStr);
			timeout = Integer.parseInt(timeoutStr);

			properties.put(STATUS, FINISHED);
		}

	}

	@SuppressWarnings("unchecked")
	static public JSONObject mergeProperties(JSONObject base, JSONObject adds) {
		JSONObject combinedProperties = base;
		for (@SuppressWarnings("rawtypes")
		Iterator p = adds.keySet().iterator(); p.hasNext();) {
			String key = (String) p.next();
			combinedProperties.put(key, adds.get(key));
		}
		return combinedProperties;
	}
}
