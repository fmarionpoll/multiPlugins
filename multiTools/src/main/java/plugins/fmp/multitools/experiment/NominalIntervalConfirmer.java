package plugins.fmp.multitools.experiment;

import javax.swing.JOptionPane;
import java.awt.Component;

public final class NominalIntervalConfirmer {

	private NominalIntervalConfirmer() {}

	public static boolean confirmUseMedianAsNominal(Component parent, int suggestedSec) {
		int choice = JOptionPane.showConfirmDialog(parent,
				String.format("Use median interval (%d s) as nominal?", suggestedSec),
				"Confirm nominal interval", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		return choice == JOptionPane.YES_OPTION;
	}

	public static boolean confirmNominalIfFarFromMedian(Component parent, int nominalSec, long medianMs, boolean hadPreviousNominal) {
		double medianSec = medianMs / 1000.0;
		double ratio = nominalSec / medianSec;
		if (ratio >= 0.1 && ratio <= 10)
			return true;
		int choice = JOptionPane.showConfirmDialog(parent,
				String.format("Nominal (%d s) differs significantly from median interval (%.1f s). Continue?", nominalSec, medianSec),
				"Confirm nominal interval", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
		return choice == JOptionPane.YES_OPTION;
	}
}
