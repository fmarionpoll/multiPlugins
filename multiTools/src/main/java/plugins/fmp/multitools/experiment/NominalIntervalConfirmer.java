package plugins.fmp.multitools.experiment;

import java.awt.Component;
import java.awt.GridLayout;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

public final class NominalIntervalConfirmer {

	private NominalIntervalConfirmer() {
	}

	private static boolean medianNominalAskedThisSession = false;
	private static Integer medianNominalChosenSec = null;

	/**
	 * Asks the user to confirm the nominal interval (bin directory). They can
	 * accept the suggested value (median) or enter a custom number of seconds (e.g.
	 * 20 for bin_20).
	 *
	 * @param parent       parent component for the dialog
	 * @param suggestedSec suggested nominal interval in seconds (e.g. from median)
	 * @return the chosen nominal interval in seconds, or null if the user cancelled
	 *         / declined
	 */
	public static Integer confirmUseMedianAsNominal(Component parent, int suggestedSec) {
		if (medianNominalAskedThisSession)
			return medianNominalChosenSec;
		SpinnerNumberModel model = new SpinnerNumberModel(suggestedSec, 1, 9999, 1);
		JSpinner spinner = new JSpinner(model);
		spinner.setToolTipText("Nominal interval in seconds (e.g. 20 for bin_20)");

		JPanel panel = new JPanel(new GridLayout(0, 1));
		JPanel line1 = new JPanel(); // default FlowLayout LEFT
		line1.add(new JLabel(String.format("Median interval is %d s.", suggestedSec)));

		JPanel line2 = new JPanel();
		line2.add(new JLabel("Set nominal interval (seconds):"));
		line2.add(spinner);

		panel.add(line1);
		panel.add(line2);
		int choice = JOptionPane.showConfirmDialog(parent, panel, "Confirm nominal interval",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
		medianNominalAskedThisSession = true;
		if (choice == JOptionPane.OK_OPTION) {
			medianNominalChosenSec = ((Number) spinner.getValue()).intValue();
			return medianNominalChosenSec;
		}
		medianNominalChosenSec = null;
		return null;
	}

	public static boolean confirmNominalIfFarFromMedian(Component parent, int nominalSec, long medianMs,
			boolean hadPreviousNominal) {
		double medianSec = medianMs / 1000.0;
		double ratio = nominalSec / medianSec;
		if (ratio >= 0.1 && ratio <= 10)
			return true;
		int choice = JOptionPane.showConfirmDialog(parent,
				String.format("Nominal (%d s) differs significantly from median interval (%.1f s). Continue?",
						nominalSec, medianSec),
				"Confirm nominal interval", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		return choice == JOptionPane.YES_OPTION;
	}
}
