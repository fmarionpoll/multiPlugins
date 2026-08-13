package plugins.fmp.multitools.experiment.ui;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import plugins.fmp.multitools.tools.JComponents.JComboBoxMs;

/**
 * Shared "Common options" panel used on the Excel export tab of both multiCAFE
 * and multiSPOTS96. Behaviour is parameterised through {@link Features}: the
 * three multiCAFE-only checkboxes (collate series, pad intervals, dead=empty)
 * are only rendered when the corresponding feature flag is enabled.
 */
public class ExcelOptionsPanel extends JPanel {

	private static final long serialVersionUID = 1814896922714679663L;

	/**
	 * Per-plugin feature flags controlling which optional checkboxes are rendered.
	 * All flags default to {@code false}.
	 */
	public static final class Features {
		public boolean collateSeries = false;
		public boolean padIntervals = false;
		public boolean onlyAlive = false;

		public static Features cafeDefaults() {
			Features f = new Features();
			f.collateSeries = true;
			f.padIntervals = true;
			f.onlyAlive = true;
			return f;
		}

		public static Features spots96Defaults() {
			return new Features();
		}
	}

	private final Features features;

	private static final String TIP_EXPORT_ALL = "Export every experiment in the list, not only the selected one.";
	private static final String TIP_TRANSPOSE = "Swap rows and columns in the exported table layout.";
	private static final String TIP_COLLATE = "Merge consecutive series that share the same descriptors into one exported series.";
	private static final String TIP_PAD = "When collating, pad missing time intervals so series align on a common time grid.";
	private static final String TIP_DEAD_EMPTY = "Treat dead flies as empty cells in export instead of carrying last values.";
	private static final String TIP_LAYOUT_NORM = "CSV-oriented layout with separate measure tables (see force CSV bin grid).";
	private static final String TIP_LAYOUT_WIDE = "Classic wide Excel matrix (one sheet layout used historically).";
	private static final String TIP_FORCE_CSV_BIN = "CSV only: write measure_*_binN using the analysis interval with time-weighted resampling. Uncheck to export raw native times only.";
	private static final String TIP_ANALYZE_ALL = "Export the full experiment duration.";
	private static final String TIP_ANALYZE_FROM = "Limit export to a fixed time window (from / to).";

	private final JCheckBox exportAllFilesCheckBox = new JCheckBox("all experiments", true);
	private final JCheckBox transposeCheckBox = new JCheckBox("transpose", true);
	private final JCheckBox collateSeriesCheckBox = new JCheckBox("collate series", false);
	private final JCheckBox padIntervalsCheckBox = new JCheckBox("pad intervals", false);
	private final JCheckBox onlyAliveCheckBox = new JCheckBox("dead=empty", false);

	private final JRadioButton layoutNormalizedButton = new JRadioButton("normalized (CSV tables)", false);
	private final JRadioButton layoutWideButton = new JRadioButton("wide matrix (Excel)", true);
	private final JCheckBox forceCsvBinCheckBox = new JCheckBox("force CSV bin grid", true);

	private final JSpinner binSize = new JSpinner(new SpinnerNumberModel(1., 1., 1000., 1.));
	private final JComboBoxMs binUnit = new JComboBoxMs();

	private final JRadioButton isFloatingFrameButton = new JRadioButton("all", true);
	private final JRadioButton isFixedFrameButton = new JRadioButton("from ", false);

	private final JSpinner startJSpinner = new JSpinner(new SpinnerNumberModel(0., 0., 10000., 1.));
	private final JSpinner endJSpinner = new JSpinner(new SpinnerNumberModel(240., 1., 99999999., 1.));
	private final JComboBoxMs intervalsUnit = new JComboBoxMs();

	public ExcelOptionsPanel(Features features) {
		this.features = (features != null) ? features : new Features();
	}

