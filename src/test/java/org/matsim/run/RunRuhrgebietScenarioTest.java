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

import static org.matsim.testcases.MatsimTestUtils.EPSILON;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.analysis.ScoreStatsControlerListener;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.testcases.MatsimTestUtils;

/**
 * 
 * @author zmeng, ikaddoura
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
			config.controler().setWriteEventsInterval(0);
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
			
			org.matsim.core.controler.Controler controler = ruhrgebietScenarioRunner.prepareControler();
			
			final Id<Person> person1 = Id.createPersonId("1265160001");
			final Id<Person> person2 = Id.createPersonId("1286397001");
			final Set<Id<Person>> personList = new HashSet<>();
			personList.add(person1);
			personList.add(person2);
			
			LegAnalyzer legAnalyzer = new LegAnalyzer(personList);
			controler.addOverridingModule(new AbstractModule() {
				
				@Override
				public void install() {
					this.addEventHandlerBinding().toInstance(legAnalyzer);
				}
			});
			
			ruhrgebietScenarioRunner.run();

			// modal split
			
			Map<String,Double> modestats = getModestats("test/output/org/matsim/run/RunRuhrgebietScenarioTest/test1/ruhrgebiet-v1.0-1pct.modestats.txt");
			Assert.assertEquals(0.09121245828698554,modestats.get("bike"),EPSILON);
			Assert.assertEquals(0.3770856507230256,modestats.get("car"),EPSILON);
			Assert.assertEquals(0.29699666295884314,modestats.get("pt"),EPSILON);
			Assert.assertEquals(0.06229143492769744,modestats.get("walk"),EPSILON);
			Assert.assertEquals(0.1724137931034483,modestats.get("ride"),EPSILON);

			// travel times of person 1
						
			// first access walk leg
			Assert.assertEquals(18.0, legAnalyzer.getPerson2legInfo().get(person1).get(0).getTravelTime(),EPSILON);		
			// ride
			Assert.assertEquals(1160.0, legAnalyzer.getPerson2legInfo().get(person1).get(1).getTravelTime(),EPSILON);	
			// first egress walk leg
			Assert.assertEquals(117.0, legAnalyzer.getPerson2legInfo().get(person1).get(2).getTravelTime(),EPSILON);
			// walk
			Assert.assertEquals(1270.0,legAnalyzer.getPerson2legInfo().get(person1).get(4).getTravelTime(),EPSILON);
			// pt
			Assert.assertEquals(609.0, legAnalyzer.getPerson2legInfo().get(person1).get(6).getTravelTime(),EPSILON);
			// car
			Assert.assertEquals(198.0, legAnalyzer.getPerson2legInfo().get(person1).get(9).getTravelTime(),EPSILON);

			// travel times of person 2
			
			// bike
			Assert.assertEquals(1474.0, legAnalyzer.getPerson2legInfo().get(person2).get(1).getTravelTime(),EPSILON);
			// ride
			Assert.assertEquals(537.0, legAnalyzer.getPerson2legInfo().get(person2).get(7).getTravelTime(),EPSILON);
			// walk
			Assert.assertEquals(1287.0,legAnalyzer.getPerson2legInfo().get(person2).get(9).getTravelTime(),EPSILON);
			
			// scores
			
//			Assert.assertEquals(1.2746698932141114, ruhrgebietScenarioRunner.getScoreStats().getScoreHistory().get(ScoreStatsControlerListener.ScoreItem.average).get(0), EPSILON);
			Assert.assertEquals(1.10941588204182, ruhrgebietScenarioRunner.getScoreStats().getScoreHistory().get(ScoreStatsControlerListener.ScoreItem.average).get(0), EPSILON);

		} catch ( Exception ee ) {
			Logger.getLogger(this.getClass()).fatal("there was an exception: \n" + ee ) ;

			// if one catches an exception, then one needs to explicitly fail the test:
			ee.printStackTrace();
			Assert.fail();
		}
	}
	
	@Ignore // TODO: Make this test fit into travis-ci.
	@Test
	public final void test2() {
		
		String configFileName = "scenarios/ruhrgebiet-v1.0-1pct/input/ruhrgebiet-v1.0-1pct.config.xml";

		try {

			RunRuhrgebietScenario ruhrgebietScenarioRunner = new RunRuhrgebietScenario(new String[]{ "--" + RunRuhrgebietScenario.CONFIG_PATH, configFileName });

			Config config = ruhrgebietScenarioRunner.prepareConfig();
			config.controler().setWriteEventsInterval(0);
			config.controler().setLastIteration(20);
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

			// modal split
			
			Map<String,Double> modestats = getModestats("test/output/org/matsim/run/RunRuhrgebietScenarioTest/test1/ruhrgebiet-v1.0-1pct.modestats.txt");
			Assert.assertEquals(0.09121245828698554,modestats.get("bike"),0.05);
			Assert.assertEquals(0.3770856507230256,modestats.get("car"),0.05);
			Assert.assertEquals(0.29699666295884314,modestats.get("pt"),0.05);
			Assert.assertEquals(0.06229143492769744,modestats.get("walk"),0.05);
			Assert.assertEquals(0.1724137931034483,modestats.get("ride"),0.05);

			// scores
			
			// TODO: add score test
//			Assert.assertEquals(xxx, ruhrgebietScenarioRunner.getScoreStats().getScoreHistory().get(ScoreStatsControlerListener.ScoreItem.average).get(20), EPSILON);

		} catch ( Exception ee ) {
			Logger.getLogger(this.getClass()).fatal("there was an exception: \n" + ee ) ;

			// if one catches an exception, then one needs to explicitly fail the test:
			ee.printStackTrace();
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
}

