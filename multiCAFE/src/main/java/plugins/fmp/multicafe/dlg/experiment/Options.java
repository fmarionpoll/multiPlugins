package plugins.fmp.multicafe.dlg.experiment;

import java.awt.ComponentOrientation;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import icy.canvas.IcyCanvas;
import icy.canvas.Layer;
import icy.gui.viewer.Viewer;
import icy.roi.ROI;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.fmp_experiment.Experiment;

public class Options extends JPanel {
	private static final long serialVersionUID = 6565346204580890307L;

	JCheckBox kymographsCheckBox = new JCheckBox("kymos", true);
	JCheckBox cagesCheckBox = new JCheckBox("cages", true);
	JCheckBox measuresCheckBox = new JCheckBox("measures", true);
	public JCheckBox graphsCheckBox = new JCheckBox("graphs", true);

	public JCheckBox viewCapillariesCheckBox = new JCheckBox("capillaries", true);
	public JCheckBox viewCellsCheckbox = new JCheckBox("cages", true);
	JCheckBox viewFlyCheckbox = new JCheckBox("flies center", false);
	JCheckBox viewFlyRectCheckbox = new JCheckBox("flies rect", false);
	private MultiCAFE parent0 = null;

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout layout = new FlowLayout(FlowLayout.LEFT);
		layout.setVgap(1);

		JPanel panel2 = new JPanel(layout);
		panel2.add(new JLabel("Load: "));
		panel2.add(kymographsCheckBox);
		panel2.add(cagesCheckBox);
		panel2.add(measuresCheckBox);
		panel2.add(graphsCheckBox);
		panel2.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
		add(panel2);

		JPanel panel1 = new JPanel(layout);
		panel1.add(new JLabel("View : "));
		panel1.add(viewCapillariesCheckBox);
		panel1.add(viewCellsCheckbox);
		panel1.add(viewFlyCheckbox);
		panel1.add(viewFlyRectCheckbox);
		add(panel1);

		defineActionListeners();
	}

	private void defineActionListeners() {
		viewCapillariesCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				displayROIsCategory(viewCapillariesCheckBox.isSelected(), "line");
			}
		});

		viewCellsCheckbox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				displayROIsCategory(viewCellsCheckbox.isSelected(), "cell");
				displayROIsCategory(viewCellsCheckbox.isSelected(), "cage");
			}
		});

		viewFlyCheckbox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				displayROIsCategory(viewFlyCheckbox.isSelected(), "det");
			}
		});

		viewFlyRectCheckbox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				displayROIsCategory(viewFlyRectCheckbox.isSelected(), "det");
			}
		});
	}

	public void displayROIsCategory(boolean isVisible, String pattern) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
		IcyCanvas canvas = v.getCanvas();
		List<Layer> layers = canvas.getLayers(false);
		if (layers == null)
			return;
		for (Layer layer : layers) {
			ROI roi = layer.getAttachedROI();
			if (roi == null)
				continue;
			String cs = roi.getName();
			if (cs.contains(pattern))
				layer.setVisible(isVisible);
		}
	}

}
