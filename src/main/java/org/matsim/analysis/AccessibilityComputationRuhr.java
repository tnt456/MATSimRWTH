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

import java.util.ArrayList;
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
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.run.RunRuhrgebietScenario;

/**
 * @author dziemke
 */
public class AccessibilityComputationRuhr {
	private static final Logger log = Logger.getLogger( AccessibilityComputationRuhr.class ) ;

	public static void main(String[] args) {
		String configFileName = "scenarios/ruhrgebiet-v1.0-1pct/input/ruhrgebiet-v1.0-1pct.config.xml";

		int tileSize_m = 2000;
		String accessibilityOutputFolder = "accessibility_essen_" + tileSize_m + "/";
		Envelope envelope = new Envelope(353400, 370700, 5690500, 5710700); // Essen

//		String accessibilityOutputFolder = "accessibility_ruhrgebiet_" + tileSize_m + "/";
//		Envelope envelope = new Envelope(310200, 430700, 5676900, 5742200); // Ruhrgebiet
		
		boolean createQGisOutput = true;

		// Alternative A: use a facility file which is generated externaly, e.g. based on OSM
//		String facilitiesFileName = "/Users/dominik/Bicycle/NEMO/facilities/2019-05-08_essen_vicinity.xml.gz";
//		String facilitiesFileName = "../shared-svn/projects/nemo_mercator/data/matsim_input/accessibility/2019-05-08_essen_vicinity.xml.gz";
//		String facilitiesFileName = "../shared-svn/projects/nemo_mercator/data/matsim_input/accessibility/ruhrgebiet-v1.0-1pct.output_facilities.xml.gz";
//		final List<String> activityTypes = Arrays.asList(new String[]{"supermarket"});
		
		final List<String> consideredActivityTypePrefixes = new ArrayList<>();
        consideredActivityTypePrefixes.add("work");
        consideredActivityTypePrefixes.add("other");
        consideredActivityTypePrefixes.add("education");
        consideredActivityTypePrefixes.add("leisure");

		RunRuhrgebietScenario ruhrgebietScenarioRunner = new RunRuhrgebietScenario(new String[]{ "--" + RunRuhrgebietScenario.CONFIG_PATH, configFileName });
		
		Config config = ruhrgebietScenarioRunner.prepareConfig();

		// Alternative A: use a facility file which is generated externaly, e.g. based on OSM
//		config.facilities().setInputFile(new File(facilitiesFileName).getAbsolutePath());
		
		// Alternative B: generate facilities based on the activities in the plans file
		config.facilities().setFacilitiesSource(FacilitiesSource.onePerActivityLocationInPlansFile);
	
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setFirstIteration(0);
		config.controler().setLastIteration(0);
		config.controler().setOutputDirectory(accessibilityOutputFolder);
		
		AccessibilityConfigGroup acg = ConfigUtils.addOrGetModule(config, AccessibilityConfigGroup.class);
		acg.setTileSize_m(tileSize_m);
		acg.setAreaOfAccessibilityComputation(AreaOfAccesssibilityComputation.fromBoundingBox);
		acg.setEnvelope(envelope);
		acg.setComputingAccessibilityForMode(Modes4Accessibility.freespeed, true); // car freespeed accessibility, should work
		acg.setComputingAccessibilityForMode(Modes4Accessibility.car, true); // congested car based accessibility, should work as well
		acg.setComputingAccessibilityForMode(Modes4Accessibility.walk, true); // teleported walk mode, should work as well
		acg.setComputingAccessibilityForMode(Modes4Accessibility.matrixBasedPt, true); // TODO: check!
		acg.setComputingAccessibilityForMode(Modes4Accessibility.bike, true); // TODO: check!
		
		Scenario scenario = ruhrgebietScenarioRunner.prepareScenario();
		
		boolean downsampling = true;
		// down-sampling the scenario
		if (downsampling) {
			final double sample = 0.1;
			downsample( scenario.getPopulation().getPersons(), sample ) ;
			config.qsim().setFlowCapFactor( config.qsim().getFlowCapFactor()*sample );
			config.qsim().setStorageCapFactor( config.qsim().getStorageCapFactor()*sample );
		}
		
		String activityConsideredForAccessibilityComputation = "";
		for (String consideredActivityPrefix : consideredActivityTypePrefixes) {
			activityConsideredForAccessibilityComputation = activityConsideredForAccessibilityComputation + "_" + consideredActivityPrefix;
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
			for (String actType : consideredActivityTypePrefixes) {
				String actSpecificWorkingDirectory = workingDirectory + actType + "/";
				for (Modes4Accessibility mode : acg.getIsComputingMode()) {
					VisualizationUtils.createQGisOutputGraduatedStandardColorRange(actType, mode.toString(), envelope, workingDirectory,
							config.global().getCoordinateSystem(), includeDensityLayer, lowerBound, upperBound, range, tileSize_m, populationThreshold);
					VisualizationUtils.createSnapshot(actSpecificWorkingDirectory, mode.toString(), osName);
				}
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