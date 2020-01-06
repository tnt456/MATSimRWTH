package org.matsim.ruhrgebiet.prepare;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.contrib.osm.networkReader.LinkProperties;
import org.matsim.contrib.osm.networkReader.SupersonicBicycleOsmNetworkReader;
import org.matsim.core.network.algorithms.MultimodalNetworkCleaner;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.core.utils.gis.ShapeFileReader;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class PrepareNetworkAndPt {

	private static final Path osmData = Paths.get("matsim/scenarios/countries/de/ruhrgebiet/ruhrgebiet-v1.1-1pct/original_data/osm/nordrhein-westfalen-2020-01-03.osm.pbf");
	private static final Path ruhrShape = Paths.get("matsim/scenarios/countries/de/ruhrgebiet/ruhrgebiet-v1.1-1pct/original_data/shapes/ruhrgebiet_boundary.shp");
	private static final Path gtfsData = Paths.get("matsim/scenarios/countries/de/ruhrgebiet/ruhrgebiet-v1.1-1pct/original_data/gtfs/2019_12_03_google_transit_verbundweit_inkl_spnv.zip");

	private static final Path outputNetwork = Paths.get("matsim/scenarios/countries/de/ruhrgebiet/ruhrgebiet-v1.1-1pct/input/ruhrgebiet-v1.1-network.xml.gz");

	// we use UTM-32 as coord system
	private static final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, "EPSG:25832");

	public static void main(String[] args) {

		var commandLineArgs = new CommandLineArgs();
		JCommander.newBuilder().addObject(commandLineArgs).build().parse(args);
		new PrepareNetworkAndPt().run(Paths.get(commandLineArgs.publicSvn));
	}

	private void run(Path publicSvn) {

		var geometries = ShapeFileReader.getAllFeatures(publicSvn.resolve(ruhrShape).toString()).stream()
				.map(feature -> (Geometry) feature.getDefaultGeometry())
				.collect(Collectors.toList());

		var network = new SupersonicBicycleOsmNetworkReader.Builder()
				.coordinateTransformation(transformation)
				.includeLinkAtCoordWithHierarchy((coord, level) -> isIncludeLink(coord, level, geometries))
				.preserveNodeWithId(this::isPreserveNode)
				.afterLinkCreated((link, tags, direction) -> addRideMode(link))
				.build()
				.read(publicSvn.resolve(osmData));

		var cleaner = new MultimodalNetworkCleaner(network);
		cleaner.run(Set.of(TransportMode.car));
		cleaner.run(Set.of(TransportMode.ride));
		cleaner.run(Set.of(TransportMode.bike));

		new NetworkWriter(network).write(publicSvn.resolve(outputNetwork).toString());

		//TODO: Add gtfs data parsing
		//TODO: Add list of node ids to be preserved from map matching
		//TODO: Add counts creation?
	}

	private boolean isIncludeLink(Coord coord, int level, Collection<Geometry> geometries) {
		// include all streets wich are motorways to secondary streets
		if (level <= LinkProperties.LEVEL_SECONDARY) return true;

		// within shape include all other streets bigger than tracks
		return level <= LinkProperties.LEVEL_LIVING_STREET && geometries.stream().anyMatch(geometry -> geometry.contains(MGC.coord2Point(coord)));
	}

	private boolean isPreserveNode(long id) {
		return false; //TODO implement this with a list of ids
	}

	private void addRideMode(Link link) {
		if (link.getAllowedModes().contains(TransportMode.car)) {
			var modes = new HashSet<>(link.getAllowedModes());
			modes.add(TransportMode.ride);
			link.setAllowedModes(modes);
		}
	}

	private static class CommandLineArgs {

		@Parameter(names = {"-publicSvn", "-ps"}, required = true)
		private String publicSvn = "";
	}
}
