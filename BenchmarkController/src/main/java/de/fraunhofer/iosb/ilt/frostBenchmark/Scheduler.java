package de.fraunhofer.iosb.ilt.frostBenchmark;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.fraunhofer.iosb.ilt.frostBenchmark.BenchProperties.STATUS;
import de.fraunhofer.iosb.ilt.sta.ServiceFailureException;
import de.fraunhofer.iosb.ilt.sta.model.Thing;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Scheduler {

	/**
	 * The logger for this class.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(Scheduler.class);
	public static final TypeReference<Map<String, Object>> TYPE_REF_MAP_STRING_OBJECT = new TypeReference<Map<String, Object>>() {
		// Empty by design.
	};
	private JsonNode scriptTree;
	private Thing sessionThing = null;
	private ObjectMapper mapper;

	public Scheduler() {
		mapper = new ObjectMapper();
	}

	public void readSchedule(String scheduleFile) {
		FileReader fr;
		try {
			fr = new FileReader(scheduleFile);
			scriptTree = mapper.readTree(fr);
		} catch (IOException e) {
			LOGGER.error("Failed to load json.", e);
		}
	}

	public void runScript() throws ServiceFailureException, InterruptedException {
		if (scriptTree == null) {
			return;
		}

		JsonNode initProperties = scriptTree.get("initialize");
		sendCommands(initProperties, STATUS.INITIALIZE);

		JsonNode sequence = scriptTree.get("sequence");
		JsonNode run = null;
		for (int i = 0; i < sequence.size(); i++) {
			run = sequence.get(i);
			Long duration = run.get("duration").asLong();
			Long seqId = run.get("seq").asLong();
			System.out.println("run experiment " + seqId + " for " + duration + " msec");

			sendCommands(run, STATUS.RUNNING);
			Thread.sleep(duration);
		}
		sendCommands(run, STATUS.FINISHED);
		System.out.println("finished");
	}

	public void sendCommands(JsonNode properties, STATUS status) throws ServiceFailureException {
		if (sessionThing == null) {
			sessionThing = BenchData.getBenchmarkThing();
		}
		Map<String, Object> propertiesMap;
		if (properties == null) {
			propertiesMap = new HashMap<>();
		} else {
			propertiesMap = mapper.convertValue(properties, TYPE_REF_MAP_STRING_OBJECT);
		}
		propertiesMap.put(BenchProperties.TAG_STATUS, status);
		sessionThing.setProperties(propertiesMap);
		BenchData.service.update(sessionThing);
	}

}
