/* *********************************************************************** *
 * project: org.matsim.*
 * RunAmenityReaderForNmbm.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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

package org.matsim.analysis;

import java.io.FileNotFoundException;
import java.util.Map;

import org.matsim.contrib.accessibility.osm.OsmKeys;
import org.matsim.contrib.accessibility.osm.OsmPoiReader;
import org.matsim.contrib.accessibility.utils.AccessibilityFacilityUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

/**
 * @author dziemke
 */
public class RunOsmPOIReaderRuhr {

	public static void main(String[] args) {		
		// Input and output
		String osmFile = "/Users/dominik/Bicycle/NEMO/osm/2019-05-08_essen_vicinity.osm";
		String facilityFile = "/Users/dominik/Bicycle/NEMO/facilities/2019-05-08_essen_vicinity.xml.gz";
		
		// Parameters
		String crs = "EPSG:25832";
		
		CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation("WGS84", crs);
		OsmPoiReader osmPoiReader = null;
		try {
			osmPoiReader = new OsmPoiReader(osmFile, ct);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
		
		// Broader mapping, e.g. supermarket -> shopping
//		Map<String, String> osmAmenityToMatsimTypeMap = AccessibilityFacilityUtils.buildOsmAmenityToMatsimTypeMapV2();
//		Map<String, String> osmShopToMatsimTypeMap = AccessibilityFacilityUtils.buildOsmShopToMatsimTypeMapV2();
		
		// Finer mapping, e.g. supermarket -> supermarket
		Map<String, String> osmAmenityToMatsimTypeMap = AccessibilityFacilityUtils.buildOsmAmenityToMatsimTypeMapV2FinerClassification();
		Map<String, String> osmShopToMatsimTypeMap = AccessibilityFacilityUtils.buildOsmShopToMatsimTypeMapV2FinerClassification();

		osmPoiReader.parseOsmFileAndAddFacilities(osmAmenityToMatsimTypeMap, OsmKeys.AMENITY);
		osmPoiReader.parseOsmFileAndAddFacilities(osmShopToMatsimTypeMap, OsmKeys.SHOP);
		osmPoiReader.writeFacilities(facilityFile);		
	}
}