	public void init(GridLayout capLayout) {
		setLayout(capLayout);

		FlowLayout layout1 = new FlowLayout(FlowLayout.LEFT);
		layout1.setVgap(0);

		JPanel panel0 = new JPanel(layout1);
		exportAllFilesCheckBox.setToolTipText(TIP_EXPORT_ALL);
		transposeCheckBox.setToolTipText(TIP_TRANSPOSE);
		panel0.add(exportAllFilesCheckBox);
		panel0.add(transposeCheckBox);
		if (features.collateSeries) {
			collateSeriesCheckBox.setToolTipText(TIP_COLLATE);
			panel0.add(collateSeriesCheckBox);
		}
		if (features.padIntervals) {
			padIntervalsCheckBox.setToolTipText(TIP_PAD);
			panel0.add(padIntervalsCheckBox);
			padIntervalsCheckBox.setEnabled(false);
		}
		if (features.onlyAlive) {
			onlyAliveCheckBox.setToolTipText(TIP_DEAD_EMPTY);
			panel0.add(onlyAliveCheckBox);
		}
		add(panel0);

		JPanel panel1 = new JPanel(layout1);
		panel1.add(new JLabel("Analyze "));
		isFloatingFrameButton.setToolTipText(TIP_ANALYZE_ALL);
		isFixedFrameButton.setToolTipText(TIP_ANALYZE_FROM);
		panel1.add(isFloatingFrameButton);
		panel1.add(isFixedFrameButton);
		panel1.add(startJSpinner);
		panel1.add(new JLabel(" to "));
		panel1.add(endJSpinner);
		panel1.add(intervalsUnit);
		intervalsUnit.setSelectedIndex(2);
		add(panel1);

		JPanel panel2 = new JPanel(layout1);
		panel2.add(new JLabel("analysis interval "));
		panel2.add(binSize);
		panel2.add(binUnit);
		binUnit.setSelectedIndex(2);
		add(panel2);

		JPanel panel3 = new JPanel(layout1);
		panel3.add(new JLabel("layout "));
		layoutWideButton.setToolTipText(TIP_LAYOUT_WIDE);
		layoutNormalizedButton.setToolTipText(TIP_LAYOUT_NORM);
		panel3.add(layoutWideButton);
		panel3.add(layoutNormalizedButton);
		forceCsvBinCheckBox.setToolTipText(TIP_FORCE_CSV_BIN);
		panel3.add(forceCsvBinCheckBox);
		forceCsvBinCheckBox.setEnabled(false);
		add(panel3);

		enableIntervalButtons(false);
		ButtonGroup group = new ButtonGroup();
		group.add(isFloatingFrameButton);
		group.add(isFixedFrameButton);

		ButtonGroup layoutGroup = new ButtonGroup();
		layoutGroup.add(layoutNormalizedButton);
		layoutGroup.add(layoutWideButton);

		defineActionListeners();
		updateForceCsvBinEnabled();
	}

	private void defineActionListeners() {
		if (features.collateSeries && features.padIntervals) {
			collateSeriesCheckBox.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent event) {
					padIntervalsCheckBox.setEnabled(collateSeriesCheckBox.isSelected());
				}
			});
		}

		ActionListener layoutListener = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				updateForceCsvBinEnabled();
			}
		};
		layoutNormalizedButton.addActionListener(layoutListener);
		layoutWideButton.addActionListener(layoutListener);

		isFixedFrameButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				enableIntervalButtons(true);
			}
		});

		isFloatingFrameButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				enableIntervalButtons(false);
			}
		});
	}

	private void updateForceCsvBinEnabled() {
		forceCsvBinCheckBox.setEnabled(layoutNormalizedButton.isSelected());
	}

	private void enableIntervalButtons(boolean isSelected) {
		startJSpinner.setEnabled(isSelected);
		endJSpinner.setEnabled(isSelected);
		intervalsUnit.setEnabled(isSelected);
	}

	public boolean isExportAllFiles() {
		return exportAllFilesCheckBox.isSelected();
	}

	public boolean isTranspose() {
		return transposeCheckBox.isSelected();
	}

	public boolean isExportLayoutNormalized() {
		return layoutNormalizedButton.isSelected();
	}

	public boolean isForceCsvBinGrid() {
		return layoutNormalizedButton.isSelected() && forceCsvBinCheckBox.isSelected();
	}

	public boolean isCollateSeries() {
		return features.collateSeries && collateSeriesCheckBox.isSelected();
	}

	public boolean isPadIntervals() {
		return features.padIntervals && padIntervalsCheckBox.isSelected();
	}

	public boolean isOnlyAlive() {
		return features.onlyAlive && onlyAliveCheckBox.isSelected();
	}

	public boolean getIsFixedFrame() {
		return isFixedFrameButton.isSelected();
	}

	public int getExcelBuildStep() {
		double binValue = (double) binSize.getValue();
		return (int) (binValue * binUnit.getMsUnitValue());
	}

	public int getBinUnitMs() {
		return binUnit.getMsUnitValue();
	}

	public long getStartAllMs() {
		return (long) (((double) startJSpinner.getValue()) * intervalsUnit.getMsUnitValue());
	}

	public long getEndAllMs() {
		return (long) (((double) endJSpinner.getValue()) * intervalsUnit.getMsUnitValue());
	}

	public long getStartMs() {
		return (long) ((double) startJSpinner.getValue() * binUnit.getMsUnitValue());
	}

	public long getEndMs() {
		return (long) ((double) endJSpinner.getValue() * binUnit.getMsUnitValue());
	}

	public long getBinMs() {
		return (long) ((double) binSize.getValue() * (double) binUnit.getMsUnitValue());
	}
}
