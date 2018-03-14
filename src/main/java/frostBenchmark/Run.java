package frostBenchmark;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Properties;

import org.slf4j.LoggerFactory;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;

public class Run {

	private static final String BASE_URL = "BASE_URL";
	private static final String PROXYHOST = "proxyhost";
	private static final String PROXYPORT = "proxyport";
	private static final String DURATION = "DURATION";
	private static final String WORKERS = "WORKERS";
	private static final String POSTDELAY = "POSTDELAY";

	static SensorThingsService service;
	public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Run.class);
	public static Properties props;

	private static DataSource[] dsList;
	private static long startTime = 1000;
	private static long stopTime = 1000;
	private static int lapTime = 1000;
	private static int workers = 1;
	private static long postDelay = 0;

	/**
	 * load properties and initialize SensorThingsSerice endpoint
	 * 
	 * @return
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	static void initializeSerice() throws IOException, URISyntaxException {
		props = new Properties();
		try {
			props.load(new FileInputStream("config.properties"));
			lapTime = Integer.parseInt(props.getProperty(DURATION));
			workers = Integer.parseInt(props.getProperty(WORKERS));
			postDelay = Long.parseLong(props.getProperty(POSTDELAY));

		} catch (final FileNotFoundException e) {
			LOGGER.warn("No properties file found!");

			props.setProperty(BASE_URL, "http://localhost:8080/FROST-Server.HTTP-1.6-SNAPSHOT/v1.0/");
			props.setProperty(PROXYHOST, "proxy-ka.iosb.fraunhofer.de");
			props.setProperty(PROXYPORT, "80");
			props.setProperty(DURATION, "10000");
			props.setProperty(WORKERS, "1");
			props.setProperty(POSTDELAY, "10");

			props.store(new FileOutputStream("config.properties"), "FROST Benchmark Properties");
			LOGGER.warn("New file has been created with default values. Please check your correct settings");
			LOGGER.warn(props.toString());
		}

		final URL baseUri = new URL(props.getProperty(BASE_URL));
		service = new SensorThingsService(baseUri);
	}

	static void initWorkLoad() throws ServiceFailureException, URISyntaxException {
		LOGGER.trace("Benchmark initializing, starting workers");
		dsList = new DataSource[workers];
		for (int i = 0; i < workers; i++) {
			dsList[i] = new DataSource(service).intialize("Benchmark" + i);
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

		initializeSerice();

		initWorkLoad();

		startWorkLoad();
		

		LOGGER.info("Benchmark running for " + lapTime + " msec");
		Thread.sleep(lapTime);

		stopWorkLoad();
		
	}

}
