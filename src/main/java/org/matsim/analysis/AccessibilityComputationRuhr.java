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
package org.matsim.analysis;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Envelope;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.contrib.accessibility.AccessibilityConfigGroup;
import org.matsim.contrib.accessibility.AccessibilityConfigGroup.AreaOfAccesssibilityComputation;
import org.matsim.contrib.accessibility.Modes4Accessibility;
import org.matsim.contrib.accessibility.utils.VisualizationUtils;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.FacilitiesConfigGroup.FacilitiesSource;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.FacilitiesFromPopulation;
import org.matsim.run.RunRuhrgebietScenario;

/**
 * @author dziemke, ikaddoura
 */
public class AccessibilityComputationRuhr {
	private static final Logger log = Logger.getLogger( AccessibilityComputationRuhr.class ) ;
	
	private final boolean downsampling = false;
	private final Envelope envelope = new Envelope(310200, 430700, 5676900, 5742200); // Ruhrgebiet
//	private final Envelope envelope = new Envelope(353400, 370700, 5690500, 5710700); // Essen
	private final List<String> consideredActivityTypePrefixes = Arrays.asList(new String[]{"work","other","education","leisure"});
	private final boolean createQGisOutput = true;
	private final boolean computeFreespeedAccessibility = true;
	private final boolean computeCarAccessibility = true;
	private final boolean computeWalkAccessibility = true;
	private final boolean computePtAccessibility = true;
	private final boolean computeBikeAccessibility = true;
	
	public static void main(String[] args) {
		
		String outputDirectory;
		String runId;	
		int tileSize_m;		
		
		if (args.length > 0) {
			
			outputDirectory = args[0];
			log.info("outputDirectory: " + outputDirectory);
			
			runId = args[1];
			log.info("runId: " + runId);
			
			tileSize_m = Integer.parseInt(args[2]);
			log.info("tileSize_m: " + tileSize_m);
			
		} else {

			outputDirectory = "/runs-svn/nemo/wissenschaftsforum2019_simulationsbasierteZukunftsforschung/run0_bc-ohne-RSV/";
			runId = "run0_bc-ohne-RSV";
			
//			outputDirectory = "/Users/ihab/Documents/workspace/runs-svn/nemo/wissenschaftsforum2019_simulationsbasierteZukunftsforschung/run3_gesundeStadt-mit-RSV/";
//			runId = "run3_gesundeStadt-mit-RSV";
			
			tileSize_m = 5000;
		}
				
		AccessibilityComputationRuhr accessibilities = new AccessibilityComputationRuhr();
		accessibilities.run(outputDirectory, runId, tileSize_m);
	}
	
