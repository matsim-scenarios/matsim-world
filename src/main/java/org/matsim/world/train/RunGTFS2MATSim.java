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

		runScenario(scenario);

		
	}

	private static Scenario createScenario() {
		Scenario scenario = new CreatePtScheduleAndVehiclesFromGtfs().run(inputGTFSFile);
		
//		sets link speeds to an average speed of all train trips that travel on this link
//		setLinkSpeedsToAverage(scenario);
		
//		sets link speeds to the maximum speed of all train trips that travel on this link
//		this should insure, that no trips are late, some may, however, be early
		setLinkSpeedsToMax(scenario);

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

		controler.run();

	}
	
	private static void setLinkSpeedsToAverage(Scenario scenario) {
		Map<Id<Link>, Double> linkSpeedSums = new HashMap<>();
		Map<Id<Link>, Double> linkSpeedNumbers = new HashMap<>();
		
		for (Link link : scenario.getNetwork().getLinks().values()) {
			linkSpeedSums.put(link.getId(), 0.);
			linkSpeedNumbers.put(link.getId(), 0.);
		}
		
		
		
		for (TransitLine line : scenario.getTransitSchedule().getTransitLines().values()) {
			for (TransitRoute transitRoute : line.getRoutes().values()) {
				double arrivalTime = 0;
				double departureTime = 0;
				for (int ii = 0; ii < transitRoute.getStops().size(); ii++) {
					
					arrivalTime = transitRoute.getStops().get(ii).getArrivalOffset();
					Id<Link> linkId = null;
					if (ii == 0) {
						linkId = transitRoute.getRoute().getStartLinkId();
						linkSpeedSums.replace(linkId, 50.);
						linkSpeedNumbers.replace(linkId, 1.);

					}
					
					else {
						
						if (ii == transitRoute.getStops().size()-1) {
							linkId = transitRoute.getRoute().getEndLinkId();
						}
						
						else {
							linkId = transitRoute.getRoute().getLinkIds().get(ii-1);
						}
						
						Double speedSum = linkSpeedSums.get(linkId);
						speedSum += scenario.getNetwork().getLinks().get(linkId).getLength() / (arrivalTime - departureTime);
						linkSpeedSums.replace(linkId, speedSum);
						
						Double speedNumber = linkSpeedNumbers.get(linkId);
						speedNumber += 1;
						linkSpeedNumbers.replace(linkId, speedNumber);
						
						
					}
					
					departureTime = transitRoute.getStops().get(ii).getDepartureOffset();
				}
				
				
			}
			
		}
		
		for (Link link : scenario.getNetwork().getLinks().values()) {
			double speed = linkSpeedSums.get(link.getId()) / linkSpeedNumbers.get(link.getId());
			link.setFreespeed(speed);
			if (speed>200./3.6) {
				log.warn("Link speed is higher than 200 km/h on link " + link.getId()+ " - Speed is " + Math.round(speed*3.6) + " km/h");
			}
			if (speed<30./3.6) {
				log.warn("Link speed is lower than 30 km/h on link " + link.getId()+ " - Speed is " + Math.round(speed*3.6) + " km/h");
			}
		}
		
	}
	
	private static void setLinkSpeedsToMax(Scenario scenario) {
		Map<Id<Link>, Double> linkMaxSpeed = new HashMap<>();
		
		
		for (Link link : scenario.getNetwork().getLinks().values()) {
			linkMaxSpeed.put(link.getId(), 0.);
		}
		
		
		
		for (TransitLine line : scenario.getTransitSchedule().getTransitLines().values()) {
			for (TransitRoute transitRoute : line.getRoutes().values()) {
				double arrivalTime = 0;
				double departureTime = 0;
				for (int ii = 0; ii < transitRoute.getStops().size(); ii++) {
					
					arrivalTime = transitRoute.getStops().get(ii).getArrivalOffset();
					Id<Link> linkId = null;
					if (ii == 0) {
						linkId = transitRoute.getRoute().getStartLinkId();
						linkMaxSpeed.replace(linkId, 50.);
					}
					
					else {
						
						if (ii == transitRoute.getStops().size()-1) {
							linkId = transitRoute.getRoute().getEndLinkId();
						}
						
						else {
							linkId = transitRoute.getRoute().getLinkIds().get(ii-1);
						}
						
						Double prevSpeed = linkMaxSpeed.get(linkId);
						double newSpeed = scenario.getNetwork().getLinks().get(linkId).getLength() / (arrivalTime - departureTime);
						
						if(newSpeed > prevSpeed) {
							linkMaxSpeed.replace(linkId, newSpeed);
						}
						
					}
					
					departureTime = transitRoute.getStops().get(ii).getDepartureOffset();
				}
				
				
			}
			
		}
		
		for (Link link : scenario.getNetwork().getLinks().values()) {
			double speed = linkMaxSpeed.get(link.getId());
			link.setFreespeed(speed);
			if (speed>200./3.6) {
				log.warn("Link speed is higher than 200 km/h on link " + link.getId()+ " - Speed is " + Math.round(speed*3.6) + " km/h");
			}
			if (speed<30./3.6) {
				log.warn("Link speed is lower than 30 km/h on link " + link.getId()+ " - Speed is " + Math.round(speed*3.6) + " km/h");
			}
		}
		
	}

}
