package frostBenchmark;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Thing;

public class Controller {

	static final String RUNNING = "running";
	static final String FINISHED = "finished";
	static final String BENCHMARK = "Benchmark";
	static final String SESSION = "session";

	public static void main(String[] args)
			throws IOException, URISyntaxException, ServiceFailureException, InterruptedException {
		String helpMsg = "Available command are <run [msec]>, <stop>, <help>, <quit>";

		BenchData.initialize(System.getenv(BenchData.BASE_URL), System.getenv(BenchData.SESSION));
		
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

			}
			if (cmd[0].equalsIgnoreCase("stop")) {
				properties.put("state", FINISHED);
				myThing.setProperties(properties);
				BenchData.service.update(myThing);
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

	
}
