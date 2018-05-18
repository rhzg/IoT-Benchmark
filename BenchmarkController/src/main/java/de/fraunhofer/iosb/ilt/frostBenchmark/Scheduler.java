package de.fraunhofer.iosb.ilt.frostBenchmark;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Thing;

public class Scheduler {

	JSONObject script = null;
	Thing sessionThing = null;

	public void readSchedule(String scheduleFile) {
		FileReader fr;
		try {
			fr = new FileReader(scheduleFile);
			JSONParser parser = new JSONParser();
			script = (JSONObject) parser.parse(fr);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void runScript() throws ServiceFailureException, InterruptedException {
		if (script == null)
			return;

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
		if (sessionThing == null)
			sessionThing = BenchData.getBenchmarkThing();
		BenchProperties.setProperty("state", BenchProperties.STATUS.RUNNING);
		sessionThing.setProperties(BenchProperties.getProperties());
		BenchData.service.update(sessionThing);

	}

	public void stopExperiment() throws ServiceFailureException {
		if (sessionThing == null)
			sessionThing = BenchData.getBenchmarkThing();
		BenchProperties.setProperty("state", BenchProperties.STATUS.FINISHED);
		sessionThing.setProperties(BenchProperties.getProperties());
		BenchData.service.update(sessionThing);

	}

	public void terminateSession() throws ServiceFailureException {
		if (sessionThing == null)
			sessionThing = BenchData.getBenchmarkThing();
		BenchProperties.setProperty("state", BenchProperties.STATUS.TERMINATE);
		sessionThing.setProperties(BenchProperties.getProperties());
		BenchData.service.update(sessionThing);

	}
}
