package plugins.fmp.multitools.experiment.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;
import java.lang.reflect.InvocationTargetException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import icy.system.thread.ThreadUtil;
import plugins.fmp.multitools.series.ProgressReporter;
import plugins.fmp.multitools.tools.JComponents.Dialog;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;
import plugins.fmp.multitools.tools.JComponents.exceptions.FileDialogException;
import plugins.fmp.multitools.transfer.TransferDirection;
import plugins.fmp.multitools.transfer.TransferMode;
import plugins.fmp.multitools.transfer.TransferPlan;
import plugins.fmp.multitools.transfer.TransferReport;
import plugins.fmp.multitools.transfer.TransferRunner;
import plugins.fmp.multitools.transfer.TransferScanner;

public class TransferResultsDialogPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private static boolean suppressIfNewerWarningThisSession = false;
	private static final Preferences PREFS = Preferences.userNodeForPackage(TransferResultsDialogPanel.class);
	private static final String PREF_OTHER_ROOT = "transferResults.otherRoot";
	private static final String PREF_DIRECTION = "transferResults.direction";
	private static final String PREF_MODE = "transferResults.mode";
	private static final String PREF_SCOPE_ALL = "transferResults.scopeAll";
	private static final String PREF_OVERRIDE_ENABLED = "transferResults.overrideEnabled";
	private static final String PREF_OVERRIDE_ROOT = "transferResults.overrideRoot";

	private final JComboBoxExperimentLazy experimentsCombo;
	private final TransferResultsHost host;

	private final JButton prepareButton = new JButton("Prepare transfer");
	private final JLabel scanSummaryLabel = new JLabel("No scan prepared.");
	private final JLabel warningLabel = new JLabel(" ");

	private final JTextField computedRootField = new JTextField();

	private final JCheckBox overrideRootCheck = new JCheckBox("Override common root");
	private final JTextField overrideRootField = new JTextField();
	private final JButton overrideBrowseButton = new JButton("Browse...");

	private final JRadioButton rbAllExperiments = new JRadioButton("all experiments", true);
	private final JRadioButton rbCurrentExperiment = new JRadioButton("current experiment", false);

	private final JComboBox<TransferDirection> directionCombo = new JComboBox<>(TransferDirection.values());
	private final JComboBox<TransferMode> modeCombo = new JComboBox<>(TransferMode.values());

	private final JLabel otherRootLabel = new JLabel("Destination root");
	private final JTextField otherRootField = new JTextField();
	private final JButton otherBrowseButton = new JButton("Browse...");

	private final JButton startButton = new JButton("Start");
	private final JCheckBox closeReloadBeforeImportCheck = new JCheckBox("Close and reload project before Import",
			true);
	private final JCheckBox importScanSourceTreesCheck = new JCheckBox("Import all files from source (scan source results)",
			false);

	private volatile TransferPlan plan = null;
	private volatile List<String> preparedExperimentXmls = new ArrayList<>();

	public TransferResultsDialogPanel(JComboBoxExperimentLazy experimentsCombo, TransferResultsHost host) {
		this.experimentsCombo = experimentsCombo;
		this.host = host;
		buildUi();
		wireUi();
	}

	private Window getParentWindow() {
		return SwingUtilities.getWindowAncestor(this);
	}

	private void buildUi() {
		setLayout(new BorderLayout());
		JPanel content = new JPanel(new GridLayout(8, 1));
		content.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		computedRootField.setEditable(false);
		overrideRootField.setEditable(false);

		startButton.setEnabled(false);
		warningLabel.setForeground(new Color(140, 90, 0));

		JPanel row0 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		row0.add(prepareButton);
		row0.add(scanSummaryLabel);
		content.add(row0);

		JPanel rowScope = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		rowScope.add(new JLabel("Scan scope:"));
		rowScope.add(rbAllExperiments);
		rowScope.add(rbCurrentExperiment);
		content.add(rowScope);

		JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		row1.add(new JLabel("Computed common root:"));
		computedRootField.setColumns(45);
		row1.add(computedRootField);
		content.add(row1);

		JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		row2.add(overrideRootCheck);
		overrideRootField.setColumns(40);
		row2.add(overrideRootField);
		row2.add(overrideBrowseButton);
		content.add(row2);

		JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		row3.add(new JLabel("Direction:"));
		row3.add(directionCombo);
		row3.add(new JLabel("Mode:"));
		row3.add(modeCombo);
		content.add(row3);

		JPanel row4 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		row4.add(otherRootLabel);
		otherRootField.setColumns(45);
		row4.add(otherRootField);
		row4.add(otherBrowseButton);
		content.add(row4);

		JPanel row5 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		row5.add(startButton);
		row5.add(closeReloadBeforeImportCheck);
		row5.add(importScanSourceTreesCheck);
		content.add(row5);

		JPanel row6 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		row6.add(warningLabel);
		content.add(row6);

		add(content, BorderLayout.NORTH);
	}

	private void wireUi() {
		overrideBrowseButton.setEnabled(false);

		javax.swing.ButtonGroup scopeGroup = new javax.swing.ButtonGroup();
		scopeGroup.add(rbAllExperiments);
		scopeGroup.add(rbCurrentExperiment);

		loadPrefs();

		prepareButton.addActionListener(e -> runPrepare());
		rbAllExperiments.addActionListener(e -> invalidatePreparedScan());
		rbCurrentExperiment.addActionListener(e -> invalidatePreparedScan());

		overrideRootCheck.addActionListener(e -> {
			boolean enabled = overrideRootCheck.isSelected();
			overrideBrowseButton.setEnabled(enabled);
			overrideRootField.setEditable(false);
			savePrefs();
			validateReadyToStart();
		});

		overrideBrowseButton.addActionListener(e -> {
			try {
				String base = (overrideRootField.getText() != null && !overrideRootField.getText().isBlank())
						? overrideRootField.getText()
						: computedRootField.getText();
				String selected = Dialog.selectDirectory(base);
				if (selected != null) {
					overrideRootField.setText(selected);
				}
			} catch (FileDialogException ex) {
				ex.printStackTrace();
			}
			savePrefs();
			validateReadyToStart();
		});

		directionCombo.addActionListener(e -> {
			updateOtherRootLabel();
			savePrefs();
			validateReadyToStart();
		});

		modeCombo.addActionListener(e -> {
			savePrefs();
			validateReadyToStart();
		});

		otherBrowseButton.addActionListener(e -> {
			try {
				String base = (otherRootField.getText() != null && !otherRootField.getText().isBlank())
						? otherRootField.getText()
						: computedRootField.getText();
				String selected = Dialog.selectDirectory(base);
				if (selected != null) {
					otherRootField.setText(selected);
				}
			} catch (FileDialogException ex) {
				ex.printStackTrace();
			}
			savePrefs();
			validateReadyToStart();
		});

		startButton.addActionListener(e -> runTransfer());
		otherRootField.addActionListener(e -> {
			savePrefs();
			validateReadyToStart();
		});
	}

	private void updateOtherRootLabel() {
		TransferDirection dir = (TransferDirection) directionCombo.getSelectedItem();
		if (dir == TransferDirection.IMPORT) {
			otherRootLabel.setText("Source root");
			closeReloadBeforeImportCheck.setEnabled(host != null);
			importScanSourceTreesCheck.setEnabled(true);
		} else {
			otherRootLabel.setText("Destination root");
			closeReloadBeforeImportCheck.setEnabled(false);
			importScanSourceTreesCheck.setEnabled(false);
		}
	}

	private void runPrepare() {
		startButton.setEnabled(false);
		warningLabel.setText("Scanning local results...");
		scanSummaryLabel.setText("Scanning...");
		computedRootField.setText("");
		overrideRootField.setText("");
		overrideRootCheck.setSelected(false);
		overrideBrowseButton.setEnabled(false);
		plan = null;
		preparedExperimentXmls = new ArrayList<>();

		ThreadUtil.bgRun(() -> {
			try {
				boolean scanAll = rbAllExperiments.isSelected();
				TransferPlan p = scanAll ? TransferScanner.scanAllExperimentsResults(experimentsCombo)
						: TransferScanner.scanSelectedExperimentResults(experimentsCombo);
				List<String> xmls = collectExperimentXmls(p);
				SwingUtilities.invokeLater(() -> onPrepared(p, xmls));
			} catch (Exception ex) {
				ex.printStackTrace();
				SwingUtilities.invokeLater(() -> {
					warningLabel.setForeground(Color.RED);
					warningLabel.setText("Scan failed: " + ex.getMessage());
					scanSummaryLabel.setText("Scan failed.");
				});
			}
		});
	}

	private void onPrepared(TransferPlan p, List<String> experimentXmls) {
		this.plan = p;
		this.preparedExperimentXmls = (experimentXmls != null) ? experimentXmls : new ArrayList<>();
		warningLabel.setForeground(new Color(140, 90, 0));

		int nFiles = (p != null && p.items != null) ? p.items.size() : 0;
		int nRoots = (p != null && p.resultsRoots != null) ? p.resultsRoots.size() : 0;

		if (p == null || p.localCommonRoot == null || nFiles == 0) {
			scanSummaryLabel.setText(String.format("Prepared: %d file(s) from %d results dir(s).", nFiles, nRoots));
			computedRootField.setText((p != null && p.localCommonRoot != null) ? p.localCommonRoot.toString() : "");
			warningLabel.setText("Nothing to transfer.");
			startButton.setEnabled(false);
			return;
		}

		scanSummaryLabel.setText(String.format("Prepared: %d file(s) from %d results dir(s).", nFiles, nRoots));
		computedRootField.setText(p.localCommonRoot.toString());

		int nameCount = p.localCommonRoot.getNameCount();
		if (nameCount < 2) {
			warningLabel.setText("Warning: common root seems very high (mixed projects?).");
		} else {
			warningLabel.setText(" ");
		}
		validateReadyToStart();
	}

	private void validateReadyToStart() {
		boolean ok = true;
		String msg = " ";
		Color msgColor = new Color(140, 90, 0);

		if (plan == null || plan.localCommonRoot == null || plan.items == null || plan.items.isEmpty()) {
			ok = false;
			msg = "Prepare transfer first (no files).";
		}

		String other = otherRootField.getText();
		if (ok && (other == null || other.isBlank())) {
			ok = false;
			msg = "Select a source/destination root.";
		}

		if (ok && overrideRootCheck.isSelected()) {
			String override = overrideRootField.getText();
			if (override == null || override.isBlank()) {
				ok = false;
				msg = "Select an override root (or disable override).";
			} else {
				Path overridePath = Paths.get(override).toAbsolutePath().normalize();
				List<Path> roots = plan.resultsRoots;
				for (Path r : roots) {
					if (!TransferScanner.isAncestorOrEqual(overridePath, r)) {
						ok = false;
						msgColor = Color.RED;
						msg = "Override root must be a parent of all results directories.";
						break;
					}
				}
			}
		}

		startButton.setEnabled(ok);
		if (!" ".equals(msg)) {
			warningLabel.setForeground(msgColor);
			warningLabel.setText(msg);
		}
	}

	private Path getEffectiveCommonRoot() {
		if (plan == null)
			return null;
		if (overrideRootCheck.isSelected()) {
			String override = overrideRootField.getText();
			if (override != null && !override.isBlank()) {
				return Paths.get(override).toAbsolutePath().normalize();
			}
		}
		return plan.localCommonRoot;
	}

	private void runTransfer() {
		if (!startButton.isEnabled())
			return;

		TransferMode mode = (TransferMode) modeCombo.getSelectedItem();
		if (mode == TransferMode.IF_NEWER && !suppressIfNewerWarningThisSession) {
			IfNewerWarningDialog warning = new IfNewerWarningDialog(getParentWindow());
			warning.setVisible(true);
			if (warning.suppressForSession) {
				suppressIfNewerWarningThisSession = true;
			}
		}

		Path otherRoot = Paths.get(otherRootField.getText()).toAbsolutePath().normalize();
		TransferDirection direction = (TransferDirection) directionCombo.getSelectedItem();

		Path effectiveCommonRoot = getEffectiveCommonRoot();
		TransferPlan effectivePlan = new TransferPlan(effectiveCommonRoot, plan.resultsRoots, plan.items);

		savePrefs();
		startButton.setEnabled(false);

		TransferProgressDialog progressDialog = new TransferProgressDialog(getParentWindow());
		ThreadUtil.bgRun(() -> {
			List<String> xmls = preparedExperimentXmls != null ? preparedExperimentXmls : new ArrayList<>();

			if (direction == TransferDirection.IMPORT && closeReloadBeforeImportCheck.isSelected() && host != null) {
				runOnEdtAndWait(() -> host.closeAllExperimentsForTransfer());
				sleepQuietly(250);
			}

			boolean scanSourceTrees = direction == TransferDirection.IMPORT && importScanSourceTreesCheck.isSelected();
			TransferReport report = TransferRunner.run(effectivePlan, otherRoot, direction, mode, scanSourceTrees,
					progressDialog);

			if (direction == TransferDirection.IMPORT && closeReloadBeforeImportCheck.isSelected() && host != null) {
				sleepQuietly(150);
				runOnEdtAndWait(() -> host.reloadExperimentsFromExperimentXml(xmls));
			}

			SwingUtilities.invokeLater(() -> {
				progressDialog.dispose();
				showReport(report);
				startButton.setEnabled(false);
			});
		});
		progressDialog.setVisible(true);
	}

	private static void sleepQuietly(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ignored) {
		}
	}

	private static void runOnEdtAndWait(Runnable r) {
		if (r == null)
			return;
		if (SwingUtilities.isEventDispatchThread()) {
			r.run();
			return;
		}
		try {
			SwingUtilities.invokeAndWait(r);
		} catch (InvocationTargetException | InterruptedException e) {
			// Best-effort: continue even if the UI callback fails.
		}
	}

	private List<String> collectExperimentXmls(TransferPlan p) {
		ArrayList<String> xmls = new ArrayList<>();
		if (p == null || p.resultsRoots == null)
			return xmls;
		for (Path r : p.resultsRoots) {
			if (r == null)
				continue;
			Path xml = r.resolve("Experiment.xml");
			if (java.nio.file.Files.exists(xml)) {
				xmls.add(xml.toAbsolutePath().normalize().toString());
			}
		}
		return xmls;
	}

	private void invalidatePreparedScan() {
		plan = null;
		scanSummaryLabel.setText("No scan prepared.");
		computedRootField.setText("");
		startButton.setEnabled(false);
		savePrefs();
	}

	private void loadPrefs() {
		String other = PREFS.get(PREF_OTHER_ROOT, "");
		if (!other.isBlank())
			otherRootField.setText(other);

		String dir = PREFS.get(PREF_DIRECTION, TransferDirection.EXPORT.name());
		try {
			directionCombo.setSelectedItem(TransferDirection.valueOf(dir));
		} catch (Exception ignored) {
		}

		String mode = PREFS.get(PREF_MODE, TransferMode.OVERWRITE.name());
		try {
			modeCombo.setSelectedItem(TransferMode.valueOf(mode));
		} catch (Exception ignored) {
		}

		boolean scopeAll = PREFS.getBoolean(PREF_SCOPE_ALL, true);
		rbAllExperiments.setSelected(scopeAll);
		rbCurrentExperiment.setSelected(!scopeAll);

		boolean overrideEnabled = PREFS.getBoolean(PREF_OVERRIDE_ENABLED, false);
		overrideRootCheck.setSelected(overrideEnabled);
		overrideBrowseButton.setEnabled(overrideEnabled);
		String override = PREFS.get(PREF_OVERRIDE_ROOT, "");
		if (!override.isBlank())
			overrideRootField.setText(override);

		updateOtherRootLabel();
	}

	private void savePrefs() {
		PREFS.put(PREF_OTHER_ROOT, otherRootField.getText() != null ? otherRootField.getText().trim() : "");
		Object dir = directionCombo.getSelectedItem();
		if (dir instanceof TransferDirection)
			PREFS.put(PREF_DIRECTION, ((TransferDirection) dir).name());
		Object mode = modeCombo.getSelectedItem();
		if (mode instanceof TransferMode)
			PREFS.put(PREF_MODE, ((TransferMode) mode).name());
		PREFS.putBoolean(PREF_SCOPE_ALL, rbAllExperiments.isSelected());
		PREFS.putBoolean(PREF_OVERRIDE_ENABLED, overrideRootCheck.isSelected());
		PREFS.put(PREF_OVERRIDE_ROOT, overrideRootField.getText() != null ? overrideRootField.getText().trim() : "");
	}

	private void showReport(TransferReport report) {
		String text = formatReport(report);
		JDialog dlg = new JDialog(getParentWindow(), "Transfer report");
		dlg.setLayout(new BorderLayout());
		JTextArea area = new JTextArea(text);
		area.setEditable(false);
		area.setCaretPosition(0);
		dlg.add(new JScrollPane(area), BorderLayout.CENTER);
		JButton close = new JButton("Close");
		close.addActionListener(e -> dlg.dispose());
		JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		south.add(close);
		dlg.add(south, BorderLayout.SOUTH);
		dlg.setSize(900, 500);
		dlg.setLocationRelativeTo(getParentWindow());
		dlg.setModal(true);
		dlg.setVisible(true);
	}

	private String formatReport(TransferReport r) {
		StringBuilder sb = new StringBuilder();
		sb.append("Direction: ").append(r.direction).append("\n");
		sb.append("Mode: ").append(r.mode).append("\n");
		sb.append("Local common root: ").append(r.localCommonRoot).append("\n");
		sb.append("Other root: ").append(r.otherRoot).append("\n\n");
		sb.append("Total: ").append(r.totalItems).append("\n");
		sb.append("Copied: ").append(r.copied).append("\n");
		sb.append("Skipped existing: ").append(r.skippedExisting).append("\n");
		sb.append("Skipped not newer: ").append(r.skippedNotNewer).append("\n");
		if (r.direction == TransferDirection.IMPORT) {
			sb.append("Missing source: ").append(r.missingSource).append("\n");
			if (r.missingSourceRoots > 0) {
				sb.append("Missing source roots: ").append(r.missingSourceRoots).append("\n");
			}
		}
		sb.append("Locked destination (skipped): ").append(r.lockedDestination).append("\n");
		sb.append("Failed: ").append(r.failed).append("\n");
		sb.append("Elapsed: ").append(formatDuration(r.elapsed)).append("\n");

		if (r.failed > 0) {
			sb.append("\nFailures:\n");
			for (TransferReport.Failure f : r.getFailures()) {
				sb.append("- ").append(f.src).append(" -> ").append(f.dst).append("\n");
				sb.append("  ").append(f.message).append("\n");
			}
		}
		return sb.toString();
	}

	private static String formatDuration(Duration d) {
		if (d == null)
			return "0s";
		long s = d.getSeconds();
		long m = s / 60;
		long h = m / 60;
		s = s % 60;
		m = m % 60;
		if (h > 0)
			return String.format("%dh %dm %ds", h, m, s);
		if (m > 0)
			return String.format("%dm %ds", m, s);
		return String.format("%ds", s);
	}

	private static final class TransferProgressDialog extends JDialog implements ProgressReporter {
		private static final long serialVersionUID = 1L;

		private final AtomicBoolean cancelled = new AtomicBoolean(false);
		private final JLabel message = new JLabel("Starting...");
		private final JProgressBar bar = new JProgressBar(0, 100);

		TransferProgressDialog(Window parent) {
			super(parent, "Transfer in progress", ModalityType.APPLICATION_MODAL);
			setLayout(new BorderLayout());
			JPanel center = new JPanel(new GridLayout(2, 1));
			center.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
			center.add(message);
			center.add(bar);
			add(center, BorderLayout.CENTER);

			JButton cancel = new JButton("Cancel");
			cancel.addActionListener(e -> cancelled.set(true));
			JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
			south.add(cancel);
			add(south, BorderLayout.SOUTH);

			setSize(600, 140);
			setLocationRelativeTo(parent);
		}

		@Override
		public void updateMessage(String msg) {
			SwingUtilities.invokeLater(() -> message.setText(msg));
		}

		@Override
		public void updateProgress(int percentage) {
			SwingUtilities.invokeLater(() -> bar.setValue(Math.min(100, Math.max(0, percentage))));
		}

		@Override
		public void completed() {
		}

		@Override
		public void failed(String errorMessage) {
		}

		@Override
		public boolean isCancelled() {
			return cancelled.get();
		}
	}

	private static final class IfNewerWarningDialog extends JDialog {
		private static final long serialVersionUID = 1L;
		boolean suppressForSession = false;

		IfNewerWarningDialog(Window parent) {
			super(parent, "Warning: If newer", ModalityType.APPLICATION_MODAL);
			setLayout(new BorderLayout());

			String msg = "The \"If newer\" option relies on file timestamps.\n"
					+ "Across network shares / filesystems, timestamps may be unreliable.\n\n"
					+ "If unsure, prefer \"Overwrite\".\n";
			JTextArea area = new JTextArea(msg);
			area.setEditable(false);
			area.setBackground(getBackground());
			area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
			add(area, BorderLayout.CENTER);

			JCheckBox dontShow = new JCheckBox("Don't show again in this session");
			JButton ok = new JButton("OK");
			ok.addActionListener(e -> {
				suppressForSession = dontShow.isSelected();
				dispose();
			});

			JPanel south = new JPanel(new BorderLayout());
			south.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
			south.add(dontShow, BorderLayout.WEST);
			JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT));
			right.add(ok);
			south.add(right, BorderLayout.EAST);
			add(south, BorderLayout.SOUTH);

			setSize(520, 220);
			setLocationRelativeTo(parent);
		}
	}
}

