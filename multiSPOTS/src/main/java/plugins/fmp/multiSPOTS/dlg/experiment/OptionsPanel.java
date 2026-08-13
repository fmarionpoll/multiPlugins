package plugins.fmp.multiSPOTS.dlg.experiment;

import java.awt.ComponentOrientation;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;

public class OptionsPanel extends JPanel {
	private static final long serialVersionUID = 6565346204580890307L;

	JCheckBox autoLoadKymographsCheckBox = new JCheckBox("kymographs", true);
	JCheckBox autoGraphSpotMeasuresCheckBox = new JCheckBox("spot charts", true);
	JCheckBox autoGraphKymoMeasuresCheckBox = new JCheckBox("kymo charts", false);

	public JCheckBox viewSpotsCheckBox = new JCheckBox("spots", true);
	public JCheckBox viewCagesCheckbox = new JCheckBox("cages", true);
	public JCheckBox viewFlyRectCheckbox = new JCheckBox("flies rect", false);
	private MultiSPOTS parent0 = null;

	private static final String TIP_AUTO_KYMO = "Auto-load cage kymograph sequences when opening an experiment.";
	private static final String TIP_AUTO_SPOT_CHARTS = "Auto-open spot-measure chart windows when opening an experiment.";
	private static final String TIP_AUTO_KYMO_CHARTS = "Auto-open kymograph chart windows when opening an experiment.";
	private static final String TIP_FLIES_RECT = "Show or hide fly detection rectangles (ROI names starting with det).";

	void init(GridLayout capLayout, MultiSPOTS parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout layout = new FlowLayout(FlowLayout.LEFT);
		layout.setVgap(1);

		JPanel panel0 = new JPanel(layout);
		panel0.add(new JLabel("On open: "));
		autoLoadKymographsCheckBox.setToolTipText(TIP_AUTO_KYMO);
		autoGraphSpotMeasuresCheckBox.setToolTipText(TIP_AUTO_SPOT_CHARTS);
		autoGraphKymoMeasuresCheckBox.setToolTipText(TIP_AUTO_KYMO_CHARTS);
		panel0.add(autoLoadKymographsCheckBox);
		panel0.add(autoGraphSpotMeasuresCheckBox);
		panel0.add(autoGraphKymoMeasuresCheckBox);
		panel0.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
		add(panel0);

		JPanel panel1 = new JPanel(layout);
		panel1.add(new JLabel("View : "));
		viewFlyRectCheckbox.setToolTipText(TIP_FLIES_RECT);
		panel1.add(viewSpotsCheckBox);
		panel1.add(viewCagesCheckbox);
		panel1.add(viewFlyRectCheckbox);
		add(panel1);

		defineActionListeners();
		syncCheckboxesFromViewOptions();
	}

	private void syncCheckboxesFromViewOptions() {
		if (parent0 == null)
			return;
		autoLoadKymographsCheckBox.setSelected(parent0.viewOptions.isAutoLoadKymographs());
		autoGraphSpotMeasuresCheckBox.setSelected(parent0.viewOptions.isAutoGraphSpotMeasures());
		autoGraphKymoMeasuresCheckBox.setSelected(parent0.viewOptions.isAutoGraphKymoMeasures());
		viewSpotsCheckBox.setSelected(parent0.viewOptions.isViewSpots());
		viewCagesCheckbox.setSelected(parent0.viewOptions.isViewCages());
	}

	private void saveViewOptions() {
		parent0.viewOptions.save(parent0.getPreferences("viewOptions"));
	}

	private void defineActionListeners() {
		autoLoadKymographsCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				parent0.viewOptions.setAutoLoadKymographs(autoLoadKymographsCheckBox.isSelected());
				saveViewOptions();
			}
		});

		autoGraphSpotMeasuresCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				parent0.viewOptions.setAutoGraphSpotMeasures(autoGraphSpotMeasuresCheckBox.isSelected());
				saveViewOptions();
			}
		});

		autoGraphKymoMeasuresCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				parent0.viewOptions.setAutoGraphKymoMeasures(autoGraphKymoMeasuresCheckBox.isSelected());
				saveViewOptions();
			}
		});

		viewSpotsCheckBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				boolean v = viewSpotsCheckBox.isSelected();
				parent0.viewOptions.setViewSpots(v);
				saveViewOptions();
				displayROIsCategory(v, "line");
				displayROIsCategory(v, "spot");
			}
		});

		viewCagesCheckbox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				boolean v = viewCagesCheckbox.isSelected();
				parent0.viewOptions.setViewCages(v);
				saveViewOptions();
				displayROIsCategory(v, "cage");
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
		exp.getSeqCamData().displaySpecificROIs(isVisible, pattern);
	}

	public void applyViewOptionsToCurrentExperiment() {
		if (parent0 == null)
			return;
		boolean vSpots = parent0.viewOptions.isViewSpots();
		boolean vCages = parent0.viewOptions.isViewCages();
		displayROIsCategory(vSpots, "spot");
		displayROIsCategory(vCages, "cage");
	}

}
