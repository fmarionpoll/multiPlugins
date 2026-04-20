package plugins.fmp.multitools.experiment;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import plugins.fmp.multitools.experiment.BinDirectoryResolver.BinCandidate;
import plugins.fmp.multitools.experiment.BinDirectoryResolver.EquivKey;

/**
 * Shared dialog that lets the user choose an "analysis interval"
 * (historically: a {@code results/bin_xxx} subdirectory) when several
 * candidates coexist.
 * <p>
 * Entries are grouped by compression-equivalence class and each entry is
 * annotated with the class info (effective interval per sample, subsample
 * factor and generation mode) rather than just a raw integer. When several
 * {@code bin_NN} directories share the same class, they are presented as a
 * single row with the list of on-disk names, which is enough to distinguish
 * the 60 s-jitter case ({@code bin_59} vs {@code bin_60}) from a genuine
 * subsampling change.
 * </p>
 */
public final class ChooseAnalysisIntervalDialog {

	private ChooseAnalysisIntervalDialog() {
	}

	public static final class Result {
		public final String chosenBinName;
		public final boolean rememberForSession;

		public Result(String chosenBinName, boolean rememberForSession) {
			this.chosenBinName = chosenBinName;
			this.rememberForSession = rememberForSession;
		}
	}

	public static Result ask(Component parent, List<BinCandidate> candidates, Integer detectedSec,
			String lastUserChoice) {
		if (candidates == null || candidates.isEmpty())
			return null;

		Map<EquivKey, List<BinCandidate>> groups = groupPreservingOrder(candidates);
		List<Entry> primary = new ArrayList<>();
		List<Entry> secondary = new ArrayList<>();
		partitionGroups(groups, detectedSec, primary, secondary);

		JPanel panel = new JPanel(new BorderLayout(6, 6));
		panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

		String header = detectedSec != null
				? String.format("Detected interval from frames: %d s.", detectedSec.intValue())
				: "Could not detect interval from frames.";
		panel.add(new JLabel(header + " Choose which analysis interval to use:"), BorderLayout.NORTH);

		final JPanel options = new JPanel(new GridBagLayout());
		final GridBagConstraints gc = new GridBagConstraints();
		gc.gridx = 0;
		gc.gridy = 0;
		gc.anchor = GridBagConstraints.WEST;
		gc.insets = new Insets(2, 4, 2, 4);
		gc.fill = GridBagConstraints.HORIZONTAL;

		final ButtonGroup group = new ButtonGroup();
		final List<EntryButton> entryButtons = new ArrayList<>();

		int preselectIndex = buildEntryButtons(options, gc, group, entryButtons, primary, detectedSec, lastUserChoice);

		final JButton showOthers = new JButton("Show other classes (" + secondary.size() + ")");
		if (secondary.isEmpty())
			showOthers.setVisible(false);
		else {
			gc.gridy++;
			options.add(showOthers, gc);
		}

		showOthers.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				showOthers.setVisible(false);
				for (Entry entry : secondary) {
					EntryButton eb = addRow(options, gc, group, entry);
					entryButtons.add(eb);
				}
				options.revalidate();
				options.repaint();
				java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(options);
				if (w != null)
					w.pack();
			}
		});

		if (preselectIndex >= 0 && preselectIndex < entryButtons.size()) {
			entryButtons.get(preselectIndex).radio.setSelected(true);
		} else if (!entryButtons.isEmpty()) {
			entryButtons.get(0).radio.setSelected(true);
		}

		panel.add(options, BorderLayout.CENTER);

		JCheckBox remember = new JCheckBox("Remember for this session (do not ask for remaining experiments)");
		remember.setSelected(true);
		panel.add(remember, BorderLayout.SOUTH);

		panel.setPreferredSize(new Dimension(Math.max(520, panel.getPreferredSize().width),
				panel.getPreferredSize().height));

		int choice = JOptionPane.showConfirmDialog(parent, panel, "Choose analysis interval",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
		if (choice != JOptionPane.OK_OPTION)
			return null;

		for (EntryButton eb : entryButtons) {
			if (eb.radio.isSelected()) {
				return new Result(eb.entry.canonicalName(), remember.isSelected());
			}
		}
		return null;
	}

	// ---------------- grouping / partition ----------------

	private static Map<EquivKey, List<BinCandidate>> groupPreservingOrder(List<BinCandidate> candidates) {
		Map<EquivKey, List<BinCandidate>> map = new LinkedHashMap<>();
		for (BinCandidate c : candidates) {
			EquivKey k = BinDirectoryResolver.equivalenceKey(c);
			map.computeIfAbsent(k, kk -> new ArrayList<>()).add(c);
		}
		return map;
	}

	private static void partitionGroups(Map<EquivKey, List<BinCandidate>> groups, Integer detectedSec,
			List<Entry> primary, List<Entry> secondary) {
		for (Map.Entry<EquivKey, List<BinCandidate>> e : groups.entrySet()) {
			Entry entry = new Entry(e.getKey(), e.getValue());
			boolean isPrimary = detectedSec == null || e.getKey().cameraSec <= 0
					|| Math.abs(e.getKey().cameraSec - detectedSec.intValue()) <= 1;
			if (isPrimary)
				primary.add(entry);
			else
				secondary.add(entry);
		}
	}

	private static int buildEntryButtons(JPanel options, GridBagConstraints gc, ButtonGroup group,
			List<EntryButton> entryButtons, List<Entry> entries, Integer detectedSec, String lastUserChoice) {
		int preselect = -1;
		int bestDelta = Integer.MAX_VALUE;
		int idx = 0;
		for (Entry entry : entries) {
			EntryButton eb = addRow(options, gc, group, entry);
			entryButtons.add(eb);

			if (detectedSec != null) {
				int d = Math.abs(entry.representative().seconds - detectedSec.intValue());
				if (d < bestDelta) {
					bestDelta = d;
					preselect = idx;
				}
			}
			if (lastUserChoice != null && entry.containsName(lastUserChoice)) {
				preselect = idx;
			}
			idx++;
		}
		if (preselect < 0 && !entries.isEmpty())
			preselect = 0;
		return preselect;
	}

	private static EntryButton addRow(JPanel options, GridBagConstraints gc, ButtonGroup group, Entry entry) {
		JRadioButton rb = new JRadioButton(entry.label());
		group.add(rb);
		gc.gridy++;
		options.add(rb, gc);
		return new EntryButton(rb, entry);
	}

	// ---------------- entry model ----------------

	private static final class Entry {
		final EquivKey key;
		final List<BinCandidate> members;
		private static final SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");

		Entry(EquivKey key, List<BinCandidate> members) {
			this.key = key;
			this.members = members;
		}

		BinCandidate representative() {
			BinCandidate best = null;
			for (BinCandidate c : members) {
				if (best == null || (c.hasMeasures && !best.hasMeasures)
						|| (c.hasMeasures == best.hasMeasures && c.lastModifiedMs > best.lastModifiedMs)) {
					best = c;
				}
			}
			return best != null ? best : members.get(0);
		}

		String canonicalName() {
			return representative().name;
		}

		boolean containsName(String name) {
			if (name == null)
				return false;
			for (BinCandidate c : members) {
				if (name.equals(c.name))
					return true;
			}
			return false;
		}

		String label() {
			BinCandidate rep = representative();
			StringBuilder sb = new StringBuilder();
			long effectiveMs = rep.cameraIntervalMs > 0 ? rep.cameraIntervalMs * Math.max(1, rep.subsampleFactor)
					: rep.binKymoColMs;
			int effSec = (int) Math.round(effectiveMs / 1000.0);
			sb.append("~").append(effSec).append(" s/sample");
			if (rep.subsampleFactor > 0)
				sb.append(", factor ").append(rep.subsampleFactor).append("x");
			sb.append(", ").append(modeLabel(key.mode));
			boolean anyMeasures = false;
			boolean anyImages = false;
			long newest = 0;
			for (BinCandidate c : members) {
				anyMeasures = anyMeasures || c.hasMeasures;
				anyImages = anyImages || c.hasImages;
				newest = Math.max(newest, c.lastModifiedMs);
			}
			if (anyImages)
				sb.append("  \u2713 images");
			if (anyMeasures)
				sb.append("  \u2713 measures");
			if (!anyImages && !anyMeasures)
				sb.append("  (empty)");
			if (newest > 0)
				sb.append("  \u00B7 ").append(DF.format(new Date(newest)));
			sb.append("   [");
			for (int i = 0; i < members.size(); i++) {
				if (i > 0)
					sb.append(", ");
				sb.append(members.get(i).name);
			}
			sb.append("]");
			return sb.toString();
		}

		private static String modeLabel(GenerationMode mode) {
			if (mode == null)
				return "unknown mode";
			switch (mode) {
			case KYMOGRAPH:
				return "kymograph";
			case DIRECT_FROM_STACK:
				return "direct";
			default:
				return "unknown mode";
			}
		}
	}

	private static final class EntryButton {
		final JRadioButton radio;
		final Entry entry;

		EntryButton(JRadioButton radio, Entry entry) {
			this.radio = radio;
			this.entry = entry;
		}
	}
}
