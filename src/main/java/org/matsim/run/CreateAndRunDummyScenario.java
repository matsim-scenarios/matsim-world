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

import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ModeParams;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule.DefaultStrategy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.examples.ExamplesUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.ModeMappingForPassengersParameterSet;
import ch.sbb.matsim.routing.pt.raptor.RaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;

/**
 * @author nagel
 *
 */
public class CreateAndRunDummyScenario{
	
	private static final Logger log = Logger.getLogger(CreateAndRunDummyScenario.class);

	public static void main(String[] args) {
		if ( args.length==0 ) {
			args = new String [] { IOUtils.extendUrl(ExamplesUtils.getTestScenarioURL("pt-simple-lineswitch"), "config.xml").getPath() };
		} else {
			Gbl.assertIf( args[0] != null && !args[0].equals( "" ) );
		}
		Logger.getRoot().setLevel(Level.ALL); // otherwise even log.error() in AirplaneTrainSwitcherIndividualRaptorParametersForPerson is not printed out
		
		Config config = ConfigUtils.loadConfig( args ) ;
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
		config.strategy().setMaxAgentPlanMemorySize(0);
		
		config.strategy().clearStrategySettings();
		
		StrategySettings stratSetsReRoute = new StrategySettings();
		stratSetsReRoute.setStrategyName(DefaultStrategy.ReRoute);
		stratSetsReRoute.setWeight(1.0);
		
		config.strategy().addStrategySettings(stratSetsReRoute);
		
		Set<String> transitModes = new HashSet<>();
		transitModes.add(TransportMode.pt);
		transitModes.add(TransportMode.train);
		transitModes.add(TransportMode.airplane);
		config.transit().setTransitModes(transitModes);
		
		ModeParams scorePt = config.planCalcScore().getModes().get(TransportMode.pt);
		
		ModeParams scoreTrain = new ModeParams(TransportMode.train);
		scoreTrain.setConstant(scorePt.getConstant());
		scoreTrain.setDailyMonetaryConstant(scorePt.getDailyMonetaryConstant());
		scoreTrain.setDailyUtilityConstant(scorePt.getDailyUtilityConstant());
		scoreTrain.setMarginalUtilityOfDistance(scorePt.getMarginalUtilityOfDistance());
		scoreTrain.setMarginalUtilityOfTraveling(scorePt.getMarginalUtilityOfTraveling());
		scoreTrain.setMonetaryDistanceRate(scorePt.getMonetaryDistanceRate());
		config.planCalcScore().addModeParams(scoreTrain);
		
		ModeParams scoreAirplane = new ModeParams(TransportMode.airplane);
		scoreAirplane.setConstant(scorePt.getConstant());
		scoreAirplane.setDailyMonetaryConstant(scorePt.getDailyMonetaryConstant());
		scoreAirplane.setDailyUtilityConstant(scorePt.getDailyUtilityConstant());
		scoreAirplane.setMarginalUtilityOfDistance(scorePt.getMarginalUtilityOfDistance());
		scoreAirplane.setMarginalUtilityOfTraveling(scorePt.getMarginalUtilityOfTraveling());
		scoreAirplane.setMonetaryDistanceRate(scorePt.getMonetaryDistanceRate());
		config.planCalcScore().addModeParams(scoreAirplane);
		
		SwissRailRaptorConfigGroup srrConfig = new SwissRailRaptorConfigGroup();
		srrConfig.setUseModeMappingForPassengers(true);
		
		ModeMappingForPassengersParameterSet modeMappingTrain = new ModeMappingForPassengersParameterSet();
		modeMappingTrain.setPassengerMode(TransportMode.train);
		modeMappingTrain.setRouteMode(TransportMode.train);
		srrConfig.addModeMappingForPassengers(modeMappingTrain);
		
		ModeMappingForPassengersParameterSet modeMappingAirplane = new ModeMappingForPassengersParameterSet();
		modeMappingAirplane.setPassengerMode(TransportMode.airplane);
		modeMappingAirplane.setRouteMode(TransportMode.airplane);
		srrConfig.addModeMappingForPassengers(modeMappingAirplane);
		
		config.addModule(srrConfig);
		
		config.controler().setLastIteration(10);
		
		Scenario scenario = ScenarioUtils.loadScenario(config) ;
		
		// possibly modify scenario here
		scenario.getTransitSchedule().getTransitLines().values().forEach(tl -> tl.getRoutes().values().forEach(tr -> tr.setTransportMode(TransportMode.train)));
		
		TransitLine transitLineBlau = scenario.getTransitSchedule().getTransitLines().get(Id.create("blau", TransitLine.class));
		transitLineBlau.getRoutes().values().forEach(tr -> tr.setTransportMode(TransportMode.airplane));
		// ---
		
		// allow all transit modes on all links
		scenario.getNetwork().getLinks().values().forEach(l -> {
			Set<String> modes = new HashSet<>();
			modes.add(TransportMode.car); // let XY2Links give a car only network with some links in it,otherwise givrs exceptions
			modes.addAll(l.getAllowedModes());
			modes.addAll(transitModes);
			l.setAllowedModes(modes);
		});

//		scenario.getPopulation().getPersons().values().forEach(p -> p.getPlans().forEach(pl -> pl.getPlanElements().forEach(pe -> {
//			if (pe instanceof Leg) {
//				Leg leg = (Leg) pe;
//				if (leg.getMode().equals(TransportMode.pt)) {
//					leg.setMode(TransportMode.airplane);
//				}
//			}
//		})));
		
		
		
		Controler controler = new Controler( scenario ) ;
		
		// possibly modify controler here

//		controler.addOverridingModule( new OTFVisLiveModule() ) ;
		controler.addOverridingModule(new SwissRailRaptorModule());
		controler.addOverridingModule(new AbstractModule() {
			
			@Override
			public void install() {
				// TODO Auto-generated method stub
				bind(RaptorParametersForPerson.class).to(AirplaneTrainSwitcherIndividualRaptorParametersForPerson.class);
			}
		});
		// ---
		
		controler.run();
	}
	
}
