/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2019 by the members listed in the COPYING,        *
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

package org.matsim.ruhrgebiet.run;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.contrib.bicycle.BicycleConfigGroup;
import org.matsim.contrib.bicycle.Bicycles;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.ruhrgebiet.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RunRuhrgebietScenario {

	private static final Logger log = Logger.getLogger(RunRuhrgebietScenario.class);

	public static void main(String[] args) {

		Config config = prepareConfig(args);
		Scenario scenario = prepareScenario(config);
		Controler controler = prepareControler(scenario);
		controler.run();
	}

	public static Config prepareConfig(String[] args, ConfigGroup... modules) {

		OutputDirectoryLogging.catchLogEntries();

		BicycleConfigGroup bikeConfigGroup = new BicycleConfigGroup();
		bikeConfigGroup.setBicycleMode(TransportMode.bike);

		//this feels a little messy, but I guess this is how var-args work
		List<ConfigGroup> moduleList = new ArrayList<>(Arrays.asList(modules));
		moduleList.add(bikeConfigGroup);

		Config config = ConfigUtils.loadConfig(args, moduleList.toArray(ConfigGroup[]::new));

		config.plansCalcRoute().setInsertingAccessEgressWalk(true);

		config.qsim().setUsingTravelTimeCheckInTeleportation(true);
		config.qsim().setUsePersonIdForMissingVehicleId(false);
		config.subtourModeChoice().setProbaForRandomSingleTripMode(0.5);

		final long minDuration = 600;
		final long maxDuration = 3600 * 27;
		final long difference = 600;

		Utils.createTypicalDurations("home", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
		Utils.createTypicalDurations("work", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
		Utils.createTypicalDurations("education", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
		Utils.createTypicalDurations("leisure", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
		Utils.createTypicalDurations("shopping", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
		Utils.createTypicalDurations("other", minDuration, maxDuration, difference).forEach(params -> config.planCalcScore().addActivityParams(params));
		// TODO: for next release: define opening and closing times! ihab April'19

		return config;
	}

	public static Scenario prepareScenario(Config config) {

		var scenario = ScenarioUtils.loadScenario(config);

		// remove route information from population since the base case was calibrated with different network
		// TODO: Re-Calibrate baseCase with new network and currnt pt
		scenario.getPopulation().getPersons().values().parallelStream()
				.flatMap(person -> person.getPlans().stream())
				.flatMap(plan -> plan.getPlanElements().stream())
				.filter(element -> element instanceof Leg)
				.map(element -> (Leg) element)
				.forEach(leg -> leg.setRoute(null));

		// map persons onto new link ids
		scenario.getPopulation().getPersons().values().parallelStream()
				.flatMap(person -> person.getPlans().stream())
				.flatMap(plan -> plan.getPlanElements().stream())
				.filter(element -> element instanceof Activity)
				.map(element -> (Activity) element)
				.forEach(activity -> {
					var link = NetworkUtils.getNearestLink(scenario.getNetwork(), activity.getCoord());
					activity.setLinkId(link.getId());
				});

		return scenario;
	}

	public static Controler prepareControler(Scenario scenario) {

		Controler controler = new Controler(scenario);
		if (!controler.getConfig().transit().isUsingTransitInMobsim())
			throw new RuntimeException("Public transit will be teleported and not simulated in the mobsim! "
					+ "This will have a significant effect on pt-related parameters (travel times, modal split, and so on). "
					+ "Should only be used for testing or car-focused studies with fixed modal split.");

		controler.addOverridingModule(new SwissRailRaptorModule());
		// use the (congested) car travel time for the teleported ride mode
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				addTravelTimeBinding(TransportMode.ride).to(networkTravelTime());
				addTravelDisutilityFactoryBinding(TransportMode.ride).to(carTravelDisutilityFactoryKey());
			}
		});

		Bicycles.addAsOverridingModule(controler);
		return controler;
	}
}
