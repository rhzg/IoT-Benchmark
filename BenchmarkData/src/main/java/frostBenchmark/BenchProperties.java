package frostBenchmark;

import org.json.JSONObject;

public class BenchProperties {
	static final String BENCHMARK = "Benchmark";
	static final String SESSION = "SESSION";
	static final String BASE_URL = "BASE_URL";
	static final String BROKER = "BROKER";
	static final String PROXYHOST = "proxyhost";
	static final String PROXYPORT = "proxyport";
	static final String WORKERS = "WORKERS";
	static final String POSTDELAY = "POSTDELAY";

	static private JSONObject properties = null;

	static public void intialize() {
		if (properties == null) {
			String workers = System.getenv(WORKERS);
			if (workers != null)
				properties.append(WORKERS, workers);
		}

	}
}
