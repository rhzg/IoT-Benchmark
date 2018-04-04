package frostBenchmark;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Properties;

import org.geojson.Point;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Datastream;
import de.fraunhofer.iosb.ilt.sta.model.Id;
import de.fraunhofer.iosb.ilt.sta.model.Location;
import de.fraunhofer.iosb.ilt.sta.model.Observation;
import de.fraunhofer.iosb.ilt.sta.model.ObservedProperty;
import de.fraunhofer.iosb.ilt.sta.model.Sensor;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
import de.fraunhofer.iosb.ilt.sta.model.ext.UnitOfMeasurement;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;


public class DataSource implements Runnable {
	private SensorThingsService service;
	private Sensor sensor;
	private ObservedProperty obsProp1;
	private Thing myThing;
	private Location location;
	private String myName;
	private int nbEntries = 0;
	private boolean running = false;
	private long POSTDELAY = 1000;
	
	static Properties dataSources = null;

	private Datastream datastream;

	public Thread myThread = null;

	public DataSource(SensorThingsService sensorThingsService) {
		service = sensorThingsService;
	}
	
	public static Id thingId = null;
	
	static void loadDataSourceProperties() {
		dataSources = new Properties();
        try {
        	dataSources.load(new FileInputStream("DataSources.properties"));

        }
        catch (final FileNotFoundException e) {
            Run.LOGGER.warn("No DataSource file found!");
            return;
        } catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}
	}
	
	static void saveDataSourceProperties () {
		try {
			dataSources.store(new FileOutputStream("DataSources.properties"), "Benchmark Data Source Identifiers");
			Run.LOGGER.info("DataSource identifiers have been changed. Properties file was updated");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			Run.LOGGER.error("Error while saving properties file. Exiting.");
//			System.exit(1);
		}
	}

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
		
		if (dataSources == null) loadDataSourceProperties();
		boolean propertiesToBeSaved = false;
		
		String sensorName = name+"-Sensor";
		Run.LOGGER.debug("DataSource - Sensor " + sensorName);
		if (dataSources.getProperty(sensorName) != null) {
			long id = Long.parseLong(dataSources.getProperty(sensorName));
			sensor = service.sensors().find(id);
			Run.LOGGER.debug("DataSource - Sensor known id " + id);
		} 
		if (sensor == null) {
			Run.LOGGER.debug("DataSource - Sensor not found");
			sensor = new Sensor(sensorName, "Random Data", "text", "Some metadata.");
			service.create(sensor);
			dataSources.setProperty(sensorName, String.valueOf(sensor.getId()));
			propertiesToBeSaved = true;
			Run.LOGGER.debug("DataSource - Sensor new id " + String.valueOf(sensor.getId()));
		}
		
		String observedPropertyName = name+"-ObservedProperty";
		if (dataSources.getProperty(observedPropertyName) != null) {
			long id = Long.parseLong(dataSources.getProperty(observedPropertyName));
			obsProp1 = service.observedProperties().find(id);
		} 
		if (obsProp1 == null) {
			obsProp1 = new ObservedProperty(observedPropertyName, new URI("http://ucom.org/temperature"), "random temperature");
			service.create(obsProp1);
			dataSources.setProperty(observedPropertyName, String.valueOf(obsProp1.getId()));
			propertiesToBeSaved = true;
		}

		myThing = Controller.getBenchmarkThing();
//		
//		String thingName = "Benchmark";
//		if (dataSources.getProperty(thingName) != null) {
//			long id = Long.parseLong(dataSources.getProperty(thingName));
//			myThing = service.things().find(id);
//		} 
//		if (myThing == null) {
//			myThing = new Thing(thingName, "Benchmark Random Thing");
//			HashMap<String,Object> thingProperties = new HashMap<String,Object>();
//			thingProperties.put ("state", "stopped");
//			
//			myThing.setProperties(thingProperties);
//			service.create(myThing);
//			dataSources.setProperty(thingName, String.valueOf(myThing.getId()));
//			propertiesToBeSaved = true;
//		}
		thingId = myThing.getId();
		
		String locationName = name+"-Location";
		if (dataSources.getProperty(locationName) != null) {
			long id = Long.parseLong(dataSources.getProperty(locationName));
			location = service.locations().find(id);
		} 
		if (location == null) {
			location = new Location(locationName, "Benchmark Random Location", "application/vnd.geo+json", new Point(8, 52));
			location.getThings().add(myThing);
			service.create(location);
			dataSources.setProperty(locationName, String.valueOf(location.getId()));
			propertiesToBeSaved = true;
		}
		
		String dataSourceName = name+"-DataStream";
		if (dataSources.getProperty(dataSourceName) != null) {
			long id = Long.parseLong(dataSources.getProperty(dataSourceName));
			datastream = service.datastreams().find(id);
		} 
		if (datastream == null) {
			datastream = new Datastream(dataSourceName, "Benchmark Random Stream", name,
					new UnitOfMeasurement("observation rate", "observation per sec", "ucum:T"));
			datastream.setThing(myThing);
			datastream.setSensor(sensor);
			datastream.setObservedProperty(obsProp1);
			service.create(datastream);
			dataSources.setProperty(dataSourceName, String.valueOf(datastream.getId()));
			propertiesToBeSaved = true;
		}
		
		if (propertiesToBeSaved) saveDataSourceProperties ();
		
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
