package plugins.fmp.multitools.experiment.capillary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.Logger;

/**
 * Position label within a cage (L/R for two capillaries; last-character digit 0–9 otherwise). A
 * position change between two capillaries is a <strong>full ROI name swap</strong> (plus kymograph
 * stems, TIFF/XML on disk, {@link Capillary#syncDerivedNamesAfterRoiRename()} for AlongT copies
 * and prefix). The capillary table shows Side as read-only; use dedicated actions to swap.
 * <p>
 * Capillaries in a cage are ordered by {@link Capillary#getRoiName()} (lexicographic, nulls last).
 */
public final class CapillaryCagePositionSwap {

	private CapillaryCagePositionSwap() {
	}

	public static int countCapillariesInCage(List<Capillary> list, int cageId) {
		int n = 0;
		for (Capillary c : list) {
			if (c.getCageID() == cageId)
				n++;
		}
		return n;
	}

	public static List<Capillary> capillariesInCageSorted(List<Capillary> list, int cageId) {
		List<Capillary> out = new ArrayList<>();
		for (Capillary c : list) {
			if (c.getCageID() == cageId)
				out.add(c);
		}
		out.sort(Comparator.comparing(Capillary::getRoiName, Comparator.nullsLast(String::compareTo)));
		return out;
	}

	public static String positionLabel(Capillary c) {
		if (c == null)
			return "";
		return c.getCapillarySide();
	}

	public static boolean isLrPairCage(List<Capillary> list, int cageId) {
		return countCapillariesInCage(list, cageId) == 2;
	}

	public static String[] comboOptionsForCage(List<Capillary> list, int cageId) {
		if (countCapillariesInCage(list, cageId) < 2)
			return new String[0];
		if (isLrPairCage(list, cageId))
			return new String[] { "L", "R" };
		List<Capillary> caps = capillariesInCageSorted(list, cageId);
		String[] opts = new String[caps.size()];
		for (int i = 0; i < caps.size(); i++)
			opts[i] = positionLabel(caps.get(i));
		return opts;
	}

	/**
	 * @return row indices in {@code exp.getCapillaries().getList()} that were modified
	 */
	public static int[] applyPositionSelection(Experiment exp, int rowIndex, String newLabel) {
		Objects.requireNonNull(exp, "exp");
		if (newLabel == null || newLabel.isEmpty())
			return new int[0];
		List<Capillary> list = exp.getCapillaries().getList();
		if (rowIndex < 0 || rowIndex >= list.size())
			return new int[0];
		Capillary a = list.get(rowIndex);
		if (a.getRoi() == null)
			return new int[0];
		String current = positionLabel(a);
		if (current.equals(newLabel))
			return new int[0];
		int cageId = a.getCageID();
		Capillary b = findOtherCapillaryWithLabel(list, cageId, newLabel, a);
		if (b == null) {
			applyPositionLabelToCapillary(a, newLabel);
			tryRenameSingleCapillaryFiles(exp, a);
			exp.refreshAfterCapillaryRoiIdentityChange(a);
			return new int[] { rowIndex };
		}
		if (b.getRoi() == null)
			return new int[0];
		swapCapillaryPair(exp, a, b);
		int ia = list.indexOf(a);
		int ib = list.indexOf(b);
		if (ia < 0 || ib < 0)
			return new int[0];
		if (ia <= ib)
			return new int[] { ia, ib };
		return new int[] { ib, ia };
	}

	private static Capillary findOtherCapillaryWithLabel(List<Capillary> list, int cageId, String label,
			Capillary exclude) {
		for (Capillary c : list) {
			if (c.getCageID() != cageId || c == exclude)
				continue;
			if (positionLabel(c).equals(label))
				return c;
		}
		return null;
	}

	/** Exchanges complete ROI names (and kymograph file stems) between two capillaries. */
	private static void swapCapillaryPair(Experiment exp, Capillary a, Capillary b) {
		String roiA = a.getRoiName();
		String roiB = b.getRoiName();
		String stemA = kymographStem(a);
		String stemB = kymographStem(b);

		String bin = exp.getKymosBinFullDirectory();
		if (bin != null && stemA != null && stemB != null && !stemA.equals(stemB)) {
			try {
				swapStemsOnDisk(bin, stemA, stemB);
			} catch (IOException e) {
				Logger.error("CapillaryCagePositionSwap: failed to swap TIFF/XML stems " + stemA + " <-> " + stemB,
						e);
			}
		}

		setRoiNameSafe(a, roiB);
		setRoiNameSafe(b, roiA);
		exp.refreshAfterCapillaryRoiIdentityChange(a, b);
	}

	private static void setRoiNameSafe(Capillary cap, String name) {
		if (cap.getRoi() != null && name != null)
			cap.setRoiName(name);
	}

	private static void applyPositionLabelToCapillary(Capillary cap, String label) {
		if (cap.getRoi() == null)
			return;
		cap.setRoiName(replaceLastPositionChar(cap.getRoiName(), label));
	}

	private static void tryRenameSingleCapillaryFiles(Experiment exp, Capillary cap) {
		String oldStem = kymographStem(cap);
		String newStem = stemForRoiName(cap.getRoiName());
		if (oldStem == null || newStem == null || oldStem.equals(newStem))
			return;
		String bin = exp.getKymosBinFullDirectory();
		if (bin == null)
			return;
		try {
			moveStem(bin, oldStem, newStem);
		} catch (IOException e) {
			Logger.error("CapillaryCagePositionSwap: failed to rename stem " + oldStem + " -> " + newStem, e);
		}
	}

	static String replaceLastPositionChar(String roiName, String oneCharLabel) {
		if (oneCharLabel == null || oneCharLabel.length() != 1)
			return roiName;
		if (roiName == null || roiName.isEmpty())
			return oneCharLabel;
		return roiName.substring(0, roiName.length() - 1) + oneCharLabel;
	}

	static String kymographStem(Capillary cap) {
		String k = cap.getKymographName();
		if (k != null && !k.isEmpty())
			return k;
		return stemForRoiName(cap.getRoiName());
	}

	static String stemForRoiName(String roiName) {
		if (roiName == null || roiName.isEmpty())
			return null;
		return Capillary.replace_LR_with_12(roiName);
	}

	static void swapStemsOnDisk(String binDirectory, String stem1, String stem2) throws IOException {
		if (stem1.equals(stem2))
			return;
		String tmp = "_multicafe_swap_" + System.nanoTime();
		moveStem(binDirectory, stem1, tmp);
		moveStem(binDirectory, stem2, stem1);
		moveStem(binDirectory, tmp, stem2);
	}

	private static void moveStem(String binDirectory, String fromStem, String toStem) throws IOException {
		for (String ext : new String[] { ".tiff", ".tif", ".xml" }) {
			Path from = Paths.get(binDirectory, fromStem + ext);
			Path to = Paths.get(binDirectory, toStem + ext);
			if (Files.exists(from))
				Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** In-memory only (exchange / paste / duplicate column): copies full ROI name from source. */
	public static void copyPositionOnto(Capillary dest, Capillary source) {
		if (dest == null || source == null || dest.getRoi() == null || source.getRoi() == null)
			return;
		dest.setRoiName(source.getRoiName());
	}

	public static List<Integer> rowIndicesForCage(List<Capillary> list, int cageId) {
		List<Integer> idx = new ArrayList<>();
		for (Capillary c : capillariesInCageSorted(list, cageId)) {
			int i = list.indexOf(c);
			if (i >= 0)
				idx.add(i);
		}
		Collections.sort(idx);
		return idx;
	}
}
