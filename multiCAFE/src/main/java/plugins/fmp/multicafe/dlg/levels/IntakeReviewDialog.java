package plugins.fmp.multicafe.dlg.levels;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.util.List;
import java.util.Locale;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import plugins.fmp.multitools.service.CorrelationGulpPreview;
import plugins.fmp.multitools.service.IntakeDiagnostics;
import plugins.fmp.multitools.service.IntakeEpisodes;

/** Snapshot estimate for one interval. It does not change saved measures. */
public final class IntakeReviewDialog {
	private IntakeReviewDialog() {
	}

	static JDialog open(Component owner, String capillaryName, CorrelationGulpPreview preview,
			IntakeEpisodes.Detection detection, boolean control, int selectedFrame,
			List<IntakeDiagnostics.Warning> warnings) {
		JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(owner),
				"Review " + capillaryName + " - snapshot, estimates only", Dialog.ModalityType.MODELESS);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		JPanel content = new JPanel(new BorderLayout(4, 4));
		content.add(panel(preview, detection, control, selectedFrame, warnings), BorderLayout.CENTER);
		JButton close = new JButton("Close");
		close.addActionListener(e -> dialog.dispose());
		content.add(close, BorderLayout.SOUTH);
		dialog.setContentPane(content);
		dialog.setSize(800, 550);
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
		return dialog;
	}

	private static JPanel panel(CorrelationGulpPreview reviewed, IntakeEpisodes.Detection reviewedDetection,
			boolean reviewedControl, int selectedFrame, List<IntakeDiagnostics.Warning> warnings) {
		int n = reviewed.raw.length;
		int first = Math.max(1, selectedFrame), last = Math.min(n - 1, first + 2);
		IntakeDiagnostics.Warning nearest = null;
		int distance = Integer.MAX_VALUE;
		for (IntakeDiagnostics.Warning warning : warnings) {
			int delta = Math.max(0, Math.max(warning.first - selectedFrame, selectedFrame - warning.last));
			if (delta < distance) {
				distance = delta;
				nearest = warning;
			}
		}
		if (nearest != null) {
			first = Math.max(1, nearest.first);
			last = Math.max(first, nearest.last);
		}
		JSpinner from = new JSpinner(new SpinnerNumberModel(first, 1, n - 1, 1));
		JSpinner to = new JSpinner(new SpinnerNumberModel(last, 1, n - 1, 1));
		JTextArea details = new JTextArea(14, 65);
		details.setEditable(false);
		details.setLineWrap(true);
		details.setWrapStyleWord(true);
		Runnable estimate = () -> {
			int a = ((Number) from.getValue()).intValue(), b = ((Number) to.getValue()).intValue();
			try {
				IntakeDiagnostics.Estimate result = IntakeDiagnostics.estimate(reviewed.correctedLevels,
						reviewed.evaporation, reviewed.minutes, reviewed.motion.reliable, a, b);
				double alreadyDetected = 0;
				for (int i = a; i <= b; i++)
					alreadyDetected += reviewedDetection.net[i];
				StringBuilder text = new StringBuilder();
				if (reviewedControl)
					text.append("NO-FLY CONTROL: excess loss is not labelled feeding.\n\n");
				text.append("Interval: frames ").append(a).append(" to ").append(b)
						.append(" (bin i represents the transition i-1 to i).\n").append("Before position: ")
						.append(format(result.before)).append(" px; after: ").append(format(result.after))
						.append(" px\nExpected evaporation: ").append(format(result.evaporation))
						.append(" px\nNet persistent displacement: ").append(format(result.net))
						.append(result.reliable ? " px\n\n" : " px (UNCERTAIN flanks)\n\n")
						.append(result.localReliable ? "Alternative using local flanking slopes: "
								: "UNCERTAIN alternative using local flanks: ")
						.append(format(result.localNet)).append(" px; local baseline rate ")
						.append(format(result.localRate)).append(" px/min. ")
						.append(result.localReliable ? "Flanking slopes are consistent."
								: "UNCERTAIN: local slopes are poorly supported or inconsistent.")
						.append("\n\nAlready detected net in interval: ").append(format(alreadyDetected))
						.append(" px\n").append("Unassigned difference: active-model estimate ")
						.append(format(result.net - alreadyDetected)).append(" px; local-flank alternative ")
						.append(format(result.localNet - alreadyDetected)).append(" px\n\n")
						.append("Flanking plateau samples: ").append(result.beforeSamples).append(" before, ")
						.append(result.afterSamples).append(" after. ")
						.append(result.reliable
								? "Flanking positions have usable level support (see source quality status)."
								: "UNCERTAIN: insufficient reliable flanking positions.")
						.append("\n\nEstimate only: no events, saved measures or consumption totals are changed. ")
						.append("Choose boundaries on stable meniscus stretches using the original image; neighbouring feeding, low contrast or evaporation mismatch can bias this estimate.\n\n");
				for (IntakeDiagnostics.Warning warning : warnings)
					if (warning.last >= a - 1 && warning.first <= b)
						text.append(warning.label()).append("; frames ").append(warning.first).append('-')
								.append(warning.last).append("; displacement ").append(format(warning.excess))
								.append(" px\n");
				details.setText(text.toString());
				details.setCaretPosition(0);
			} catch (IllegalArgumentException error) {
				details.setText(error.getMessage());
			}
		};
		from.addChangeListener(e -> estimate.run());
		to.addChangeListener(e -> estimate.run());
		JPanel bounds = new JPanel(new FlowLayout(FlowLayout.LEFT));
		bounds.add(new JLabel("First interval bin"));
		bounds.add(from);
		bounds.add(new JLabel("Last interval bin"));
		bounds.add(to);
		JPanel selection = new JPanel(new java.awt.GridLayout(0, 1));
		javax.swing.JComboBox<String> warningChoice = new javax.swing.JComboBox<>();
		warningChoice.addItem("Manual interval");
		for (IntakeDiagnostics.Warning warning : warnings)
			warningChoice.addItem(warning.label() + " [frames " + warning.first + "-" + warning.last + "]");
		warningChoice.addActionListener(e -> {
			int index = warningChoice.getSelectedIndex() - 1;
			if (index >= 0) {
				IntakeDiagnostics.Warning warning = warnings.get(index);
				from.setValue(Math.max(1, warning.first));
				to.setValue(Math.max(1, warning.last));
			}
		});
		if (nearest != null)
			warningChoice.setSelectedIndex(warnings.indexOf(nearest) + 1);
		selection.add(warningChoice);
		selection.add(bounds);
		JPanel content = new JPanel(new BorderLayout());
		content.add(selection, BorderLayout.NORTH);
		content.add(new JScrollPane(details), BorderLayout.CENTER);
		estimate.run();
		return content;
	}

	private static String format(double v) {
		return String.format(Locale.ROOT, "%.2f", v);
	}
}
