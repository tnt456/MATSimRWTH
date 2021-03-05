package org.matsim.rwth.run;

import net.bhl.matsim.uam.config.UAMConfigGroup;
import net.bhl.matsim.uam.run.UAMConstants;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.contrib.bicycle.BicycleConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contribs.discrete_mode_choice.modules.config.DiscreteModeChoiceConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.MultimodalNetworkCleaner;
import org.matsim.core.scenario.MutableScenario;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import java.util.*;

public class RunNetworkCleaner {

    private static final Logger log = Logger.getLogger(RunRWTHScenario.class);
    private static UAMConfigGroup uamConfigGroup;
    private static Scenario scenario;
    private static Config config;
    private static Network network;

    public static void main(String[] args) {

        config = prepareConfig(args);
        scenario = prepareScenario(config);
        MultimodalNetworkCleaner myCleaner = new MultimodalNetworkCleaner(network);
        Set<String> modes = new HashSet<>();
        modes.add(UAMConstants.uam);
        modes.add("car");
        myCleaner.run(modes);

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
        moduleList.add(new DvrpConfigGroup().setNetworkModesAsString("uam"));
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
        network = scenario.getNetwork();

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
}
