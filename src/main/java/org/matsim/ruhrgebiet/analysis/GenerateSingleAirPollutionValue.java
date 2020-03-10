package org.matsim.ruhrgebiet.analysis;

import org.apache.log4j.Logger;
import org.matsim.contrib.emissions.Pollutant;
import org.matsim.contrib.emissions.events.*;
import org.matsim.core.events.EventsUtils;

import java.util.HashMap;
import java.util.Map;

public class GenerateSingleAirPollutionValue implements ColdEmissionEventHandler, WarmEmissionEventHandler {

	private static final Logger logger = Logger.getLogger(GenerateSingleAirPollutionValue.class);
	private static final String eventsFile = "C:\\Users\\Janek\\repos\\runs-svn\\nemo\\wissenschaftsforum2019_simulationsbasierteZukunftsforschung\\run3_gesundeStadt-mit-RSV\\run3_gesundeStadt-mit-RSV.emission.events.offline.xml.gz";
	private final Map<Pollutant, Double> pollution = new HashMap<>();

	public static void main(String[] args) {

		new GenerateSingleAirPollutionValue().run();
	}

	public void run() {

		var manager = EventsUtils.createEventsManager();
		manager.addHandler(this);

		var reader = new EmissionEventsReader(manager);
		reader.readFile(eventsFile);

		for (Map.Entry<Pollutant, Double> pollutant : pollution.entrySet()) {
			logger.info(pollutant.getKey() + ": \t\t" + pollutant.getValue());
		}
	}

	@Override
	public void handleEvent(ColdEmissionEvent event) {

		for (Map.Entry<Pollutant, Double> pollutant : event.getColdEmissions().entrySet()) {
			pollution.merge(pollutant.getKey(), pollutant.getValue(), Double::sum);
		}
	}

	@Override
	public void handleEvent(WarmEmissionEvent event) {

		for (Map.Entry<Pollutant, Double> pollutant : event.getWarmEmissions().entrySet()) {
			pollution.merge(pollutant.getKey(), pollutant.getValue(), Double::sum);
		}
	}
}
