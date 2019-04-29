/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */
package org.matsim.run;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.analysis.ScoreStatsControlerListener;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.testcases.MatsimTestUtils;

import static org.matsim.testcases.MatsimTestUtils.EPSILON;

/**
 * @author nagel zmeng
 *
 */
public class RunRuhrgebietScenarioTest {
	private static final Logger log = Logger.getLogger( RunRuhrgebietScenarioTest.class ) ;
	
	@Rule public MatsimTestUtils utils = new MatsimTestUtils() ;

	@Test
	public final void test0() {
		
		String configFileName = "scenarios/ruhrgebiet-v1.0-1pct/input/ruhrgebiet-v1.0-1pct.config.xml";

		try {
			
			RunRuhrgebietScenario runner0 = new RunRuhrgebietScenario(new String[]{ "--" + RunRuhrgebietScenario.CONFIG_PATH, configFileName });
			runner0.prepareConfig();
			
			RunRuhrgebietScenario runner1 = new RunRuhrgebietScenario(new String[]{ "--config-path", configFileName});
			runner1.prepareConfig();

			RunRuhrgebietScenario runner2 = new RunRuhrgebietScenario(new String[]{ configFileName });
			runner2.prepareConfig();

		} catch ( Exception ee ) {
			Logger.getLogger(this.getClass()).fatal("there was an exception: \n" + ee ) ;

			// if one catches an exception, then one needs to explicitly fail the test:
			Assert.fail();
		}
	}
	
	@Test
	public final void test1() {
		
		String configFileName = "scenarios/ruhrgebiet-v1.0-1pct/input/ruhrgebiet-v1.0-1pct.config.xml";

		try {

			RunRuhrgebietScenario ruhrgebietScenarioRunner = new RunRuhrgebietScenario(new String[]{ "--" + RunRuhrgebietScenario.CONFIG_PATH, configFileName });

			Config config = ruhrgebietScenarioRunner.prepareConfig();
			config.controler().setWriteEventsInterval(1); // change the 0 to 1, otherwise, there will be no eventsFile
			config.controler().setLastIteration(0);
			config.controler().setOutputDirectory( utils.getOutputDirectory() );
			config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
			config.controler().setWritePlansUntilIteration( 0 );
			config.controler().setWritePlansInterval( 0 );
			config.qsim().setNumberOfThreads( 1 );
			config.global().setNumberOfThreads( 1 );
			
			Scenario scenario = ruhrgebietScenarioRunner.prepareScenario();
			final double sample = 0.01;
			downsample( scenario.getPopulation().getPersons(), sample ) ;
			config.qsim().setFlowCapFactor( config.qsim().getFlowCapFactor()*sample );
			config.qsim().setStorageCapFactor( config.qsim().getStorageCapFactor()*sample );
			
			ruhrgebietScenarioRunner.run();

			// ScoresTests
			Assert.assertEquals(1.2746698932141114,ruhrgebietScenarioRunner.getScoreStats().getScoreHistory().get(ScoreStatsControlerListener.ScoreItem.average).get(0), EPSILON);

			// ModestatsTests
			Map<String,Double> modestats = getModestats("test/output/org/matsim/run/RunRuhrgebietScenarioTest/test1/ruhrgebiet-v1.0-1pct.modestats.txt");
			Assert.assertEquals(0.09121245828698554,modestats.get("bike"),0.05);
			Assert.assertEquals(0.3770856507230256,modestats.get("car"),0.05);
			Assert.assertEquals(0.29699666295884314,modestats.get("pt"),0.05);
			Assert.assertEquals(0.06229143492769744,modestats.get("walk"),0.05);
			Assert.assertEquals(0.1724137931034483,modestats.get("ride"),0.05);

			// Mode-specific travel time of the Agent (person1:id = 126516001 && person2:id = 1286397001 )
			String person1 = "1265160001";
			String person2 = "1286397001";
			List<String> personList = new LinkedList<>();
			personList.add(person1); personList.add(person2);

			Map<String, Map<String, TripTime>> tripTime2mode2Person = tripTime2mode2Person(personList);

			Assert.assertEquals(198.0, tripTime2mode2Person.get(person1).get("car").tripTime,EPSILON);
			Assert.assertEquals(609.0, tripTime2mode2Person.get(person1).get("pt").tripTime,EPSILON);
			Assert.assertEquals(1160.0, tripTime2mode2Person.get(person1).get("ride").tripTime,EPSILON);
			Assert.assertEquals(1270.0,tripTime2mode2Person.get(person1).get("walk").tripTime,EPSILON);

			Assert.assertEquals(1474.0, tripTime2mode2Person.get(person2).get("bike").tripTime,EPSILON);
			Assert.assertEquals(537.0, tripTime2mode2Person.get(person2).get("ride").tripTime,EPSILON);
			Assert.assertEquals(1287.0,tripTime2mode2Person.get(person2).get("walk").tripTime,EPSILON);

		} catch ( Exception ee ) {
			Logger.getLogger(this.getClass()).fatal("there was an exception: \n" + ee ) ;

			// if one catches an exception, then one needs to explicitly fail the test:
			Assert.fail();
		}
	}
	
	private static void downsample( final Map<Id<Person>, ? extends Person> map, final double sample ) {
		final Random rnd = MatsimRandom.getLocalInstance();
		log.warn( "map size before=" + map.size() ) ;
		map.values().removeIf( person -> rnd.nextDouble()>sample ) ;
		log.warn( "map size after=" + map.size() ) ;
	}

	private Map<String,Double> getModestats(String modestats){
		File inputFile = new File(modestats);
		Map<String, Double> getModestats = new HashMap<>();

		try(BufferedReader in = new BufferedReader(new FileReader(inputFile))){
			String line = in.readLine();
			String[] modes = line.split("\t");
			String line2 = in.readLine();
			String[] modeSplit = line2.split("\t");

			for(int i=1;i<modes.length ;i++){
				getModestats.put(modes[i],Double.valueOf(modeSplit[i]));
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
		return getModestats;
	}

	private Map<String,Map<String,TripTime>> tripTime2mode2Person (List<String> personList){

		String networkFile = "test/output/org/matsim/run/RunRuhrgebietScenarioTest/test1/ruhrgebiet-v1.0-1pct.output_network.xml.gz";
		String outputEventsFile = "test/output/org/matsim/run/RunRuhrgebietScenarioTest/test1/ruhrgebiet-v1.0-1pct.output_events.xml.gz";

		EventsManager eventsManager = EventsUtils.createEventsManager();

		Network network = NetworkUtils.createNetwork();
		new MatsimNetworkReader(network).readFile(networkFile);

		SpecificTripTime2ModeEventHandler specificTripTime2ModeEventHandler = new SpecificTripTime2ModeEventHandler(network, personList);

		eventsManager.addHandler(specificTripTime2ModeEventHandler);

		MatsimEventsReader reader = new MatsimEventsReader(eventsManager);
		reader.readFile(outputEventsFile);

		return specificTripTime2ModeEventHandler.allTrips2Person;
	}



}

