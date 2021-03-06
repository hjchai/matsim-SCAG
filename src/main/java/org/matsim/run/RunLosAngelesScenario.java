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

import static org.matsim.core.config.groups.ControlerConfigGroup.RoutingAlgorithmType.FastAStarLandmarks;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.QSimConfigGroup.TrafficDynamics;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.scenario.ScenarioUtils;

import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;

/**
 * @author nagel, ikaddoura
 *
 */
public class RunLosAngelesScenario {
	private static final Logger log = Logger.getLogger(RunLosAngelesScenario.class );

	public static void main(String[] args) {
		
		String rootDirectory = null;
		
		if (args.length == 1) {
			rootDirectory = args[0];
		} else {
			throw new RuntimeException("Please set the root directory (the directory above 'matsim-input-files'). Aborting...");
		}
		
		if (!rootDirectory.endsWith("/")) rootDirectory = rootDirectory + "/";
		
		String configFile = rootDirectory + "matsim-input-files/scag-config_2019-11-19.xml";
		
		Config config = prepareConfig( configFile ) ;
		Scenario scenario = prepareScenario( config ) ;
		Controler controler = prepareControler( scenario ) ;
		controler.run() ;
	}
	
	public static Controler prepareControler( Scenario scenario ) {
		// note that for something like signals, and presumably drt, one needs the controler object
		
		Gbl.assertNotNull(scenario);
		
		final Controler controler = new Controler( scenario );
		
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
					+ "Should only be used for testing or car-focused studies with a fixed modal split.  ");
		}
		
		// use the (congested) car travel time for the teleported ride mode
		controler.addOverridingModule( new AbstractModule() {
			@Override
			public void install() {
				addTravelTimeBinding( TransportMode.ride ).to( networkTravelTime() );
				addTravelDisutilityFactoryBinding( TransportMode.ride ).to( carTravelDisutilityFactoryKey() );
			}
		} );

		return controler;
	}
	
	public static Scenario prepareScenario( Config config ) {
		Gbl.assertNotNull( config );
		final Scenario scenario = ScenarioUtils.loadScenario( config );
		return scenario;
	}
	
	public static Config prepareConfig(String configFile) {
		OutputDirectoryLogging.catchLogEntries();
		
		final Config config = ConfigUtils.loadConfig(configFile);
		
		config.controler().setRoutingAlgorithmType( FastAStarLandmarks );
		
		config.subtourModeChoice().setProbaForRandomSingleTripMode( 0.5 );
		
		config.plansCalcRoute().setRoutingRandomness( 3. );
		config.plansCalcRoute().removeModeRoutingParams(TransportMode.ride);
		config.plansCalcRoute().removeModeRoutingParams(TransportMode.pt);
		config.plansCalcRoute().removeModeRoutingParams("undefined");
	
		config.qsim().setInsertingWaitingVehiclesBeforeDrivingVehicles( true );
				
		// vsp defaults
		config.vspExperimental().setVspDefaultsCheckingLevel( VspExperimentalConfigGroup.VspDefaultsCheckingLevel.abort );
		config.plansCalcRoute().setInsertingAccessEgressWalk( true );
		config.qsim().setUsingTravelTimeCheckInTeleportation( true );
		config.qsim().setTrafficDynamics( TrafficDynamics.kinematicWaves );
				
		// activities:
		for ( long ii = 600 ; ii <= 97200; ii+=600 ) {
			config.planCalcScore().addActivityParams( new ActivityParams( "home_" + ii + ".0" ).setTypicalDuration( ii ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "work_" + ii + ".0" ).setTypicalDuration( ii ).setOpeningTime(6. * 3600. ).setClosingTime(20. * 3600. ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "leisure_" + ii + ".0" ).setTypicalDuration( ii ).setOpeningTime(9. * 3600. ).setClosingTime(27. * 3600. ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "shopping_" + ii + ".0" ).setTypicalDuration( ii ).setOpeningTime(8. * 3600. ).setClosingTime(20. * 3600. ) );
			config.planCalcScore().addActivityParams( new ActivityParams( "other_" + ii + ".0" ).setTypicalDuration( ii ) );
		}
		config.planCalcScore().addActivityParams( new ActivityParams( "freight" ).setTypicalDuration( 12.*3600. ) );
		
		config.planCalcScore().addActivityParams( new ActivityParams( "home" ).setTypicalDuration( 12.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "work" ).setTypicalDuration( 8.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "university" ).setTypicalDuration( 8.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "school" ).setTypicalDuration( 6.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "escort" ).setTypicalDuration( 1.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "schoolescort" ).setTypicalDuration( 1.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "schoolpureescort" ).setTypicalDuration( 1.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "schoolridesharing" ).setTypicalDuration( 1.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "non-schoolescort" ).setTypicalDuration( 1.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "shop" ).setTypicalDuration( 1.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "maintenance" ).setTypicalDuration( 1.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "HHmaintenance" ).setTypicalDuration( 1.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "personalmaintenance" ).setTypicalDuration( 1.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "eatout" ).setTypicalDuration( 1.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "eatoutbreakfast" ).setTypicalDuration( 1.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "eatoutlunch" ).setTypicalDuration( 1.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "eatoutdinner" ).setTypicalDuration( 1.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "visiting" ).setTypicalDuration( 1.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "discretionary" ).setTypicalDuration( 1.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "specialevent" ).setTypicalDuration( 1.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "atwork" ).setTypicalDuration( 1.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "atworkbusiness" ).setTypicalDuration( 1.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "atworklunch" ).setTypicalDuration( 1.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "atworkother" ).setTypicalDuration( 1.*3600. ) );
		config.planCalcScore().addActivityParams( new ActivityParams( "business" ).setTypicalDuration( 1.*3600. ) );

		return config ;
	}
	
}
