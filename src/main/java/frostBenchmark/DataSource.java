package frostBenchmark;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

import org.geojson.Point;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Datastream;
import de.fraunhofer.iosb.ilt.sta.model.IdLong;
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
	private static Properties dataSources = null;

	private Datastream datastream;

	public Thread myThread = null;

	public DataSource(SensorThingsService sensorThingsService) {
		service = sensorThingsService;
	}

	private void loadDataSourceProperties() {
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
		}
	}
	
	private void saveDataSourceProperties () {
		try {
			dataSources.store(new FileOutputStream("DataSources.properties"), "Benchmark Data Source Identifiers");
			Run.LOGGER.info("DataSource identifiers have been changed. Properties file was updated");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
		
		if (dataSources.getProperty(name+"-Sensor") != null) {
			long id = Long.parseLong(dataSources.getProperty(name+"-Sensor"));
			sensor = service.sensors().find(id);
		} 
		if (sensor == null) {
			sensor = new Sensor(name, "Random Data", "text", "Some metadata.");
			service.create(sensor);
			dataSources.setProperty(name+"-Sensor", String.valueOf(sensor.getId()));
			propertiesToBeSaved = true;
		}
		
		if (dataSources.getProperty(name+"-ObservedProperty") != null) {
			long id = Long.parseLong(dataSources.getProperty(name+"-ObservedProperty"));
			obsProp1 = service.observedProperties().find(id);
		} 
		if (obsProp1 == null) {
			obsProp1 = new ObservedProperty(name, new URI("http://ucom.org/temperature"), "random temperature");
			service.create(obsProp1);
			dataSources.setProperty(name+"-ObservedProperty", String.valueOf(obsProp1.getId()));
			propertiesToBeSaved = true;
		}

		if (dataSources.getProperty(name+"-Thing") != null) {
			long id = Long.parseLong(dataSources.getProperty(name+"-Thing"));
			myThing = service.things().find(id);
		} 
		if (myThing == null) {
			myThing = new Thing(name, "Benchmark Random Thing");
			service.create(myThing);
			dataSources.setProperty(name+"-Thing", String.valueOf(myThing.getId()));
			propertiesToBeSaved = true;
		}
		
		if (dataSources.getProperty(name+"-Location") != null) {
			long id = Long.parseLong(dataSources.getProperty(name+"-Location"));
			location = service.locations().find(id);
		} 
		if (location == null) {
			location = new Location(name, "Benchmark Random Location", "application/vnd.geo+json", new Point(8, 52));
			location.getThings().add(myThing);
			service.create(location);
			dataSources.setProperty(name+"-Location", String.valueOf(location.getId()));
			propertiesToBeSaved = true;
		}
		
		if (dataSources.getProperty(name+"-DataStream") != null) {
			long id = Long.parseLong(dataSources.getProperty(name+"-DataStream"));
			datastream = service.datastreams().find(id);
		} 
		if (datastream == null) {
			datastream = new Datastream(name, "Benchmark Random Stream", name,
					new UnitOfMeasurement("degree celsius", "°C", "ucum:T"));
			datastream.setThing(myThing);
			datastream.setSensor(sensor);
			datastream.setObservedProperty(obsProp1);
			service.create(datastream);
			dataSources.setProperty(name+"-DataStream", String.valueOf(datastream.getId()));
			propertiesToBeSaved = true;
		}
		
		if (propertiesToBeSaved) saveDataSourceProperties ();
		
		return this;
	}

	public void run() {
		Observation o = null;
		double c;
		running = true;
		while (running) {
			c = nbEntries;
			o = new Observation(c, datastream);
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
		(myThread = new Thread(this)).start();
		return this;
	}

}
