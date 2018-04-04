package frostBenchmark;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
import de.fraunhofer.iosb.ilt.sta.model.ext.EntityList;

public class Controller {

	static final String RUNNING = "running";
	static final String FINISHED = "finished";
	static final String BENCHMARK = "Benchmark";
	static final String SESSION = "session";

	public static void main(String[] args)
			throws IOException, URISyntaxException, ServiceFailureException, InterruptedException {
		String helpMsg = "Available command are <run [msec]>, <stop>, <help>, <quit>";

		Run.initializeSerice();

		DataSource.loadDataSourceProperties();

		Thing myThing = getBenchmarkThing();

		Map<String, Object> properties = new HashMap<String, Object>();

		System.out.println(helpMsg);
		boolean running = true;
		Scanner sc = new Scanner(System.in);
		while (running) {
			System.out.println("Benchmark > ");

			String[] cmd = sc.nextLine().split(" ");
			if (cmd[0].equalsIgnoreCase("run")) {
				properties.put("state", RUNNING);
				myThing.setProperties(properties);
				Run.service.update(myThing);

				if (cmd.length > 1) {
					int ms = Integer.parseInt(cmd[1]);
					System.out.println("running for " + ms + " msec");
					Thread.sleep(ms);
					properties.put("state", FINISHED);
					myThing.setProperties(properties);
					Run.service.update(myThing);
				}

			}
			if (cmd[0].equalsIgnoreCase("stop")) {
				properties.put("state", FINISHED);
				myThing.setProperties(properties);
				Run.service.update(myThing);
			}
			if (cmd[0].equalsIgnoreCase("help")) {
				System.out.println(helpMsg);
			}
			if (cmd[0].equalsIgnoreCase("quit")) {
				running = false;
				System.out.println("Bye");
			}
		}
		sc.close();
	}

	
	public static Thing getBenchmarkThing() throws ServiceFailureException {
		// find the Benchmark Thing to control the load generators
		Thing myThing = null;
		String sessionId = System.getenv(SESSION);

		// search for the session thing
		EntityList<Thing> things = Run.service.things().query().select("name", "id", "description").list();
		for (Thing thing : things) {
			Run.LOGGER.trace(thing.toString());
			if (sessionId.equalsIgnoreCase(thing.getDescription())) { // found it
				myThing = Run.service.things().find(thing.getId());
				break;
			}
		}
		
//		if (DataSource.dataSources.getProperty(thingName) != null) {
//			long id = Long.parseLong(DataSource.dataSources.getProperty(thingName));
//			myThing = Run.service.things().find(id);
//		}
		if (myThing == null) {
			myThing = new Thing(BENCHMARK, sessionId);
			HashMap<String, Object> thingProperties = new HashMap<String, Object>();
			thingProperties.put("state", "stopped");
			thingProperties.put(SESSION, sessionId);
			myThing.setProperties(thingProperties);
			Run.service.create(myThing);
			// update properties file
			DataSource.dataSources.setProperty(BENCHMARK, String.valueOf(myThing.getId()));
			DataSource.saveDataSourceProperties();
		}
		return myThing;
	}

}
