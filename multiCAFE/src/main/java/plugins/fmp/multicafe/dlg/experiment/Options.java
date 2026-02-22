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
import plugins.fmp.multitools.experiment.Experiment;

public class Options extends JPanel {
	private static final long serialVersionUID = 6565346204580890307L;

	JCheckBox kymographsCheckBox = new JCheckBox("kymos", true);
	JCheckBox cagesCheckBox = new JCheckBox("cages", true);
	JCheckBox measuresCheckBox = new JCheckBox("measures", true);
	public JCheckBox graphsCheckBox = new JCheckBox("graphs", true);

	public JCheckBox viewCapillariesCheckBox = new JCheckBox("capillaries", true);
	public JCheckBox viewCellsCheckbox = new JCheckBox("cages", true);
	JCheckBox viewFlyCheckbox = new JCheckBox("flies", false);
	private MultiCAFE parent0 = null;

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		viewCapillariesCheckBox.setSelected(parent0.viewOptions.isViewCapillaries());
		viewCellsCheckbox.setSelected(parent0.viewOptions.isViewCages());
		viewFlyCheckbox.setSelected(parent0.viewOptions.isViewFliesCenter());

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
		add(panel1);

		defineActionListeners();
	}

	private void saveViewOptions() {
		parent0.viewOptions.save(parent0.getPreferences("viewOptions"));
	}

	private void defineActionListeners() {
		viewCapillariesCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				boolean sel = viewCapillariesCheckBox.isSelected();
				parent0.viewOptions.setViewCapillaries(sel);
				saveViewOptions();
				displayROIsCategory(sel, "line");
			}
		});

		viewCellsCheckbox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				boolean sel = viewCellsCheckbox.isSelected();
				parent0.viewOptions.setViewCages(sel);
				saveViewOptions();
				displayROIsCategory(sel, "cell");
				displayROIsCategory(sel, "cage");
			}
		});

		viewFlyCheckbox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				boolean sel = viewFlyCheckbox.isSelected();
				parent0.viewOptions.setViewFliesCenter(sel);
				saveViewOptions();
				displayROIsCategory(sel, "det");
			}
		});

	}

	public void displayROIsCategory(boolean isVisible, String pattern) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
		if (v == null)
			return;
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

	/**
	 * Applies central view options to the given experiment's cam viewer. Used on
	 * viewer T change and when a new experiment is loaded.
	 */
	public void applyCentralViewOptionsToCamViewer(Experiment exp) {
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null)
			return;
		Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
		if (v == null)
			return;
		plugins.fmp.multicafe.ViewOptionsHolder opts = parent0.viewOptions;
		displayROIsCategory(v, "line", opts.isViewCapillaries());
		displayROIsCategory(v, "cell", opts.isViewCages());
		displayROIsCategory(v, "cage", opts.isViewCages());
		displayROIsCategory(v, "det", opts.isViewFliesCenter() || opts.isViewFliesRect());
	}

	private void displayROIsCategory(Viewer v, String pattern, boolean isVisible) {
		if (v == null)
			return;
		IcyCanvas canvas = v.getCanvas();
		List<Layer> layers = canvas.getLayers(false);
		if (layers == null)
			return;
		for (Layer layer : layers) {
			ROI roi = layer.getAttachedROI();
			if (roi == null)
				continue;
			String cs = roi.getName();
			if (cs != null && cs.contains(pattern))
				layer.setVisible(isVisible);
		}
	}

}
