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

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Envelope;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contrib.accessibility.AccessibilityConfigGroup;
import org.matsim.contrib.accessibility.AccessibilityConfigGroup.AreaOfAccesssibilityComputation;
import org.matsim.contrib.accessibility.utils.VisualizationUtils;
import org.matsim.contrib.accessibility.Modes4Accessibility;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.run.RunRuhrgebietScenario;

/**
 * @author dziemke
 */
public class AccessibilityComputationRuhr {
	private static final Logger log = Logger.getLogger( AccessibilityComputationRuhr.class ) ;

	public static void main(String[] args) {
		// Parameters
		String configFileName = "scenarios/ruhrgebiet-v1.0-1pct/input/ruhrgebiet-v1.0-1pct.config.xml";

		int tileSize_m = 1000;
		String accessibilityOutputFolder = "accessibility_essen_" + tileSize_m + "/";
		boolean createQGisOutput = true;
		Envelope envelope = new Envelope(353400, 370700, 5690500, 5710700); // Essen
//		Envelope envelope = new Envelope(310200, 430700, 5676900, 5742200); // Ruhrgebiet
		String facilitiesFileName = "/Users/dominik/Bicycle/NEMO/facilities/2019-05-08_essen_vicinity.xml.gz";
		final List<String> activityTypes = Arrays.asList(new String[]{"supermarket"});
		//

		RunRuhrgebietScenario ruhrgebietScenarioRunner = new RunRuhrgebietScenario(new String[]{ "--" + RunRuhrgebietScenario.CONFIG_PATH, configFileName });
		
		Config config = ruhrgebietScenarioRunner.prepareConfig();
	
		config.facilities().setInputFile(new File(facilitiesFileName).getAbsolutePath());
		
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
		config.controler().setFirstIteration(0);
		config.controler().setLastIteration(0);
		
		config.controler().setOutputDirectory(config.controler().getOutputDirectory() + accessibilityOutputFolder);
		
		AccessibilityConfigGroup acg = ConfigUtils.addOrGetModule(config, AccessibilityConfigGroup.class);
		acg.setTileSize_m(tileSize_m);
		acg.setAreaOfAccessibilityComputation(AreaOfAccesssibilityComputation.fromBoundingBox);
		acg.setEnvelope(envelope);
		acg.setComputingAccessibilityForMode(Modes4Accessibility.freespeed, false);
		acg.setComputingAccessibilityForMode(Modes4Accessibility.car, false);
		acg.setComputingAccessibilityForMode(Modes4Accessibility.bike, true);
		
		Scenario scenario = ruhrgebietScenarioRunner.prepareScenario();
		final double sample = 0.01;
		downsample( scenario.getPopulation().getPersons(), sample ) ;
		config.qsim().setFlowCapFactor( config.qsim().getFlowCapFactor()*sample );
		config.qsim().setStorageCapFactor( config.qsim().getStorageCapFactor()*sample );
		
		org.matsim.core.controler.Controler controler = ruhrgebietScenarioRunner.prepareControler();
		
		for (String activityType : activityTypes) {
			AccessibilityModuleRuhr module = new AccessibilityModuleRuhr();
			module.setConsideredActivityType(activityType);
//			module.addAdditionalFacilityData(densityFacilities);
//			module.setPushing2Geoserver(push2Geoserver);
			controler.addOverridingModule(module);
		}
		
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
			for (String actType : activityTypes) {
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