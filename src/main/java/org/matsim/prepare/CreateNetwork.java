/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
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

/**
 * 
 */
package org.matsim.prepare;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.OutputDirectoryLogging;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.algorithms.NetworkSimplifier;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.io.OsmNetworkReader;


/**
 * 
 * requires the following:
 * 
 * 1) extract LA region from SouthCalifornia file: 
 * osmosis --rb file=socal-latest-2019-09-22.osm.pbf  --bounding-box left=-118.66 bottom=33.44 right=-116.95 top=34.33 --wb file=/Users/ihab/Desktop/socal-latest-2019-09-22_extracted-LA.osm.pbf
 *
 * 2) filter LA region osm file (detailed network)
 * osmosis --rb file=socal-latest-2019-09-22_extracted-LA.osm.pbf --tf accept-ways highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,tertiary,motorway_junction,residential,unclassified,living_street --used-node --wb LA-region-2019-09-22_incl-residential-and-living-street.osm.pbf
 *
 * 3) filter south california osm file (coarse network)
 * osmosis --rb file=socal-latest-2019-09-22.osm.pbf --tf accept-ways highway=motorway,motorway_link,trunk,trunk_link,primary,primary_link,secondary_link,secondary,tertiary,motorway_junction --used-node --wb socal-2019-09-22_incl-tertiary.osm.pbf
 * 
 * 4) merge coarse and detailed network
 * osmosis --rb file=socal-2019-09-22_incl-tertiary.osm.pbf --rb LA-region-2019-09-22_incl-residential-and-living-street.osm.pbf --merge --wx socal-LA-network_2019-09-22.osm
 * 
 * @author ikaddoura
 *
 */
public class CreateNetwork {
	
	private final Logger log = Logger.getLogger(CreateNetwork.class);

	private final String INPUT_OSMFILE ;
	private final String outputDir;
	private final String networkCS ;

	private Network network = null;
	private String outnetworkPrefix ;
	
	public static void main(String[] args) {
		String rootDirectory = null;
		
		if (args.length == 1) {
			rootDirectory = args[0];
		} else {
			throw new RuntimeException("Please set the root directory (the directory above 'scag_model'). Aborting...");
		}
		
		if (!rootDirectory.endsWith("/")) rootDirectory = rootDirectory + "/";
		
		String osmfile = rootDirectory + "osm-data/socal-LA-network_2019-09-22.osm";
		
		String prefix = "scag-network_" + new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		String outDir = rootDirectory + "/matsim-input-files/network/";

//		String crs = "EPSG:4326";
		String crs = "EPSG:3310";
		CreateNetwork networkCreator = new CreateNetwork(osmfile, crs , outDir, prefix);

		boolean keepPaths = false;
		boolean clean = true;
		boolean simplify = false;
		
		networkCreator.createNetwork(keepPaths, simplify, clean);
		networkCreator.adjustNetwork();		
		networkCreator.writeNetwork();
	}
	
	private void adjustNetwork() {
		for (Link link : network.getLinks().values()) {
			if (link.getAllowedModes().contains("car")) {
				Set<String> modes = new HashSet<>();
				modes.add("car");
				modes.add("ride");
				link.setAllowedModes(modes );
			}
		}
		
		// TODO: further modifications?
	}

	public CreateNetwork(String inputOSMFile, String networkCoordinateSystem, String outputDir, String prefix) {
		this.INPUT_OSMFILE = inputOSMFile;
		this.networkCS = networkCoordinateSystem;
		this.outputDir = outputDir.endsWith("/")?outputDir:outputDir+"/";
		this.outnetworkPrefix = prefix;
				
		OutputDirectoryLogging.catchLogEntries();
		try {
			OutputDirectoryLogging.initLoggingWithOutputDirectory(outputDir);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		log.info("--- set the coordinate system for network to be created to " + this.networkCS + " ---");
	}

	public void writeNetwork(){
		String outNetwork = this.outputDir+outnetworkPrefix+"_network.xml.gz";
		log.info("Writing network to " + outNetwork);
		new NetworkWriter(network).write(outNetwork);
		log.info("... done.");
	}
	
	public void createNetwork(boolean keepPaths, boolean doSimplify, boolean doCleaning){
		CoordinateTransformation ct =
			 TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, networkCS);
		
		if(this.network == null) {
			Config config = ConfigUtils.createConfig();
			Scenario scenario = ScenarioUtils.createScenario(config);
			network = scenario.getNetwork();

			log.info("start parsing from osm file " + INPUT_OSMFILE);
	
			OsmNetworkReader networkReader = new OsmNetworkReader(network,ct, true, true);
						
			if (keepPaths) {
				networkReader.setKeepPaths(true);
			} else {
				networkReader.setKeepPaths(false);
			}

			networkReader.parse(INPUT_OSMFILE);
			log.info("finished parsing osm file");
		}	
		
		if (doSimplify){
			outnetworkPrefix += "_simplified";
			log.info("number of nodes before simplifying:" + network.getNodes().size());
			log.info("number of links before simplifying:" + network.getLinks().size());
			log.info("start simplifying the network");
			/*
			 * simplify network: merge links that are shorter than the given threshold
			 */

			NetworkSimplifier simp = new NetworkSimplifier();
			simp.setMergeLinkStats(false);
			simp.run(network);
			
			log.info("number of nodes after simplifying:" + network.getNodes().size());
			log.info("number of links after simplifying:" + network.getLinks().size());
		}
		
		if (doCleaning){
//			outnetworkPrefix += "_cleaned";
				/*
				 * Clean the Network. Cleaning means removing disconnected components, so that afterwards there is a route from every link
				 * to every other link. This may not be the case in the initial network converted from OpenStreetMap.
				 */
			log.info("number of nodes before cleaning:" + network.getNodes().size());
			log.info("number of links before cleaning:" + network.getLinks().size());
			log.info("attempt to clean the network");
			new NetworkCleaner().run(network);
		}
		
		log.info("number of nodes after cleaning:" + network.getNodes().size());
		log.info("number of links after cleaning:" + network.getLinks().size());
		
		log.info("checking if all count nodes are in the network..");		
	}
}
