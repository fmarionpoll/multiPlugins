package plugins.fmp.multitools.experiment.capillary.geometry;

import java.awt.geom.Line2D;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.tools.Logger;

/** Sidecar persistence for time-dependent blue physical geometry. */
public final class CapillaryPhaseGeometryPersistence {
	public static final String FILE_NAME = "CapillaryPhaseGeometry.csv";

	private CapillaryPhaseGeometryPersistence() {}

	public static boolean load(Capillaries capillaries, String resultsDirectory) {
		if (capillaries == null || resultsDirectory == null)
			return false;
		for (Capillary capillary : capillaries.getList())
			capillary.getPhaseGeometry().clear();
		Path path = Paths.get(resultsDirectory, FILE_NAME);
		if (!Files.isRegularFile(path))
			return true;
		Map<String, Capillary> byName = index(capillaries);
		try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isEmpty() || line.startsWith("#") || line.startsWith("type;"))
					continue;
				String[] f = line.split(";", -1);
				if (f.length < 5)
					continue;
				Capillary capillary = byName.get(decode(f[1]));
				if (capillary == null)
					continue;
				CapillaryPhaseGeometryModel model = capillary.getPhaseGeometry();
				if ("RATIO".equals(f[0]))
					model.setExtensions(new CorridorExtensionRatios(Double.parseDouble(f[2]), Double.parseDouble(f[3])));
				else if ("BLUE".equals(f[0]) && f.length >= 7)
					model.putBlue(Long.parseLong(f[2]), new Line2D.Double(Double.parseDouble(f[3]),
							Double.parseDouble(f[4]), Double.parseDouble(f[5]), Double.parseDouble(f[6])));
			}
			return true;
		} catch (Exception e) {
			Logger.warn("Cannot load capillary phase geometry from " + path + ": " + e.getMessage());
			return false;
		}
	}

	public static boolean save(Capillaries capillaries, String resultsDirectory) {
		if (capillaries == null || resultsDirectory == null)
			return false;
		Path path = Paths.get(resultsDirectory, FILE_NAME);
		try {
			Files.createDirectories(path.getParent());
			try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				writer.write("# multiCAFE coordinated capillary phase geometry v1\n");
				writer.write("type;capillary_base64;frame_or_upper;lower_or_x1;y1;x2;y2\n");
				for (Capillary capillary : capillaries.getList()) {
					CapillaryPhaseGeometryModel model = capillary.getPhaseGeometry();
					if (!model.isInitialized())
						continue;
					String name = encode(capillary.getRoiName() != null ? capillary.getRoiName() : capillary.getKymographName());
					writer.write("RATIO;" + name + ";" + model.getExtensions().getUpper() + ";"
							+ model.getExtensions().getLower() + ";;;\n");
					for (Map.Entry<Long, Line2D> entry : model.getBlueKeyframes().entrySet()) {
						Line2D blue = entry.getValue();
						writer.write("BLUE;" + name + ";" + entry.getKey() + ";" + blue.getX1() + ";"
								+ blue.getY1() + ";" + blue.getX2() + ";" + blue.getY2() + "\n");
					}
				}
			}
			return true;
		} catch (Exception e) {
			Logger.warn("Cannot save capillary phase geometry to " + path + ": " + e.getMessage());
			return false;
		}
	}

	private static Map<String, Capillary> index(Capillaries capillaries) {
		Map<String, Capillary> result = new HashMap<String, Capillary>();
		for (Capillary capillary : capillaries.getList()) {
			if (capillary.getRoiName() != null)
				result.put(capillary.getRoiName(), capillary);
			if (capillary.getKymographName() != null)
				result.put(capillary.getKymographName(), capillary);
		}
		return result;
	}

	private static String encode(String text) {
		return Base64.getEncoder().encodeToString((text != null ? text : "").getBytes(StandardCharsets.UTF_8));
	}

	private static String decode(String text) {
		return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
	}
}
