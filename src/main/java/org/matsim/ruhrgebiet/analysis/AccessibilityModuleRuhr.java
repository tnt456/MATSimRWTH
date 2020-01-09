/* *********************************************************************** *
 * project: org.matsim.*                                                   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
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

package org.matsim.ruhrgebiet.analysis;

/**
 * @author dziemke
 */
/*
public final class AccessibilityModuleRuhr extends AbstractModule {
	private static final Logger LOG = Logger.getLogger(AccessibilityModuleRuhr.class);

	private List<FacilityDataExchangeInterface> facilityDataListeners = new ArrayList<>() ; 
	private ActivityFacilities measuringPoints;
	private Map<String, ActivityFacilities> additionalFacs = new TreeMap<>() ;
	private String activityType;
	private boolean pushing2Geoserver = false;
	private boolean createQGisOutput = false;
	private String crs;

	@Override
	public void install() {
		addControlerListenerBinding().toProvider(new Provider<ControlerListener>() {
			// yy not sure if this truly needs to be a provider.  kai, dec'16
			
			@Inject private Config config ;
			@Inject private Network network ;
			@Inject private Scenario scenario;

			@Inject (optional = true) PtMatrix ptMatrix = null; // Downstream code knows how to handle a null PtMatrix
			@Inject private Map<String,TravelDisutilityFactory> travelDisutilityFactories ;
			@Inject private Map<String,TravelTime> travelTimes ;
			
			@Inject TripRouter tripRouter ;
			
			@Override
			public ControlerListener get() {
				AccessibilityConfigGroup acg = ConfigUtils.addOrGetModule(scenario.getConfig(), AccessibilityConfigGroup.class);
				crs = acg.getOutputCrs() ;
				
				ActivityFacilities opportunities = AccessibilityUtils.collectActivityFacilitiesWithOptionOfType(scenario, activityType);
				Map<Id<ActivityFacility>, Geometry> measurePointGeometryMap;
				final BoundingBox boundingBox;
				
				int tileSize_m = acg.getTileSize();
				if (tileSize_m <= 0) {
					LOG.error("Tile Size needs to be assigned a value greater than zero.");
				}
				
				if (acg.getAreaOfAccessibilityComputation() == AreaOfAccesssibilityComputation.fromShapeFile) {
					Geometry boundary = GridUtils.getBoundary(acg.getShapeFileCellBasedAccessibility());
					Envelope envelope = boundary.getEnvelopeInternal();
					boundingBox = BoundingBox.createBoundingBox(envelope.getMinX(), envelope.getMinY(), envelope.getMaxX(), envelope.getMaxY());
					measuringPoints = GridUtils.createGridLayerByGridSizeByShapeFileV2(boundary, tileSize_m);
					LOG.info("Using shape file to determine the area for accessibility computation.");
					
				} else if (acg.getAreaOfAccessibilityComputation() == AreaOfAccesssibilityComputation.fromBoundingBox) {
					boundingBox = BoundingBox.createBoundingBox(acg.getBoundingBoxLeft(), acg.getBoundingBoxBottom(), acg.getBoundingBoxRight(), acg.getBoundingBoxTop());
					measuringPoints = GridUtils.createGridLayerByGridSizeByBoundingBoxV2(boundingBox, tileSize_m);
					LOG.info("Using custom bounding box to determine the area for accessibility computation.");
					
				} else if (acg.getAreaOfAccessibilityComputation() == AreaOfAccesssibilityComputation.fromBoundingBoxHexagons) {
					boundingBox = BoundingBox.createBoundingBox(acg.getBoundingBoxLeft(), acg.getBoundingBoxBottom(), acg.getBoundingBoxRight(), acg.getBoundingBoxTop());
					measuringPoints = GridUtils.createHexagonLayer(boundingBox, tileSize_m);
					LOG.info("Using custom bounding box to determine the area for accessibility computation.");
					
				} else if (acg.getAreaOfAccessibilityComputation() == AreaOfAccesssibilityComputation.fromFacilitiesFile) {
					boundingBox = BoundingBox.createBoundingBox(acg.getBoundingBoxLeft(), acg.getBoundingBoxBottom(), acg.getBoundingBoxRight(), acg.getBoundingBoxTop());
					Scenario measuringPointsSc = ScenarioUtils.createScenario(ConfigUtils.createConfig());
					String measuringPointsFile = ConfigUtils.addOrGetModule(config, AccessibilityConfigGroup.class ).getMeasuringPointsFile();
					new MatsimFacilitiesReader(measuringPointsSc).readFile(measuringPointsFile);
					measuringPoints = AccessibilityUtils.collectActivityFacilitiesWithOptionOfType(measuringPointsSc, null);
					LOG.info("Using measuring points from file: " + measuringPointsFile);
					
				} else if (acg.getAreaOfAccessibilityComputation() == AreaOfAccesssibilityComputation.fromFacilitiesObject) {
					boundingBox = BoundingBox.createBoundingBox(acg.getBoundingBoxLeft(), acg.getBoundingBoxBottom(), acg.getBoundingBoxRight(), acg.getBoundingBoxTop());
					measuringPoints = ConfigUtils.addOrGetModule(config, AccessibilityConfigGroup.class ).getMeasuringPointsFacilities();
					LOG.warn("Number of measuringPoints = " +  measuringPoints.getFacilities().size());
					if (measuringPoints == null) {
						throw new RuntimeException("Measuring points should have been set direclty if from-facilities-object mode is used.");
					}
					LOG.info("Using measuring points from facilities object.");
					
				} else { // This covers also the "fromNetwork" case
					LOG.info("Using the boundary of the network file to determine the area for accessibility computation.");
					LOG.warn("This can lead to memory issues when the network is large and/or the cell size is too fine!");
					boundingBox = BoundingBox.createBoundingBox(scenario.getNetwork());
					measuringPoints = GridUtils.createGridLayerByGridSizeByBoundingBoxV2(boundingBox, tileSize_m);
					LOG.info("Using the boundary of the network file to determine the area for accessibility computation.");
					LOG.warn("This can lead to memory issues when the network is large and/or the cell size is too fine!");
				}
				
				if (acg.getMeasurePointGeometryProvision() == MeasurePointGeometryProvision.fromShapeFile) {
					measurePointGeometryMap = acg.getMeasurePointGeometryMap();
				} else {
					measurePointGeometryMap = VoronoiGeometryUtils.buildMeasurePointGeometryMap(measuringPoints, boundingBox, tileSize_m);
				}
				AccessibilityUtils.assignAdditionalFacilitiesDataToMeasurePoint(measuringPoints, measurePointGeometryMap, additionalFacs);
				
				// TODO Need to find a stable way for multi-modal networks
				// AV stuff -------------------------------------------------------------
				TransportModeNetworkFilter filter = new TransportModeNetworkFilter(scenario.getNetwork());
				LOG.warn("Full network has " + network.getNodes().size() + " nodes.");
				Network carNetwork = NetworkUtils.createNetwork();
				Set<String> carModeSet = new HashSet<>();
				carModeSet.add("car");
				filter.filter(carNetwork, carModeSet);
				LOG.warn("Pure car network now has " + carNetwork.getNodes().size() + " nodes.");
				
				Network bikeNetwork = NetworkUtils.createNetwork();
				Set<String> bikeModeSet = new HashSet<>();
				bikeModeSet.add("car");
				filter.filter(bikeNetwork, bikeModeSet);
				LOG.warn("Pure bike network now has " + bikeNetwork.getNodes().size() + " nodes.");
				// End AV stuff -------------------------------------------------------------
				
//				AccessibilityCalculator accessibilityCalculator = new AccessibilityCalculator(scenario, measuringPoints, network);
				AccessibilityCalculator accessibilityCalculator = new AccessibilityCalculator(scenario, measuringPoints, bikeNetwork);
				for (Modes4Accessibility mode : acg.getIsComputingMode()) {
					AccessibilityContributionCalculator calculator;
					switch(mode) {
					case bike:
						final TravelTime bikeTravelTime = travelTimes.get(mode.name());
						Gbl.assertNotNull(bikeTravelTime);
						final TravelDisutilityFactory bikeTravelDisutilityFactory = travelDisutilityFactories.get(mode.name());
						LOG.warn("bikeTravelDisutilityFactory = " + bikeTravelDisutilityFactory);
						calculator = new NetworkModeAccessibilityExpContributionCalculator(bikeTravelTime, bikeTravelDisutilityFactory, scenario, bikeNetwork);
//						calculator = new ConstantSpeedAccessibilityExpContributionCalculator(mode.name(), config, network);
						break;
					case car: {
						final TravelTime carTravelTime = travelTimes.get(mode.name());
						Gbl.assertNotNull(carTravelTime);
						final TravelDisutilityFactory carTravelDisutilityFactory = travelDisutilityFactories.get(mode.name());
//						calculator = new NetworkModeAccessibilityExpContributionCalculator(carTravelTime, carTravelDisutilityFactory, scenario, network);
						calculator = new NetworkModeAccessibilityExpContributionCalculator(carTravelTime, carTravelDisutilityFactory, scenario, carNetwork);
						break; }
					case freespeed: {
						final TravelDisutilityFactory travelDisutilityFactory = travelDisutilityFactories.get(TransportMode.car);
						Gbl.assertNotNull(travelDisutilityFactory);
						calculator = new NetworkModeAccessibilityExpContributionCalculator(new FreeSpeedTravelTime(), travelDisutilityFactory, scenario, network);
//						calculator = new NetworkModeAccessibilityExpContributionCalculator(new FreeSpeedTravelTime(), travelDisutilityFactory, scenario, carNetwork);
						break; }
					case walk:
						calculator = new ConstantSpeedAccessibilityExpContributionCalculator(mode.name(), config, network);
						break;
					case matrixBasedPt:
						calculator = new LeastCostPathCalculatorAccessibilityContributionCalculator(
								config.planCalcScore(),	ptMatrix.asPathCalculator(config.planCalcScore()));
						break;
						//$CASES-OMITTED$
					default:
//						TravelTime timeCalculator = this.travelTimes.get( mode.toString() ) ;
//						TravelDisutility travelDisutility = this.travelDisutilityFactories.get(mode.toString()).createTravelDisutility(timeCalculator) ;
						calculator = new TripRouterAccessibilityContributionCalculator(mode.toString(), tripRouter, config.planCalcScore());
					}
					accessibilityCalculator.putAccessibilityContributionCalculator(mode.name(), calculator);
				}
				
				String outputDirectory = scenario.getConfig().controler().getOutputDirectory() + "/" + activityType;
				
				if (pushing2Geoserver || createQGisOutput) {
					if (measurePointGeometryMap == null) {
						throw new IllegalArgumentException("measure-point-to-geometry map must not be null if push to Geoserver is intended.");
					}
					Set <String> additionalFacInfo = additionalFacs.keySet();
					accessibilityCalculator.addFacilityDataExchangeListener(new GeoserverUpdater(crs,
							config.controler().getRunId() + "_" + activityType, measurePointGeometryMap, additionalFacInfo,
							outputDirectory, pushing2Geoserver, createQGisOutput));
				}				

				AccessibilityShutdownListenerV4 accessibilityShutdownListener = new AccessibilityShutdownListenerV4(accessibilityCalculator, 
						opportunities, outputDirectory, acg);
				
				for (ActivityFacilities fac : additionalFacs.values()) {
					accessibilityShutdownListener.addAdditionalFacilityData(fac);
				}
				
				for (FacilityDataExchangeInterface listener : facilityDataListeners) {
					accessibilityShutdownListener.addFacilityDataExchangeListener(listener);
				}
				
				return accessibilityShutdownListener;
			}
		});
	}
	
	public final void setPushing2Geoserver(boolean pushing2Geoserver) {
		this.pushing2Geoserver = pushing2Geoserver;
	}
	
	public final void setCreateQGisOutput(boolean createQGisOutput) {
		this.createQGisOutput = createQGisOutput;
	}
	
	public final void addFacilityDataExchangeListener(FacilityDataExchangeInterface listener) {
		this.facilityDataListeners.add(listener) ;
	}


 */
/**
 * Add additional facility data that will generate an additional column for each (x,y,t)-Entry. The facilities are aggregated to
 * the measurement points in downstream code.
 */
	/*
	public void addAdditionalFacilityData(ActivityFacilities facilities) { // TDO cleanu up this method
		if (facilities.getName() == null || facilities.getName().equals("")) {
			throw new RuntimeException("Cannot add unnamed facility containers here. A key is required to identify them.") ;
		}
		for (ActivityFacilities existingFacilities : this.additionalFacs.values()) {
			if (existingFacilities.getName().equals(facilities.getName())) {
				throw new RuntimeException("Additional facilities under the name of + " + facilities.getName() + 
						" already exist. Cannot add additional facilities under the same name twice.") ;
			}
		}
		this.additionalFacs.put(facilities.getName(), facilities);
	}

	void setConsideredActivityType(String activityType) {
		this.activityType = activityType ;
	}
}

	 */
