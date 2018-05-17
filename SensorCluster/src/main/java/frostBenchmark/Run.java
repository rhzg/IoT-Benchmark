package frostBenchmark;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.LoggerFactory;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Datastream;
import de.fraunhofer.iosb.ilt.sta.model.Observation;

import static java.util.concurrent.TimeUnit.*;

public class Run {


	static URL baseUri;
	public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Run.class);

	private static DataSource[] dsList;
	private static long startTime = 0;
	private static long stopTime = 0;
	private static int lapTime = 1000;


	static void initWorkLoad() throws ServiceFailureException, URISyntaxException {
		LOGGER.trace("Benchmark initializing, starting workers");
		BenchData.initialize();
		dsList = new DataSource[BenchProperties.workers];
		for (int i = 0; i < BenchProperties.workers; i++) {
			dsList[i] = new DataSource(BenchData.service).intialize("Benchmark." + i);
		}
		LOGGER.trace("Benchmark initialized");
	}

	
private final static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1); 

	static void startWorkLoad() throws ServiceFailureException, URISyntaxException {
		DataSource ds = new DataSource(BenchData.service).intialize("BenchmarkWorker");
		startTime = System.currentTimeMillis();
		scheduler.scheduleAtFixedRate(ds, 0, BenchProperties.period, MILLISECONDS);
		
//		LOGGER.trace("Benchmark start workload");
//		for (int i = 0; i < BenchProperties.workers; i++) {
//			dsList[i].startUp(BenchProperties.postdelay);
//		}
//		startTime = System.currentTimeMillis();
//		LOGGER.trace("Benchmark workload started");
	}

	static void stopWorkLoad() {
		scheduler.shutdown();
		
		
		LOGGER.trace("Benchmark finishing");
		stopTime = System.currentTimeMillis();
		int entries = 0;
		for (int i = 0; i < BenchProperties.workers; i++) {
			entries += dsList[i].endUp();
		}

		Datastream ds = BenchData.getDatastream("SensorCluster");
		double rate = 1000.0 * entries / (stopTime - startTime);
		try {
			BenchData.service.create(new Observation(rate, ds));
		} catch (ServiceFailureException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		LOGGER.info(1000 * entries / (stopTime - startTime) + " entries created per sec");
		LOGGER.info("Benchmark finished");

	}
	
	public static void main(String[] args)
			throws IOException, URISyntaxException, ServiceFailureException, InterruptedException {

		BenchData.initialize();

		initWorkLoad();

		startWorkLoad();
		

		LOGGER.info("Benchmark running for " + lapTime + " msec");
		Thread.sleep(lapTime);

		stopWorkLoad();
		
	}

}
