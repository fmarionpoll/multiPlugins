package plugins.fmp.multicafe.dlg.levels;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import icy.main.Icy;
import icy.sequence.Sequence;
import icy.swimmingPool.SwimmingObject;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.fmp_experiment.Experiment;
import plugins.fmp.multitools.fmp_tools.ImageKMeans;
import plugins.nherve.maskeditor.MaskEditor;
import plugins.nherve.toolbox.image.feature.region.SupportRegionException;
import plugins.nherve.toolbox.image.feature.signature.SignatureException;
import plugins.nherve.toolbox.image.mask.MaskException;
import plugins.nherve.toolbox.image.segmentation.Segmentation;
import plugins.nherve.toolbox.image.segmentation.SegmentationException;
import plugins.nherve.toolbox.image.toolboxes.ColorSpaceTools;

public class LevelsKMeans extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6031521157029550040L;

	private JCheckBox allKymosCheckBox = new JCheckBox("all kymographs", true);
	private String detectString = "  Detect ";
	private JButton detectButton = new JButton(detectString);
	private JCheckBox allSeriesCheckBox = new JCheckBox("ALL (current to last)", false);
	private JCheckBox leftCheckBox = new JCheckBox("L", true);
	private JCheckBox rightCheckBox = new JCheckBox("R", true);
	private JButton displayButton = new JButton("Display");

	private JComboBox<String> cbColorSpace = new JComboBox<String>(new String[] {
			ColorSpaceTools.COLOR_SPACES[ColorSpaceTools.RGB], ColorSpaceTools.COLOR_SPACES[ColorSpaceTools.RGB_TO_HSV],
			ColorSpaceTools.COLOR_SPACES[ColorSpaceTools.RGB_TO_H1H2H3] });
	private JSpinner tfNbCluster2 = new JSpinner(new SpinnerNumberModel(3, 1, 10, 1));
	private JSpinner tfNbIteration2 = new JSpinner(new SpinnerNumberModel(100, 1, 999, 1));
	private JSpinner tfStabCrit2 = new JSpinner(new SpinnerNumberModel(0.001, 0.001, 100., .1));
	private JCheckBox cbSendMaskDirectly = new JCheckBox("To editor");
	private Thread currentlyRunning;

	private MultiCAFE parent0 = null;

	// -----------------------------------------------------

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);

		JPanel panel0 = new JPanel(layoutLeft);
		((FlowLayout) panel0.getLayout()).setVgap(0);
		panel0.add(detectButton);
		panel0.add(allSeriesCheckBox);
		panel0.add(allKymosCheckBox);
		panel0.add(leftCheckBox);
		panel0.add(rightCheckBox);
		panel0.add(cbSendMaskDirectly);
		add(panel0);

		JPanel panel01 = new JPanel(layoutLeft);
		panel01.add(new JLabel("Color space"));
		panel01.add(cbColorSpace);
		panel01.add(new JLabel("Clusters"));
		panel01.add(tfNbCluster2);
		panel01.add(new JLabel("Iterations"));
		panel01.add(tfNbIteration2);
		panel01.add(displayButton);
		add(panel01);

		JPanel panel1 = new JPanel(layoutLeft);
		panel1.add(new JLabel("Stabilization"));
		panel1.add(tfStabCrit2);
		add(panel1);

		defineActionListeners();
		currentlyRunning = null;
		cbSendMaskDirectly.setSelected(false);

		// no detection yet
		detectButton.setEnabled(false);
		allSeriesCheckBox.setEnabled(false);
		allKymosCheckBox.setEnabled(false);
		leftCheckBox.setEnabled(false);
		rightCheckBox.setEnabled(false);
	}

	private void defineActionListeners() {
		displayButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null && (currentlyRunning == null)) {
					runKMeans(exp);
				}
			}
		});

	}

	private int getColorSpaceFromCombo() {
		int cs = ColorSpaceTools.RGB;
		switch (cbColorSpace.getSelectedIndex()) {
		case 1:
			cs = ColorSpaceTools.RGB_TO_HSV;
			break;
		case 2:
			cs = ColorSpaceTools.RGB_TO_H1H2H3;
			break;
		default:
			cs = ColorSpaceTools.RGB;
			break;
		}
		return cs;
	}

	private void runKMeans(Experiment exp) {
		displayButton.setEnabled(false);
		final int nbc2 = (int) tfNbCluster2.getValue();
		final int nbi2 = (int) tfNbIteration2.getValue();
		final double stab2 = (double) tfStabCrit2.getValue();
		final int cs = getColorSpaceFromCombo();

		currentlyRunning = new Thread() {
			@Override
			public void run() {
				try {
					final Sequence seq = exp.getSeqKymos().getSequence();
					final Segmentation segmentation = ImageKMeans.doClustering(seq, nbc2, nbi2, stab2, cs);
					if (cbSendMaskDirectly.isSelected())
						callMaskEditor(seq, segmentation);
					else
						callDirect(segmentation);
				} catch (SupportRegionException e1) {
					System.out.println(e1.getClass().getName() + " : " + e1.getMessage());
				} catch (SegmentationException e1) {
					System.out.println(e1.getClass().getName() + " : " + e1.getMessage());
				} catch (MaskException e1) {
					System.out.println(e1.getClass().getName() + " : " + e1.getMessage());
				} catch (NumberFormatException e) {
					System.out.println(e.getClass().getName() + " : " + e.getMessage());
				} catch (SignatureException e) {
					System.out.println(e.getClass().getName() + " : " + e.getMessage());
				}
			}
		};
		currentlyRunning.start();
	}

	void callMaskEditor(Sequence seq, Segmentation segmentation) {
		final MaskEditor maskEditorPlugin = MaskEditor.getRunningInstance(true);
		currentlyRunning = null;
		Runnable r = new Runnable() {
			@Override
			public void run() {
				maskEditorPlugin.setSegmentationForSequence(seq, segmentation);
				maskEditorPlugin.switchOpacityOn();
				displayButton.setEnabled(true);
			}
		};
		try {
			SwingUtilities.invokeAndWait(r);
		} catch (InvocationTargetException | InterruptedException e) {
			System.out.println(e.getClass().getName() + " : " + e.getMessage());
		}
	}

	void callDirect(Segmentation segmentation) {
		SwimmingObject result = new SwimmingObject(segmentation);
		Icy.getMainInterface().getSwimmingPool().add(result);
		currentlyRunning = null;
		Runnable r = new Runnable() {
			@Override
			public void run() {
				displayButton.setEnabled(true);
			}
		};
		try {
			SwingUtilities.invokeAndWait(r);
		} catch (InvocationTargetException | InterruptedException e) {
			// TODO Auto-generated catch block
			System.out.println(e.getClass().getName() + " : " + e.getMessage());
		}
	}
}
