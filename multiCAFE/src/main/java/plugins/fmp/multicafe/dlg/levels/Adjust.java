package plugins.fmp.multicafe.dlg.levels;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

import icy.util.StringUtil;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.capillary.Capillary;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.series.AdjustMeasuresToDimensions;
import plugins.fmp.multitools.series.ClipCageMeasuresToSmallest;
import plugins.fmp.multitools.series.CropMeasuresToDimensions;
import plugins.fmp.multitools.series.CurvesRestoreLength;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;

public class Adjust extends JPanel implements PropertyChangeListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2580935598417087197L;
	private MultiCAFE parent0;
	private JCheckBox allSeriesCheckBox = new JCheckBox("ALL series", false);

	private final String adjustString = "Resize levels to Kymographs";
	private final String cropString = "Crop levels to Kymograph";
	private final String clipString = "Clip levels npts to the shortest curve";
	private final String restoreString = "Restore levels";
	private final String stopString = "STOP ";

	private JButton adjustButton = new JButton(adjustString);
	private JButton restoreButton = new JButton(restoreString);
	private JButton clipButton = new JButton(clipString);
	private JButton cropButton = new JButton(cropString);

	private AdjustMeasuresToDimensions threadAdjust = null;
	private CurvesRestoreLength threadRestore = null;
	private ClipCageMeasuresToSmallest threadClip = null;
	private CropMeasuresToDimensions threadCrop = null;

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout layout = new FlowLayout(FlowLayout.LEFT);
		layout.setVgap(0);

		JPanel panel0 = new JPanel(layout);
		panel0.add(adjustButton);
		panel0.add(cropButton);
		add(panel0);

		JPanel panel1 = new JPanel(layout);
		panel1.add(clipButton);
		panel1.add(restoreButton);
		add(panel1);

		JPanel panel2 = new JPanel(layout);
		panel2.add(allSeriesCheckBox);
		add(panel2);

		defineListeners();
	}

	private void defineListeners() {
		adjustButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (adjustButton.getText().equals(adjustString))
					series_adjustDimensionsStart();
				else
					series_adjustDimensionsStop();
			}
		});

		cropButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (cropButton.getText().equals(cropString))
					series_cropDimensionsStart();
				else
					series_cropDimensionsStop();
			}
		});

		restoreButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (restoreButton.getText().equals(restoreString))
					series_restoreStart();
				else
					series_restoreStop();
			}
		});

		clipButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (restoreButton.getText().equals(restoreString))
					series_clipStart();
				else
					series_clipStop();
			}
		});

		allSeriesCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Color color = Color.BLACK;
				if (allSeriesCheckBox.isSelected())
					color = Color.RED;
				allSeriesCheckBox.setForeground(color);
				adjustButton.setForeground(color);
				clipButton.setForeground(color);
				restoreButton.setForeground(color);
				cropButton.setForeground(color);
			}
		});
	}

	void restoreClippedPoints(Experiment exp) {
		SequenceKymos seqKymos = exp.getSeqKymos();
		int t = seqKymos.getCurrentFrame();
		Capillary cap = exp.getCapillaries().getList().get(t);
		cap.restoreClippedMeasures();

		seqKymos.updateROIFromCapillaryMeasure(cap, cap.getTopLevel());
		seqKymos.updateROIFromCapillaryMeasure(cap, cap.getBottomLevel());
		seqKymos.updateROIFromCapillaryMeasure(cap, cap.getDerivative());
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (StringUtil.equals("thread_ended", evt.getPropertyName())) {
			if (adjustButton.getText().contains(stopString))
				adjustButton.setText(adjustString);
			else if (restoreButton.getText().contains(stopString))
				restoreButton.setText(restoreString);
			else if (clipButton.getText().contains(stopString))
				clipButton.setText(clipString);
			else if (cropButton.getText().contains(stopString))
				cropButton.setText(cropString);
		}
	}

	private void series_adjustDimensionsStop() {
		if (threadAdjust != null && !threadAdjust.stopFlag) {
			threadAdjust.stopFlag = true;
		}
	}

	private void series_cropDimensionsStop() {
		if (threadCrop != null && !threadCrop.stopFlag) {
			threadCrop.stopFlag = true;
		}
	}

	private void series_restoreStop() {
		if (threadRestore != null && !threadRestore.stopFlag) {
			threadRestore.stopFlag = true;
		}
	}

	private void series_clipStop() {
		if (threadClip != null && !threadClip.stopFlag) {
			threadClip.stopFlag = true;
		}
	}

	private boolean initBuildParameters(BuildSeriesOptions options) {
		int index = parent0.expListComboLazy.getSelectedIndex();
		Experiment exp = parent0.expListComboLazy.getItemAt(index);
		if (exp == null)
			return false;

		parent0.paneBrowse.panelLoadSave.closeViewsForCurrentExperiment(exp);
		options.expList = parent0.expListComboLazy;
		options.expList.index0 = parent0.expListComboLazy.getSelectedIndex();
		if (allSeriesCheckBox.isSelected())
			options.expList.index1 = parent0.expListComboLazy.getItemCount() - 1;
		else
			options.expList.index1 = options.expList.index0;

		options.isFrameFixed = parent0.paneExcel.tabCommonOptions.getIsFixedFrame();
		options.t_Ms_First = parent0.paneExcel.tabCommonOptions.getStartMs();
		options.t_Ms_Last = parent0.paneExcel.tabCommonOptions.getEndMs();
		options.t_Ms_BinDuration = parent0.paneExcel.tabCommonOptions.getBinMs();

		options.parent0Rect = parent0.mainFrame.getBoundsInternal();
		options.binSubDirectory = exp.getBinSubDirectory();
		return true;
	}

	private void series_adjustDimensionsStart() {
		threadAdjust = new AdjustMeasuresToDimensions();
		BuildSeriesOptions options = threadAdjust.options;
		if (initBuildParameters(options)) {
			threadAdjust.addPropertyChangeListener(this);
			threadAdjust.execute();
			adjustButton.setText(stopString + adjustString);
		}
	}

	private void series_cropDimensionsStart() {
		threadCrop = new CropMeasuresToDimensions();
		BuildSeriesOptions options = threadCrop.options;
		if (initBuildParameters(options)) {
			threadCrop.addPropertyChangeListener(this);
			threadCrop.execute();
			cropButton.setText(stopString + cropString);
		}
	}

	private void series_restoreStart() {
		threadRestore = new CurvesRestoreLength();
		BuildSeriesOptions options = threadRestore.options;
		if (initBuildParameters(options)) {
			threadRestore.addPropertyChangeListener(this);
			threadRestore.execute();
			restoreButton.setText(stopString + restoreString);
		}
	}

	private void series_clipStart() {
		threadClip = new ClipCageMeasuresToSmallest();
		BuildSeriesOptions options = threadClip.options;
		if (initBuildParameters(options)) {
			threadClip.addPropertyChangeListener(this);
			threadClip.execute();
			clipButton.setText(stopString + clipString);
		}
	}

}
