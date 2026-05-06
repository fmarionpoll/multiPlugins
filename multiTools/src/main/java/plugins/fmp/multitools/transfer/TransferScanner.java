package plugins.fmp.multitools.transfer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;

public final class TransferScanner {
	private TransferScanner() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static TransferPlan scanAllExperimentsResults(JComboBoxExperimentLazy combo) throws IOException {
		return scanResults(combo, true);
	}

	public static TransferPlan scanSelectedExperimentResults(JComboBoxExperimentLazy combo) throws IOException {
		return scanResults(combo, false);
	}

	private static TransferPlan scanResults(JComboBoxExperimentLazy combo, boolean scanAllExperiments) throws IOException {
		Objects.requireNonNull(combo, "combo");

		List<Path> resultsRoots = new ArrayList<>();
		int n = combo.getItemCount();
		int i0 = 0;
		int i1 = n - 1;
		if (!scanAllExperiments) {
			int idx = combo.getSelectedIndex();
			if (idx < 0 || idx >= n) {
				return new TransferPlan(null, Collections.emptyList(), Collections.emptyList());
			}
			i0 = idx;
			i1 = idx;
		}

		for (int i = i0; i <= i1; i++) {
			Experiment exp = combo.getItemAtNoLoad(i);
			if (exp == null)
				continue;
			String resultsDir = exp.getResultsDirectory();
			if (resultsDir == null || resultsDir.isBlank())
				continue;
			Path root = Paths.get(resultsDir).toAbsolutePath().normalize();
			if (Files.isDirectory(root)) {
				resultsRoots.add(root);
			}
		}

		if (resultsRoots.isEmpty()) {
			return new TransferPlan(null, Collections.emptyList(), Collections.emptyList());
		}

		Path commonRoot = computeCommonRoot(resultsRoots);
		if (commonRoot == null) {
			return new TransferPlan(null, resultsRoots, Collections.emptyList());
		}

		List<TransferItem> items = new ArrayList<>();
		for (Path root : resultsRoots) {
			try (Stream<Path> stream = Files.walk(root)) {
				stream.filter(Files::isRegularFile).forEach(p -> {
					try {
						Path abs = p.toAbsolutePath().normalize();
						Path rel = commonRoot.relativize(abs);
						long size = Files.size(abs);
						FileTime mtime = Files.getLastModifiedTime(abs);
						items.add(new TransferItem(abs, rel, size, mtime));
					} catch (Exception ignored) {
						// Scan is best-effort; unreadable files will be reported later during copy.
					}
				});
			}
		}

		// Deterministic order: by relative path (then by absolute for stability)
		items.sort(Comparator.comparing((TransferItem it) -> it.rel.toString()).thenComparing(it -> it.src.toString()));

		return new TransferPlan(commonRoot, resultsRoots, items);
	}

	public static Path computeCommonRoot(List<Path> roots) {
		if (roots == null || roots.isEmpty())
			return null;
		Path prefix = roots.get(0);
		for (int i = 1; i < roots.size(); i++) {
			prefix = commonPrefix(prefix, roots.get(i));
			if (prefix == null)
				return null;
		}
		return prefix;
	}

	public static boolean isAncestorOrEqual(Path ancestor, Path child) {
		if (ancestor == null || child == null)
			return false;
		Path a = ancestor.toAbsolutePath().normalize();
		Path c = child.toAbsolutePath().normalize();
		return c.startsWith(a);
	}

	private static Path commonPrefix(Path a, Path b) {
		if (a == null || b == null)
			return null;
		Path pa = a.toAbsolutePath().normalize();
		Path pb = b.toAbsolutePath().normalize();

		if (pa.getRoot() != null && pb.getRoot() != null && !pa.getRoot().equals(pb.getRoot())) {
			// Different drive letters / filesystem roots -> no meaningful common root.
			return null;
		}

		int n = Math.min(pa.getNameCount(), pb.getNameCount());
		int i = 0;
		for (; i < n; i++) {
			if (!pa.getName(i).equals(pb.getName(i)))
				break;
		}
		Path root = pa.getRoot();
		if (root == null) {
			// Relative paths: build from names only.
			return (i == 0) ? null : pa.subpath(0, i);
		}
		if (i == 0) {
			return root;
		}
		return root.resolve(pa.subpath(0, i));
	}
}

