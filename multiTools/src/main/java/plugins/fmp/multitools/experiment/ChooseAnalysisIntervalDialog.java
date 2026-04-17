package plugins.fmp.multitools.experiment;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import plugins.fmp.multitools.experiment.BinDirectoryResolver.BinCandidate;

/**
 * Shared dialog that lets the user choose an "analysis interval"
 * (historically: a {@code results/bin_xxx} subdirectory) when several
 * candidates coexist and the program cannot decide unambiguously.
 * <p>
 * The wording avoids the internal term "bin" and presents each candidate as
 * a duration in seconds, annotated with evidence (has measures, timestamp).
 * The detected interval is pre-selected when it appears among the
 * candidates; otherwise the most recently modified candidate is preselected.
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

	/**
	 * Shows the chooser and blocks until the user decides.
	 *
	 * @param parent           parent component for the dialog
	 * @param candidates       candidate directories (never {@code null})
	 * @param detectedSec      detected interval in seconds (may be {@code null})
	 * @param lastUserChoice   last remembered choice (may be {@code null})
	 * @return the user's choice, or {@code null} if cancelled
	 */
	public static Result ask(Component parent, List<BinCandidate> candidates, Integer detectedSec,
			String lastUserChoice) {
		if (candidates == null || candidates.isEmpty())
			return null;

		JPanel panel = new JPanel(new BorderLayout(6, 6));
		panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

		String header = detectedSec != null
				? String.format("Detected interval from frames: %d s.", detectedSec.intValue())
				: "Could not detect interval from frames.";
		panel.add(new JLabel(header + " Choose which analysis interval to use:"), BorderLayout.NORTH);

		JPanel options = new JPanel(new GridBagLayout());
		GridBagConstraints gc = new GridBagConstraints();
		gc.gridx = 0;
		gc.gridy = 0;
		gc.anchor = GridBagConstraints.WEST;
		gc.insets = new Insets(2, 4, 2, 4);
		gc.fill = GridBagConstraints.HORIZONTAL;

		ButtonGroup group = new ButtonGroup();
		JRadioButton[] buttons = new JRadioButton[candidates.size()];
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");

		int preselect = choosePreselected(candidates, detectedSec, lastUserChoice);

		for (int i = 0; i < candidates.size(); i++) {
			BinCandidate c = candidates.get(i);
			StringBuilder label = new StringBuilder();
			label.append(c.seconds).append(" s");
			if (c.hasMeasures)
				label.append("  \u2713 measures");
			if (c.hasBinDescription)
				label.append("  \u00B7 description");
			if (c.lastModifiedMs > 0)
				label.append("  \u00B7 ").append(df.format(new Date(c.lastModifiedMs)));
			label.append("   [").append(c.name).append("]");
			JRadioButton rb = new JRadioButton(label.toString());
			group.add(rb);
			buttons[i] = rb;
			if (i == preselect)
				rb.setSelected(true);
			options.add(rb, gc);
			gc.gridy++;
		}

		panel.add(options, BorderLayout.CENTER);

		JCheckBox remember = new JCheckBox("Remember for this session (do not ask for remaining experiments)");
		remember.setSelected(true);
		panel.add(remember, BorderLayout.SOUTH);

		panel.setPreferredSize(new Dimension(Math.max(420, panel.getPreferredSize().width),
				panel.getPreferredSize().height));

		int choice = JOptionPane.showConfirmDialog(parent, panel, "Choose analysis interval",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
		if (choice != JOptionPane.OK_OPTION)
			return null;

		for (int i = 0; i < buttons.length; i++) {
			if (buttons[i].isSelected()) {
				return new Result(candidates.get(i).name, remember.isSelected());
			}
		}
		return null;
	}

	private static int choosePreselected(List<BinCandidate> candidates, Integer detectedSec, String lastUserChoice) {
		if (detectedSec != null) {
			int bestIdx = -1;
			int bestDelta = Integer.MAX_VALUE;
			for (int i = 0; i < candidates.size(); i++) {
				int d = Math.abs(candidates.get(i).seconds - detectedSec.intValue());
				if (d < bestDelta) {
					bestDelta = d;
					bestIdx = i;
				}
			}
			if (bestIdx >= 0 && bestDelta <= 1)
				return bestIdx;
		}
		if (lastUserChoice != null) {
			for (int i = 0; i < candidates.size(); i++) {
				if (lastUserChoice.equals(candidates.get(i).name))
					return i;
			}
		}
		// Fallback: most recently modified.
		int bestIdx = 0;
		long bestT = -1;
		for (int i = 0; i < candidates.size(); i++) {
			if (candidates.get(i).lastModifiedMs > bestT) {
				bestT = candidates.get(i).lastModifiedMs;
				bestIdx = i;
			}
		}
		return bestIdx;
	}
}
