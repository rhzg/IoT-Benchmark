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

	private static SensorThingsService service;
	public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Run.class);
	public static Properties props;

	/**
	 * load properties and initialize SensorThingsSerice endpoint
	 * 
	 * @return
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	private static SensorThingsService loadProperties() throws IOException, URISyntaxException {
		props = new Properties();
		try {
			props.load(new FileInputStream("config.properties"));

		} catch (final FileNotFoundException e) {
			LOGGER.warn("No properties file found!");

			props.setProperty(BASE_URL, "http://localhost:8080/FROST-Server.HTTP-1.6-SNAPSHOT/v1.0/");
			props.setProperty(PROXYHOST, "proxy-ka.iosb.fraunhofer.de");
			props.setProperty(PROXYPORT, "80");
			props.setProperty(DURATION, "10000");

			props.store(new FileOutputStream("config.properties"), "FROST Benchmark Properties");
			LOGGER.warn("New file has been created with default values. Please check your correct settings");
			LOGGER.warn(props.toString());
		}

		final URL baseUri = new URL(props.getProperty(BASE_URL));
		return new SensorThingsService(baseUri);
	}

	public static void main(String[] args) throws IOException, URISyntaxException, ServiceFailureException, InterruptedException {
		
		LOGGER.info("Benchmark initializing");

		service = loadProperties();

		int lapTime = Integer.parseInt(props.getProperty(DURATION));
		int workers = Integer.parseInt(props.getProperty(WORKERS));
		long postDelay = Long.parseLong(props.getProperty(POSTDELAY));

		DataSource[] dsList = new DataSource[workers];
		for (int i=0; i<workers; i++) {
			dsList[i] = new DataSource (service).intialize("Benchmark"+i);		
		}

		LOGGER.info("Benchmark initialized, starting workers  --------");
		for (int i=0; i<workers; i++) {
			dsList[i].startUp(postDelay);		
		}
		
		LOGGER.info("Benchmark starting for " + lapTime + " msec ---------------");

		Thread.sleep(lapTime);
		
		int entries = 0;
		for (int i=0; i<workers; i++) {
			entries += dsList[i].endUp();
		}

		
		
		LOGGER.info("Benchmark finished ------------------------------");
		LOGGER.info(1000*entries/lapTime + " entries created per sec");
		LOGGER.info("Benchmark finished ------------------------------");
	}

}