	private void run(String outputDirectory, String runId, int tileSize_m) {
		
		String dirSubString = "";
		if (downsampling) {
			dirSubString = "downsampling=" + downsampling + "_";
		}
		final String accessibilityOutputFolder = "accessibility_" + dirSubString + "tileSize=" + tileSize_m + "/";	
		if (!outputDirectory.endsWith("/")) outputDirectory = outputDirectory + "/";
		
		RunRuhrgebietScenario ruhrgebietScenarioRunner = new RunRuhrgebietScenario(new String[]{ "--" + RunRuhrgebietScenario.CONFIG_PATH, outputDirectory + runId + ".output_config.xml" });
		
		Config config = ruhrgebietScenarioRunner.prepareConfig();
		config.facilities().setFacilitiesSource(FacilitiesSource.setInScenario);
		config.plans().setInputFile(outputDirectory + runId + ".output_plans.xml.gz");
		config.network().setInputFile(outputDirectory + runId + ".output_network.xml.gz");
		config.transit().setTransitScheduleFile(outputDirectory + runId + ".output_transitSchedule.xml.gz");
		config.transit().setVehiclesFile(outputDirectory + runId + ".output_transitVehicles.xml.gz");
		config.vehicles().setVehiclesFile(outputDirectory + runId + ".output_vehicles.xml.gz");
		config.controler().setFirstIteration(config.controler().getLastIteration());
		config.controler().setLastIteration(config.controler().getLastIteration());
		config.controler().setOutputDirectory(outputDirectory + accessibilityOutputFolder);
		
		AccessibilityConfigGroup acg = ConfigUtils.addOrGetModule(config, AccessibilityConfigGroup.class);
		acg.setTileSize_m(tileSize_m);
		acg.setAreaOfAccessibilityComputation(AreaOfAccesssibilityComputation.fromBoundingBox);
		acg.setEnvelope(envelope);
		acg.setComputingAccessibilityForMode(Modes4Accessibility.freespeed, computeFreespeedAccessibility);
		acg.setComputingAccessibilityForMode(Modes4Accessibility.car, computeCarAccessibility);
		acg.setComputingAccessibilityForMode(Modes4Accessibility.walk, computeWalkAccessibility); 
		acg.setComputingAccessibilityForMode(Modes4Accessibility.pt, computePtAccessibility); 
		acg.setComputingAccessibilityForMode(Modes4Accessibility.bike, computeBikeAccessibility);
		
		Scenario scenario = ruhrgebietScenarioRunner.prepareScenario();
		
		// down-sampling the scenario
		if (downsampling) {
			final double sample = 0.1;
			downsample( scenario.getPopulation().getPersons(), sample ) ;
			config.qsim().setFlowCapFactor( config.qsim().getFlowCapFactor() * sample );
			config.qsim().setStorageCapFactor( config.qsim().getStorageCapFactor() * sample );
		}
		
		String activityConsideredForAccessibilityComputation = "";
		for (String consideredActivityPrefix : consideredActivityTypePrefixes) {
			if (activityConsideredForAccessibilityComputation.length() > 0) activityConsideredForAccessibilityComputation = activityConsideredForAccessibilityComputation + "_";
			activityConsideredForAccessibilityComputation = activityConsideredForAccessibilityComputation + consideredActivityPrefix + "*";
		}
		
		for (Person person : scenario.getPopulation().getPersons().values()) {
			for (Plan plan : person.getPlans()) {
				for (PlanElement pE : plan.getPlanElements()) {
					if (pE instanceof Activity) {
						Activity act = (Activity) pE;
						for (String consideredActivityTypePrefix : consideredActivityTypePrefixes) {
							if (act.getType().startsWith(consideredActivityTypePrefix)) {
								act.setType(activityConsideredForAccessibilityComputation);
							}
						}
					}
				}
			}
		}
		
		ActivityParams actParams = new ActivityParams(activityConsideredForAccessibilityComputation);
		actParams.setTypicalDuration(8 * 3600.); // shouldn't have any effect in the accessibility computation
		config.planCalcScore().addActivityParams(actParams);
		
		// now generate the facilities from the population and add them to the scenario
		{
			Scenario scenarioToGenerateFacilities = ScenarioUtils.createScenario(ConfigUtils.createConfig());
			scenarioToGenerateFacilities.getConfig().facilities().setFacilitiesSource(FacilitiesSource.onePerActivityLocationInPlansFile);
			FacilitiesFromPopulation facilitiesFromPopulation = new FacilitiesFromPopulation(scenarioToGenerateFacilities);
			facilitiesFromPopulation.setAssignLinksToFacilitiesIfMissing(scenario.getNetwork());
			facilitiesFromPopulation.run(scenario.getPopulation());			
			for (ActivityFacility facility : scenarioToGenerateFacilities.getActivityFacilities().getFacilities().values()) {
				scenario.getActivityFacilities().addActivityFacility(facility);
			}
		}
		
		org.matsim.core.controler.Controler controler = ruhrgebietScenarioRunner.prepareControler();
		
		AccessibilityModuleRuhr module = new AccessibilityModuleRuhr();
		module.setConsideredActivityType(activityConsideredForAccessibilityComputation);
		controler.addOverridingModule(module);
		
		ruhrgebietScenarioRunner.run();
		
		// QGis
		if (createQGisOutput) {
			final boolean includeDensityLayer = false;
			final Integer range = 9; // In the current implementation, this must always be 9
			final Double lowerBound = -3.5; // (upperBound - lowerBound) ideally nicely divisible by (range - 2) // TODO
			final Double upperBound = 3.5;
			final int populationThreshold = (int) (0 / (1000/(double) tileSize_m * 1000/(double) tileSize_m)); // TODO

			String osName = System.getProperty("os.name");
			String workingDirectory = config.controler().getOutputDirectory();
			
			String actSpecificWorkingDirectory = workingDirectory + activityConsideredForAccessibilityComputation + "/";
			for (Modes4Accessibility mode : acg.getIsComputingMode()) {
				VisualizationUtils.createQGisOutputGraduatedStandardColorRange(activityConsideredForAccessibilityComputation, mode.toString(), envelope, workingDirectory,
						config.global().getCoordinateSystem(), includeDensityLayer, lowerBound, upperBound, range, tileSize_m, populationThreshold);
				VisualizationUtils.createSnapshot(actSpecificWorkingDirectory, mode.toString(), osName);
			}
		}
	}

	private static void downsample( final Map<Id<Person>, ? extends Person> map, final double sample ) {
		final Random rnd = MatsimRandom.getLocalInstance();
		log.warn( "map size before=" + map.size() ) ;
		map.values().removeIf( person -> rnd.nextDouble()>sample ) ;
		log.warn( "map size after=" + map.size() ) ;
	}
}