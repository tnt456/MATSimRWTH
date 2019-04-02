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

package org.matsim.run;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.mobsim.qsim.AbstractQSimModule;
import org.matsim.core.scenario.ScenarioUtils;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;

public class RunRuhrgebietScenario {
	
	public static final String CONFIG_PATH = "config-path";
	private static final Logger log = Logger.getLogger(RunRuhrgebietScenario.class );

	private final CommandLine cmd;

	private final String configFileName;
	private Config config;
	private Scenario scenario;
	private Controler controler;

	public static void main(String[] args) {
		
		for (String arg : args) {
			log.info( arg );
		}
		
		if ( args.length==0 ) {
			String configFileName = "scenarios/ruhrgebiet-v1.0-1pct/input/ruhrgebiet-v1.0-1pct.config.xml";
			new RunRuhrgebietScenario( new String[]{ "--" + CONFIG_PATH, configFileName } ).run() ;
			
		} else {
			new RunRuhrgebietScenario( args ).run() ;
		}
	}

    public RunRuhrgebietScenario(String [] args) {

    	try{
			cmd = new CommandLine.Builder( args )
					.allowPositionalArguments(false)
					.requireOptions(CONFIG_PATH)
					.allowAnyOption(true)
					.build() ;
			this.configFileName = cmd.getOptionStrict( CONFIG_PATH ) ;

		} catch( CommandLine.ConfigurationException e ){
			throw new RuntimeException( e ) ;
		}
    }

    public Controler prepareControler(AbstractModule... overridingModules) {

        if (scenario == null) prepareScenario();

        controler = new Controler(scenario);

        if (controler.getConfig().transit().isUsingTransitInMobsim()) {
			// use the sbb pt raptor router
			controler.addOverridingModule( new AbstractModule() {
				@Override
				public void install() {
					install( new SwissRailRaptorModule() );
				}
			} );
		} else {
			log.warn("Public transit will be teleported and not simulated in the mobsim! "
					+ "This will have a significant effect on pt-related parameters (travel times, modal split, and so on). "
					+ "Should only be used for testing or car-focused studies with fixed modal split.  ");
		}

        // use the (congested) car travel time for the teleported ride mode
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                addTravelTimeBinding(TransportMode.ride).to(networkTravelTime());
                addTravelDisutilityFactoryBinding(TransportMode.ride).to(carTravelDisutilityFactoryKey());
            }
        });

        for ( AbstractModule overridingModule : overridingModules ) {
			controler.addOverridingModule( overridingModule );
		}

        return controler;
    }
    
    public final void addOverridingModule( AbstractModule controlerModule ) {
		if ( controler == null ) {
			prepareControler(  ) ;
		}
		controler.addOverridingModule( controlerModule ) ;
	}
    
    public final void addOverridingQSimModule( AbstractQSimModule qSimModule ) {
		if ( controler == null ) {
			prepareControler(  ) ;
		}
		controler.addOverridingQSimModule( qSimModule );
	}

    public Scenario prepareScenario() {

        if (config == null) prepareConfig();

        scenario = ScenarioUtils.loadScenario(config);
        
        return scenario;
    }

    public Config prepareConfig(ConfigGroup... customModules) {

        OutputDirectoryLogging.catchLogEntries();
        config = ConfigUtils.loadConfig( configFileName, customModules);
        
        config.plansCalcRoute().setInsertingAccessEgressWalk(true);
        config.qsim().setUsingTravelTimeCheckInTeleportation(true);
        config.subtourModeChoice().setProbaForRandomSingleTripMode(0.5);

        final long minDuration = 600;
        final long maxDuration = 3600 * 27;
        final long difference = 600;
        addTypicalDurations("home", minDuration, maxDuration, difference);
        addTypicalDurations("work", minDuration, maxDuration, difference);
        addTypicalDurations("education", minDuration, maxDuration, difference);
        addTypicalDurations("leisure", minDuration, maxDuration, difference);
        addTypicalDurations("shopping", minDuration, maxDuration, difference);
        addTypicalDurations("other", minDuration, maxDuration, difference);

        if (cmd != null) {
			try {
				cmd.applyConfiguration( config );
			} catch (ConfigurationException e) {
				throw new RuntimeException(e);			
			}
		}
        
        return config;
    }
    
    public void run() {
		if ( controler == null ) {
			prepareControler() ;
		}
		controler.run();
		log.info("Done.");
	}

    private void addTypicalDurations(String type, long minDurationInSeconds, long maxDurationInSeconds, long durationDifferenceInSeconds) {

        for (long duration = minDurationInSeconds; duration <= maxDurationInSeconds; duration += durationDifferenceInSeconds) {
            final PlanCalcScoreConfigGroup.ActivityParams params = new PlanCalcScoreConfigGroup.ActivityParams(type + "_" + duration + ".0");
            params.setTypicalDuration(duration);
            config.planCalcScore().addActivityParams(params);
        }
    }

}
