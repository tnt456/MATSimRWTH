/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2014 by the members listed in the COPYING,        *
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

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.noise.NoiseConfigGroup;
import org.matsim.contrib.noise.NoiseOfflineCalculation;
import org.matsim.contrib.noise.utils.MergeNoiseCSVFile;
import org.matsim.contrib.noise.utils.ProcessNoiseImmissions;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

/**
 * 
 * @author ikaddoura
 *
 */
public class RunOfflineNoiseAnalysis {
	private static final Logger log = Logger.getLogger(RunOfflineNoiseAnalysis.class);
	
//	final static String runDirectory = "runs-svn/nemo/wissenschaftsforum2019/run0_bc-ohne-RSV/output/";	
//	final static String runId = "run0_bc-ohne-RSV";
	
//	final static String runDirectory = "runs-svn/nemo/wissenschaftsforum2019/run1_bc-mit-RSV/output/";	
//	final static String runId = "run1_bc-mit-RSV";
	
	final static String runDirectory = "runs-svn/nemo/wissenschaftsforum2019/run3_gesundeStadt-mit-RSV/output/";	
	final static String runId = "run3_gesundeStadt-mit-RSV";
	
	private static String outputDirectory;
	private static double receiverPointGap;
	private static double timeBinSize;				
	private static String tunnelLinkIdFile;	

	public static void main(String[] args) {
		
		String rootDirectory = null;
		
		if (args.length == 1) {
			rootDirectory = args[0];
		} else {
			throw new RuntimeException("Please set the root directory. Aborting...");
		}
		
		outputDirectory = rootDirectory + runDirectory;
		
		tunnelLinkIdFile = null;
		receiverPointGap = 100.;
		timeBinSize = 3600.;
		
		Config config = ConfigUtils.createConfig(new NoiseConfigGroup());
		config.global().setCoordinateSystem("EPSG:25832");
		config.network().setInputCRS("EPSG:25832");
		config.network().setInputFile(rootDirectory + runDirectory + runId + ".output_network.xml.gz");
		config.plans().setInputFile(rootDirectory + runDirectory + runId + ".output_plans.xml.gz");
		config.controler().setOutputDirectory(rootDirectory + runDirectory);
		config.controler().setRunId(runId);
						
		// adjust the default noise parameters
		
		NoiseConfigGroup noiseParameters = (NoiseConfigGroup) config.getModules().get(NoiseConfigGroup.GROUP_NAME);
		noiseParameters.setReceiverPointGap(receiverPointGap);

		double xMin = 317373;
		double yMin = 5675521.;
		double xMax = 418575.;
		double yMax = 5736671.;
		
		noiseParameters.setReceiverPointsGridMinX(xMin);
		noiseParameters.setReceiverPointsGridMinY(yMin);
		noiseParameters.setReceiverPointsGridMaxX(xMax);
		noiseParameters.setReceiverPointsGridMaxY(yMax);
		
		String[] consideredActivitiesForDamages = {"home*", "work*", "leisure*", "shopping*", "other*"};
		noiseParameters.setConsideredActivitiesForDamageCalculationArray(consideredActivitiesForDamages);
		
		// ################################
		
		noiseParameters.setUseActualSpeedLevel(true);
		noiseParameters.setAllowForSpeedsOutsideTheValidRange(false);
		noiseParameters.setScaleFactor(100.);
		noiseParameters.setComputePopulationUnits(true);
		noiseParameters.setComputeNoiseDamages(true);
		noiseParameters.setInternalizeNoiseDamages(false);
		noiseParameters.setComputeCausingAgents(false);
		noiseParameters.setThrowNoiseEventsAffected(true);
		noiseParameters.setThrowNoiseEventsCaused(false);
		
		String[] hgvIdPrefixes = { "freight" };
		noiseParameters.setHgvIdPrefixesArray(hgvIdPrefixes);
		
		noiseParameters.setTunnelLinkIdFile(tunnelLinkIdFile);
		noiseParameters.setTimeBinSizeNoiseComputation(timeBinSize);
		
//		String networkModesToIgnore = "bike";
//		noiseParameters.setNetworkModesToIgnore(networkModesToIgnore);
		
		Scenario scenario = ScenarioUtils.loadScenario(config);
		NoiseOfflineCalculation noiseCalculation = new NoiseOfflineCalculation(scenario, outputDirectory);
		noiseCalculation.run();	
		
		// some processing of the output data
		String outputFilePath = outputDirectory + "noise-analysis/";
		ProcessNoiseImmissions process = new ProcessNoiseImmissions(outputFilePath + "immissions/", outputFilePath + "receiverPoints/receiverPoints.csv", noiseParameters.getReceiverPointGap());
		process.run();
				
		final String[] labels = { "damages_receiverPoint" };
		final String[] workingDirectories = { outputFilePath + "/damages_receiverPoint/" };

		MergeNoiseCSVFile merger = new MergeNoiseCSVFile() ;
		merger.setReceiverPointsFile(outputFilePath + "receiverPoints/receiverPoints.csv");
		merger.setOutputDirectory(outputFilePath);
		merger.setTimeBinSize(noiseParameters.getTimeBinSizeNoiseComputation());
		merger.setWorkingDirectory(workingDirectories);
		merger.setLabel(labels);
		merger.run();
		
		log.info("Done.");
	}
}
		

