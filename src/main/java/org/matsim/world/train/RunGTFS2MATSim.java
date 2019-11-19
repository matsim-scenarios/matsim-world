/* *********************************************************************** *
 * project: org.matsim.*
 * EditRoutesTest.java
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


package org.matsim.world.train;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.Config;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.pt.transitSchedule.TransitScheduleWriterV2;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.vehicles.VehicleWriterV1;

/**
* @author smueller
*/

public class RunGTFS2MATSim {
	
	private static final Logger log = Logger.getLogger(RunGTFS2MATSim.class);
	private static final String inputGTFSFile = "../public-svn/matsim/scenarios/countries/world/db-fv-gtfs-master/2019.zip";
	private static final String outputDir = "../public-svn/matsim/scenarios/countries/world/MATSimFiles/2019/";
	
	public static void main(String[] args) {
		
		Scenario scenario = createScenario();
		setLinksSpeeds(scenario);
//		runScenario(scenario);

		
	}

	private static Scenario createScenario() {
		Scenario scenario = new CreatePtScheduleAndVehiclesFromGtfs().run(inputGTFSFile);
		
		log.info("writing transit schedule and vehicles");

		new VehicleWriterV1(scenario.getVehicles()).writeFile(outputDir+"GTFSTransitVehiclesDB.xml.gz");
		new TransitScheduleWriterV2(scenario.getTransitSchedule()).write(outputDir+"GTFSTransitScheduleDB.xml.gz");
		new NetworkWriter(scenario.getNetwork()).write(outputDir+"GTFSNetworkDB.xml.gz");
		
		log.info("Number transitVehicles in scenario: " + scenario.getTransitVehicles().getVehicles().size());
		log.info("Number of links in scenario: " + scenario.getNetwork().getLinks().size());
		return scenario;
	}
	
	private static void runScenario(Scenario scenario) {
		
		Config config = scenario.getConfig();
		
		config.controler().setOutputDirectory(outputDir+"/Run");
		config.controler().setLastIteration(0);
		config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);
		
		config.global().setNumberOfThreads(16);
		
		config.transit().setUseTransit(true);
		
//		set to a a low value to increase performance
		config.transitRouter().setMaxBeelineWalkConnectionDistance(1);
			
		Controler controler = new Controler(scenario);
		log.info("Number transitVehicles in scenario: " + scenario.getTransitVehicles().getVehicles().size());

		controler.run();

	}
	
	private static void setLinksSpeeds(Scenario scenario) {
		Map<Id<Link>, Double> linkSpeeds = new HashMap<>();
		Map<Id<Link>, Double> linkSpeeds2 = new HashMap<>();
		
		for (Link link : scenario.getNetwork().getLinks().values()) {
			linkSpeeds.put(link.getId(), 0.);
			linkSpeeds2.put(link.getId(), 0.);
		}
		
		
		
		for (TransitLine line : scenario.getTransitSchedule().getTransitLines().values()) {
			for (TransitRoute transitRoute : line.getRoutes().values()) {
				double arrivalTime = 0;
				double departureTime = 0;
				for (int ii = 0; ii < transitRoute.getStops().size(); ii++) {
					
					arrivalTime = transitRoute.getStops().get(ii).getArrivalOffset();
					if (ii == 0) {

					}
					
					else {
						Id<Link> linkId = null;
						if (ii == transitRoute.getStops().size()-1) {
							linkId = transitRoute.getRoute().getEndLinkId();
						}
						
						else {
							linkId = transitRoute.getRoute().getLinkIds().get(ii-1);
						}
						
						Double speedSum = linkSpeeds.get(linkId);
						speedSum += scenario.getNetwork().getLinks().get(linkId).getLength() / (arrivalTime - departureTime);
						linkSpeeds.replace(linkId, speedSum);
						
						Double speedNumber = linkSpeeds2.get(linkId);
						speedNumber += 1;
						linkSpeeds2.replace(linkId, speedNumber);
						
						
					}
					
					departureTime = transitRoute.getStops().get(ii).getDepartureOffset();
				}
				
				
			}
			
		}
		
//		for (Link link : scenario.getNetwork().getLinks().values()) {
//			double speed = linkSpeeds.get(link.getId()) / linkSpeeds2.get(link.getId());
//			System.out.println(speed+"	;"+speed*3.6+"	;"+linkSpeeds2.get(link.getId())+"	;"+linkSpeeds.get(link.getId())+"	;"+link.getId().toString()+"	;"+link.getLength());
//		}
		
	}

}
