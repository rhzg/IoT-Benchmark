package de.fraunhofer.iosb.ilt.frostBenchmark;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
import java.io.FileReader;
import java.io.IOException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Scheduler {

	/**
	 * The logger for this class.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(Scheduler.class);
	JSONObject script = null;
	Thing sessionThing = null;

	public void readSchedule(String scheduleFile) {
		FileReader fr;
		try {
			fr = new FileReader(scheduleFile);
			JSONParser parser = new JSONParser();
			script = (JSONObject) parser.parse(fr);
		} catch (ParseException | IOException e) {
			LOGGER.error("Failed to load json.", e);
		}
	}

	public void runScript() throws ServiceFailureException, InterruptedException {
		if (script == null) {
			return;
		}

		JSONObject properties = (JSONObject) script.get("initialize");
		BenchProperties.addProperties(properties);

		JSONArray sequence = (JSONArray) script.get("sequence");
		for (int i = 0; i < sequence.size(); i++) {
			JSONObject run = (JSONObject) sequence.get(i);
			Long duration = (Long) run.get("duration");
			Long seqId = (Long) run.get("seq");
			System.out.println("run experiment " + seqId + " for " + duration + " msec");
			JSONObject runProperties = (JSONObject) run.get("properties");
			BenchProperties.addProperties(runProperties);

			startExperiment();
			Thread.sleep(duration);
			stopExperiment();
		}
		System.out.println("finished");
	}

	public void startExperiment() throws ServiceFailureException {
		if (sessionThing == null) {
			sessionThing = BenchData.getBenchmarkThing();
		}
		BenchProperties.setProperty("state", BenchProperties.STATUS.RUNNING);
		sessionThing.setProperties(BenchProperties.getProperties());
		BenchData.service.update(sessionThing);

	}

	public void stopExperiment() throws ServiceFailureException {
		if (sessionThing == null) {
			sessionThing = BenchData.getBenchmarkThing();
		}
		BenchProperties.setProperty("state", BenchProperties.STATUS.FINISHED);
		sessionThing.setProperties(BenchProperties.getProperties());
		BenchData.service.update(sessionThing);

	}

	public void terminateSession() throws ServiceFailureException {
		if (sessionThing == null) {
			sessionThing = BenchData.getBenchmarkThing();
		}
		BenchProperties.setProperty("state", BenchProperties.STATUS.TERMINATE);
		sessionThing.setProperties(BenchProperties.getProperties());
		BenchData.service.update(sessionThing);

	}
}
