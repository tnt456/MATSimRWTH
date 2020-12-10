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


import ch.ethz.matsim.baseline_scenario.transit.routing.DefaultEnrichedTransitRoute;
import ch.ethz.matsim.baseline_scenario.transit.routing.DefaultEnrichedTransitRouteFactory;
import ch.ethz.matsim.baseline_scenario.transit.simulation.BaselineTransitModule;
import ch.ethz.matsim.discrete_mode_choice.modules.DiscreteModeChoiceConfigurator;
import ch.ethz.matsim.discrete_mode_choice.modules.DiscreteModeChoiceModule;
import ch.ethz.matsim.discrete_mode_choice.modules.config.DiscreteModeChoiceConfigGroup;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import net.bhl.matsim.uam.config.UAMConfigGroup;
import net.bhl.matsim.uam.data.UAMFleetData;
import net.bhl.matsim.uam.dispatcher.UAMManager;
import net.bhl.matsim.uam.infrastructure.UAMStations;
import net.bhl.matsim.uam.infrastructure.UAMVehicleType;
import net.bhl.matsim.uam.infrastructure.readers.UAMXMLReader;
import net.bhl.matsim.uam.qsim.UAMQsimModule;
import net.bhl.matsim.uam.qsim.UAMSpeedModule;
import net.bhl.matsim.uam.run.UAMConstants;
import net.bhl.matsim.uam.run.UAMModule;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.contrib.bicycle.BicycleConfigGroup;
import org.matsim.contrib.bicycle.Bicycles;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.trafficmonitoring.DvrpTravelTimeModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.ruhrgebiet.Utils;
import org.matsim.core.controler.ControlerUtils;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RunRuhrgebietScenario {

	private static final Logger log = Logger.getLogger(RunRuhrgebietScenario.class);
	private static UAMConfigGroup uamConfigGroup;
	private static Scenario scenario;


	public static void main(String[] args) {

		Config config = prepareConfig(args);
		Scenario scenario = prepareScenario(config);
		Controler controler = prepareControler(scenario);
		VehicleType a = VehicleUtils.getDefaultVehicleType();
		controler.run();
	}

	public static Config prepareConfig(String[] args, ConfigGroup... modules) {

		OutputDirectoryLogging.catchLogEntries();

		BicycleConfigGroup bikeConfigGroup = new BicycleConfigGroup();
		bikeConfigGroup.setBicycleMode(TransportMode.bike);

		uamConfigGroup = new UAMConfigGroup();


		//this feels a little messy, but I guess this is how var-args work
		List<ConfigGroup> moduleList = new ArrayList<>(Arrays.asList(modules));
		moduleList.add(bikeConfigGroup);
		moduleList.add(new DiscreteModeChoiceConfigGroup());
		moduleList.add(new DvrpConfigGroup());
		moduleList.add(uamConfigGroup);

		Config config = ConfigUtils.loadConfig(args,moduleList.toArray(ConfigGroup[]::new));

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

		scenario = ScenarioUtils.createScenario(config);
		ScenarioUtils.loadScenario(scenario);

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

		scenario.getPopulation().getFactory().getRouteFactories().setRouteFactory(DefaultEnrichedTransitRoute.class,
				new DefaultEnrichedTransitRouteFactory());

		Controler controler = new Controler(scenario);


		// Initiate Urban Air Mobility XML reading and parsing
		Network network = controler.getScenario().getNetwork();
		TransportModeNetworkFilter filter = new TransportModeNetworkFilter(network);
		Set<String> modes = new HashSet<>();
		modes.add(UAMConstants.uam);
		Network networkUAM = NetworkUtils.createNetwork();
		filter.filter(networkUAM, modes);

		filter = new TransportModeNetworkFilter(network);
		Set<String> modesCar = new HashSet<>();
		modesCar.add(TransportMode.car);
		Network networkCar = NetworkUtils.createNetwork();
		filter.filter(networkCar, modesCar);

		// set up the UAM infrastructure
		UAMXMLReader uamReader = new UAMXMLReader(networkUAM);

		uamReader.readFile(ConfigGroup.getInputFileURL(controler.getConfig().getContext(), uamConfigGroup.getUAM())
				.getPath().replace("%20", " "));
		final UAMStations uamStations = new UAMStations(uamReader.getStations(), network);
		final UAMManager uamManager = new UAMManager(network);

		// populate UAMManager
		uamManager.setStations(uamStations);
		UAMQsimModule uamQsimModule = new UAMQsimModule(uamReader,uamManager);
		uamQsimModule.provideData();

		// sets transit modules in case of simulating/not pT
		controler.getConfig().transit().setUseTransit(uamConfigGroup.getPtSimulation());
		if (uamConfigGroup.getPtSimulation()) {
			controler.addOverridingModule(new SwissRailRaptorModule());
			controler.addOverridingModule(new BaselineTransitModule());
		}

		UAMModule uamModule = new UAMModule(uamManager,networkUAM,networkCar,uamReader);
		//uamModule.install();

		controler.addOverridingModule(uamModule);
		controler.addOverridingModule(new UAMSpeedModule(uamReader.getMapVehicleVerticalSpeeds(),
				uamReader.getMapVehicleHorizontalSpeeds()));
		controler.addOverridingModule(new DvrpTravelTimeModule());

		controler.configureQSimComponents(configurator -> {
			UAMQsimModule.configureComponents(configurator);
		});

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

		controler.addOverridingModule(new DiscreteModeChoiceModule());


		VehicleUtils.getDefaultVehicleType();
		return controler;
	}
}
