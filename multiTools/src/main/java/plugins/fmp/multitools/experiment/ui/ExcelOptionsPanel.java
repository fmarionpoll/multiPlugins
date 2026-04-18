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
 * Shared "Common options" panel used on the Excel export tab of both
 * multiCAFE and multiSPOTS96. Behaviour is parameterised through
 * {@link Features}: the three multiCAFE-only checkboxes (collate series,
 * pad intervals, dead=empty) are only rendered when the corresponding
 * feature flag is enabled.
 */
public class ExcelOptionsPanel extends JPanel {

	private static final long serialVersionUID = 1814896922714679663L;

	/**
	 * Per-plugin feature flags controlling which optional checkboxes are
	 * rendered. All flags default to {@code false}.
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

	private final JCheckBox exportAllFilesCheckBox = new JCheckBox("all experiments", true);
	private final JCheckBox transposeCheckBox = new JCheckBox("transpose", true);
	private final JCheckBox collateSeriesCheckBox = new JCheckBox("collate series", false);
	private final JCheckBox padIntervalsCheckBox = new JCheckBox("pad intervals", false);
	private final JCheckBox onlyAliveCheckBox = new JCheckBox("dead=empty", false);

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
		panel0.add(exportAllFilesCheckBox);
		panel0.add(transposeCheckBox);
		if (features.collateSeries)
			panel0.add(collateSeriesCheckBox);
		if (features.padIntervals) {
			panel0.add(padIntervalsCheckBox);
			padIntervalsCheckBox.setEnabled(false);
		}
		if (features.onlyAlive)
			panel0.add(onlyAliveCheckBox);
		add(panel0);

		JPanel panel1 = new JPanel(layout1);
		panel1.add(new JLabel("Analyze "));
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

		enableIntervalButtons(false);
		ButtonGroup group = new ButtonGroup();
		group.add(isFloatingFrameButton);
		group.add(isFixedFrameButton);

		defineActionListeners();
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
