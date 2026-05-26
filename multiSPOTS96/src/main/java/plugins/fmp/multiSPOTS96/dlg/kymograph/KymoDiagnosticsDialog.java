package plugins.fmp.multiSPOTS96.dlg.kymograph;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Window;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Modal dialog for kymograph diagnostic capture and on-image markers.
 */
public final class KymoDiagnosticsDialog {

	private KymoDiagnosticsDialog() {
	}

	public static void show(Component ownerComp, KymoDiagnosticsOptions options, Runnable afterApply) {
		if (options == null) {
			return;
		}
		Window owner = null;
		if (ownerComp != null) {
			owner = SwingUtilities.getWindowAncestor(ownerComp);
			if (owner == null && ownerComp instanceof Window) {
				owner = (Window) ownerComp;
			}
		}
		JDialog dlg = new JDialog(owner, "Kymograph diagnostics", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
		JCheckBox storePreGap = new JCheckBox(
				"Store pre-gap-fill fraction series when analyzing (extra memory per spot strip)",
				options.isIncludeDiagnosticsOnAnalyze());
		JCheckBox showColumns = new JCheckBox(
				"Show gap-filled columns on kymograph (needs diagnostics data; use View on Analysis tab)",
				options.isShowGapFillColumnsOnKymograph());
		storePreGap.setToolTipText(
				"When enabled, the next Analyze run keeps a copy of each fraction trace before bounded gap interpolation.");
		showColumns.setToolTipText(
				"Overlays cyan columns where gap-fill changed the fraction. Requires a successful Analyze with storage enabled.");

		JPanel center = new JPanel(new java.awt.GridLayout(0, 1, 0, 8));
		center.add(storePreGap);
		center.add(showColumns);

		JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		JButton ok = new JButton("OK");
		JButton cancel = new JButton("Cancel");
		south.add(cancel);
		south.add(ok);

		dlg.setLayout(new BorderLayout(8, 8));
		dlg.add(center, BorderLayout.CENTER);
		dlg.add(south, BorderLayout.SOUTH);

		cancel.addActionListener(e -> dlg.dispose());
		ok.addActionListener(e -> {
			options.setIncludeDiagnosticsOnAnalyze(storePreGap.isSelected());
			options.setShowGapFillColumnsOnKymograph(showColumns.isSelected());
			dlg.dispose();
			if (afterApply != null) {
				afterApply.run();
			}
		});

		dlg.pack();
		dlg.setLocationRelativeTo(ownerComp != null ? ownerComp : owner);
		dlg.setVisible(true);
	}
}
