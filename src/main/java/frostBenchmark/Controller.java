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

	static final String RUNNING = "running";
	static final String FINISHED = "finished";
	static final String BENCHMARK = "Benchmark";
	static final String SESSION = "session";

	public static void main(String[] args)
			throws IOException, URISyntaxException, ServiceFailureException, InterruptedException {
		String helpMsg = "Available command are <run [msec]>, <stop>, <help>, <delete>, <quit>";

		String baseUrl = System.getenv(BenchData.BASE_URL);
		String session = System.getenv(BenchData.SESSION);
		BenchData.initialize(baseUrl, session);
		
		Thing myThing = BenchData.getBenchmarkThing();

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
				BenchData.service.update(myThing);

				if (cmd.length > 1) {
					int ms = Integer.parseInt(cmd[1]);
					System.out.println("running for " + ms + " msec");
					Thread.sleep(ms);
					properties.put("state", FINISHED);
					myThing.setProperties(properties);
					BenchData.service.update(myThing);
				}

			} else if (cmd[0].equalsIgnoreCase("stop")) {
				properties.put("state", FINISHED);
				myThing.setProperties(properties);
				BenchData.service.update(myThing);
			} else if (cmd[0].equalsIgnoreCase("delete")) {
				System.out.println("All data in " + baseUrl + " will be deleted. After that you need to restart");
				System.out.println("Are you sure you want to do this? Type 'yes'");
				String answer = sc.nextLine();
				if (answer.equalsIgnoreCase("YES")) {
					System.out.println("ok, let's do it");
					deleteAll(BenchData.service);
					System.exit(0);
				} else {
					System.out.println("fine, we keep the data");					
				}
			
				System.out.println(helpMsg);
			} else if (cmd[0].equalsIgnoreCase("help") || cmd[0].equalsIgnoreCase("h")) {
				System.out.println("Base URL   : " + baseUrl);
				System.out.println("Session Id : " + session);
				System.out.println(helpMsg);
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
