package frostBenchmark;

import java.net.URISyntaxException;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Datastream;
import de.fraunhofer.iosb.ilt.sta.model.Id;
import de.fraunhofer.iosb.ilt.sta.model.Observation;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;


public class DataSource implements Runnable {
	private SensorThingsService service;
	private String myName;
	private int nbEntries = 0;
	private boolean running = false;
	private long POSTDELAY = 1000;
	
	private Datastream datastream;

	public Thread myThread = null;

	public DataSource(SensorThingsService sensorThingsService) {
		service = sensorThingsService;
	}
	
	public static Id thingId = null;
	

	/**
	 * find or create the datastream for given name
	 * 
	 * @param name
	 * @return
	 * @throws ServiceFailureException
	 * @throws URISyntaxException
	 */
	public DataSource intialize(String name) throws ServiceFailureException, URISyntaxException {
		myName = name;
		datastream = BenchData.getDatastream(name);
		return this;
	}

	public void run() {
		long startTime = System.currentTimeMillis();
		Observation o = null;
		double observateRate;
		running = true;
		while (running) {
			long currentTime = System.currentTimeMillis();
			observateRate = (double) nbEntries * 1000.0 / ((currentTime > startTime) ? currentTime - startTime : 1);
			o = new Observation(observateRate, datastream);
			nbEntries++;
			try {
				service.create(o);
			} catch (ServiceFailureException e1) {
				e1.printStackTrace();
			}
			try {
				Thread.sleep(POSTDELAY);
			} catch (InterruptedException e) {
				running = false;
				e.printStackTrace();
			}
		}

	}

	public int endUp() {
		running = false;
		Run.LOGGER.debug(myName + " created " + nbEntries + " entries");
		return nbEntries;
	}

	public DataSource startUp(long delay) {
		POSTDELAY = delay;
		nbEntries = 0;
		(myThread = new Thread(this)).start();
		return this;
	}

}
