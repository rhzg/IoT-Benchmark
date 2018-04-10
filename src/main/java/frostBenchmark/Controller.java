package frostBenchmark;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.dao.BaseDao;
import de.fraunhofer.iosb.ilt.sta.model.Entity;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
import de.fraunhofer.iosb.ilt.sta.model.ext.EntityList;
import de.fraunhofer.iosb.ilt.sta.service.SensorThingsService;

public class Controller {

	static final String BENCHMARK = "Benchmark";
	static final String SESSION = "session";

	public static void main(String[] args)
			throws IOException, URISyntaxException, ServiceFailureException, InterruptedException {
		String cmdInfo = "Available command are <run [msec]>, <stop>, <terminate>, <help>, <delete>, <quit>";

		BenchData.initialize();
		
		Thing myThing = BenchData.getBenchmarkThing();

		Map<String, Object> properties = new HashMap<String, Object>();

		System.out.println(cmdInfo);
		boolean running = true;
		Scanner sc = new Scanner(System.in);
		while (running) {
			System.out.println("Benchmark > ");

			String[] cmd = sc.nextLine().split(" ");
			if (cmd[0].equalsIgnoreCase("run")) {
				properties.put("state", MqttHelper.RUNNING);
				myThing.setProperties(properties);
				BenchData.service.update(myThing);

				if (cmd.length > 1) {
					int ms = Integer.parseInt(cmd[1]);
					System.out.println("running for " + ms + " msec");
					Thread.sleep(ms);
					properties.put("state", MqttHelper.FINISHED);
					myThing.setProperties(properties);
					BenchData.service.update(myThing);
				}

			} else if (cmd[0].equalsIgnoreCase("stop")) {
				properties.put("state", MqttHelper.FINISHED);
				myThing.setProperties(properties);
				BenchData.service.update(myThing);
			} else if (cmd[0].equalsIgnoreCase("delete")) {
				System.out.println("All data in " + BenchData.baseUri.toString() + " will be deleted. After that you need to restart");
				System.out.println("Are you sure you want to do this? Type 'yes'");
				String answer = sc.nextLine();
				if (answer.equalsIgnoreCase("YES")) {
					System.out.println("ok, let's do it");
					deleteAll(BenchData.service);
					System.out.println("finished - you need to restart");
					System.exit(0);
				} else {
					System.out.println("fine, we keep the data");					
				}
			
				System.out.println(cmdInfo);
			} else if (cmd[0].equalsIgnoreCase(MqttHelper.TERMINATE) || cmd[0].equalsIgnoreCase("t")) {
				properties.put("state", MqttHelper.TERMINATE);
				myThing.setProperties(properties);
				BenchData.service.update(myThing);
				System.out.println("Terminate message sent");
			} else if (cmd[0].equalsIgnoreCase("help") || cmd[0].equalsIgnoreCase("h")) {
				System.out.println("Base URL   : " + BenchData.baseUri.toString());
				System.out.println("Session Id : " + BenchData.sessionId);
				System.out.println("<run [msec]> : Start all benchmark process with optional parameter time im msec");
				System.out.println("<stop>       : Stop all running processes");
				System.out.println("<terminate>  : Terminte all running benchmark processes");
				System.out.println("<delete>     : Deletes all data in base url - THING TWICE BEFORE USING THIS!!!");
				System.out.println("<help>       : print this help info");
				System.out.println("<quit>       : Quit this Controller terminal");
			} else if (cmd[0].equalsIgnoreCase("quit") || cmd[0].equalsIgnoreCase("q")) {
				running = false;
				System.out.println("Bye");
			}
		}
		sc.close();
	}

    public static void deleteAll(SensorThingsService sts) throws ServiceFailureException {
        deleteAll(sts.things());
        deleteAll(sts.locations());
        deleteAll(sts.sensors());
        deleteAll(sts.featuresOfInterest());
        deleteAll(sts.observedProperties());
        deleteAll(sts.observations());
    }
    public static <T extends Entity<T>> void deleteAll(BaseDao<T> doa) throws ServiceFailureException {
        boolean more = true;
        int count = 0;
        while (more) {
            EntityList<T> entities = doa.query().count().list();
            if (entities.getCount() > 0) {
                BenchData.LOGGER.info("{} to go.", entities.getCount());
            } else {
                more = false;
            }
            for (T entity : entities) {
                doa.delete(entity);
                count++;
            }
        }
        BenchData.LOGGER.info("Deleted {} using {}.", count, doa.getClass().getName());
    }
	
}
