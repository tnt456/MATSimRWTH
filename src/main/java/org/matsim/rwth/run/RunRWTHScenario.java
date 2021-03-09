package org.matsim.rwth.run;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import net.bhl.matsim.uam.config.UAMConfigGroup;
import net.bhl.matsim.uam.dispatcher.UAMManager;
import net.bhl.matsim.uam.infrastructure.UAMStations;
import net.bhl.matsim.uam.infrastructure.readers.UAMXMLReader;
import net.bhl.matsim.uam.qsim.UAMQSimModule;
import net.bhl.matsim.uam.qsim.UAMSpeedModule;
import net.bhl.matsim.uam.run.UAMConstants;
import net.bhl.matsim.uam.run.UAMModule;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.contrib.bicycle.BicycleConfigGroup;
import org.matsim.contrib.bicycle.Bicycles;
import org.matsim.contrib.dvrp.run.DvrpConfigGroup;
import org.matsim.contrib.dvrp.run.DvrpModule;
import org.matsim.contribs.discrete_mode_choice.modules.DiscreteModeChoiceModule;
import org.matsim.contribs.discrete_mode_choice.modules.config.DiscreteModeChoiceConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.MultimodalNetworkCleaner;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import java.util.*;

public class RunRWTHScenario {

    private static final Logger log = Logger.getLogger(RunRWTHScenario.class);
    private static UAMConfigGroup uamConfigGroup;
    private static Scenario scenario;
    private static Config config;

    public static void main(String[] args) {

        config = prepareConfig(args);
        scenario = prepareScenario(config);
        Controler controler = prepareControler(scenario);



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

        /*scenario.getPopulation().getFactory().getRouteFactories().setRouteFactory(DefaultEnrichedTransitRoute.class,
                new DefaultEnrichedTransitRouteFactory());
*/
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

        uamReader.readFile(ConfigGroup.getInputFileURL(controler.getConfig().getContext(), uamConfigGroup.getInputFile())
                .getPath().replace("%20", " "));
        final UAMStations uamStations = new UAMStations(uamReader.getStations(), network);
        final UAMManager uamManager = new UAMManager(network,uamStations,uamReader.getVehicles());


        // populate UAMManager
        //uamManager.setStations(uamStations);
        //uamManager.setVehicles(uamReader.getVehicles());
        UAMQSimModule uamqSimModule= new UAMQSimModule();
        //uamQsimModule.provideData();

        // sets transit modules in case of simulating/not pT
        controler.getConfig().transit().setUseTransit(true);
        if (true) {
            controler.addOverridingModule(new SwissRailRaptorModule());
            //controler.addOverridingModule(new BaselineTransitModule());
        }

        UAMModule uamModule = new UAMModule();
        //uamModule.install();
        controler.addOverridingModule(new DvrpModule());
        controler.addOverridingModule(uamModule);
        controler.addOverridingModule(new UAMSpeedModule());
        //controler.addOverridingModule(new DvrpTravelTimeModule());

        controler.configureQSimComponents(configurator -> {
            UAMQSimModule.activateModes().configure(configurator);
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

        //Did this for testing?

        return controler;
    }
}
