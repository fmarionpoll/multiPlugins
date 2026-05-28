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
	// TODO _CAGES JCheckBox viewFlyCheckbox = new JCheckBox("flies center", false);
	// TODO _CAGES JCheckBox viewFlyRectCheckbox = new JCheckBox("flies rect",
	// false);
	private MultiSPOTS parent0 = null;

	void init(GridLayout capLayout, MultiSPOTS parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout layout = new FlowLayout(FlowLayout.LEFT);
		layout.setVgap(1);

		JPanel panel0 = new JPanel(layout);
		panel0.add(new JLabel("On open: "));
		panel0.add(autoLoadKymographsCheckBox);
		panel0.add(autoGraphSpotMeasuresCheckBox);
		panel0.add(autoGraphKymoMeasuresCheckBox);
		panel0.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
		add(panel0);

		JPanel panel1 = new JPanel(layout);
		panel1.add(new JLabel("View : "));
		panel1.add(viewSpotsCheckBox);
		panel1.add(viewCagesCheckbox);
		// TODO _CAGES panel1.add(viewFlyCheckbox);
		// TODO _CAGES panel1.add(viewFlyRectCheckbox);
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

		// TODO _CAGES viewFlyCheckbox.addActionListener(new ActionListener() {
		// TODO _CAGES @Override
		// TODO _CAGES public void actionPerformed(final ActionEvent e) {
		// TODO _CAGES displayROIsCategory(viewFlyCheckbox.isSelected(), "det");
		// TODO _CAGES }
		// TODO _CAGES });

		// TODO _CAGES viewFlyRectCheckbox.addActionListener(new ActionListener() {
		// TODO _CAGES @Override
		// TODO _CAGES public void actionPerformed(final ActionEvent e) {
		// TODO _CAGES displayROIsCategory(viewFlyRectCheckbox.isSelected(), "det");
		// TODO _CAGES }
		// TODO _CAGES });
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
