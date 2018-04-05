package frostBenchmark;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Properties;

import org.slf4j.LoggerFactory;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;

public class Run {

	static final String BASE_URL = "BASE_URL";
	static final String BROKER = "BROKER";
	static final String PROXYHOST = "proxyhost";
	static final String PROXYPORT = "proxyport";
	static final String DURATION = "DURATION";
	static final String WORKERS = "WORKERS";
	static final String POSTDELAY = "POSTDELAY";

	static URL baseUri;
	static SensorThingsService service;
	public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Run.class);
	public static Properties props;

	private static DataSource[] dsList;
	private static long startTime = 1000;
	private static long stopTime = 1000;
	private static int lapTime = 1000;
	private static int workers = 1;
	private static long postDelay = 0;


	static void initWorkLoad() throws ServiceFailureException, URISyntaxException {
		LOGGER.trace("Benchmark initializing, starting workers");
		dsList = new DataSource[workers];
		for (int i = 0; i < workers; i++) {
			dsList[i] = new DataSource(BenchData.service).intialize("Benchmark." + i);
		}
		LOGGER.info("Benchmark initialized");
	}

	static void startWorkLoad() {
		LOGGER.trace("Benchmark start workload");
		for (int i = 0; i < workers; i++) {
			dsList[i].startUp(postDelay);
		}
		startTime = System.currentTimeMillis();
		LOGGER.info("Benchmark workload started");
	}

	static void stopWorkLoad() {
		LOGGER.trace("Benchmark finishing");
		stopTime = System.currentTimeMillis();
		int entries = 0;
		for (int i = 0; i < workers; i++) {
			entries += dsList[i].endUp();
		}

		LOGGER.info(1000 * entries / (stopTime - startTime) + " entries created per sec");
		LOGGER.info("Benchmark finished");

	}

	public static void main(String[] args)
			throws IOException, URISyntaxException, ServiceFailureException, InterruptedException {

		BenchData.initialize(System.getenv(BenchData.BASE_URL), System.getenv(BenchData.SESSION));

		initWorkLoad();

		startWorkLoad();
		

		LOGGER.info("Benchmark running for " + lapTime + " msec");
		Thread.sleep(lapTime);

		stopWorkLoad();
		
	}

}
