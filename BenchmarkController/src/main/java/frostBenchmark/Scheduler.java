package frostBenchmark;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class Scheduler {

	JSONObject script = null;
	
	
	public void readSchedule (String scheduleFile) {
		FileReader fr;
		try {
			fr = new FileReader(scheduleFile);
			JSONParser parser = new JSONParser();			
			script = (JSONObject) parser.parse(fr);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void runScript() {
		if (script == null) return;
		
		JSONObject properties = (JSONObject) script.get("initialize");
		
		JSONArray sequence = (JSONArray) script.get("sequence");
		for (int i=0; i<sequence.size(); i++) {
			JSONObject run = (JSONObject) sequence.get(i);
			Long duration = (Long) run.get("duration");
			Long seqId= (Long) run.get("seq");
			System.out.println("run experiment " + seqId + " for " + duration + " msec");
			JSONObject runProperties = (JSONObject) run.get("properties");
			JSONObject combinedProperties = BenchProperties.mergeProperties(properties, runProperties);
			
			System.out.println(combinedProperties.toString());
			System.out.println("--- not implemented yes ---");
			System.out.println();
		}
	}
}
