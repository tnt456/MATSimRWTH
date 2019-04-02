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

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.testcases.MatsimTestUtils;

/**
 * @author nagel
 *
 */
public class RunRuhrgebietScenarioTest {
	
	@Rule public MatsimTestUtils utils = new MatsimTestUtils() ;

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
			
			ruhrgebietScenarioRunner.run();
			
		} catch ( Exception ee ) {
			Logger.getLogger(this.getClass()).fatal("there was an exception: \n" + ee ) ;

			// if one catches an exception, then one needs to explicitly fail the test:
			Assert.fail();
		}


	}

}
