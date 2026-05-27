package plugins.fmp.multiSPOTS96.dlg.export;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * Excel export for cage-level spot series aggregated by (stimulus,
 * concentration) — AGG_SUMCLEAN.
 */
public class AggregatedSpotsAreasPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	JButton exportAggregatedButton = new JButton("save XLS");
	JButton exportKymoAggButton = new JButton("save kymo AGG XLS");
	JSpinner baselineMinutesSpinner = new JSpinner(new SpinnerNumberModel(2, 0, 120, 1));
	JCheckBox stopWhenStableCheckBox = new JCheckBox("stop when max stable", false);
	JSpinner stableBinsSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 60, 1));
	JCheckBox discardNoFlyCageCheckBox = new JCheckBox("discard cages with no fly", true);

	void init(GridLayout capLayout) {
		setLayout(capLayout);
		FlowLayout flow = new FlowLayout(FlowLayout.LEFT);
		flow.setVgap(0);

//		JPanel intro = new JPanel(flow);
//		intro.add(new JLabel("Export AGG_SUMCLEAN: one column per cage \u00d7 (stimulus, conc.) group."));
//		add(intro);

		JPanel panel0 = new JPanel(flow);
		panel0.add(new JLabel("baseline (min)"));
		panel0.add(baselineMinutesSpinner);
		panel0.add(stopWhenStableCheckBox);
		panel0.add(new JLabel("stable bins"));
		panel0.add(stableBinsSpinner);
		add(panel0);

		JPanel panel1 = new JPanel(flow);
		panel1.add(discardNoFlyCageCheckBox);
		add(panel1);

		FlowLayout actionsLayout = new FlowLayout(FlowLayout.RIGHT);
		actionsLayout.setVgap(0);
		JPanel panel2 = new JPanel(actionsLayout);
		panel2.add(exportAggregatedButton);
		panel2.add(exportKymoAggButton);
		add(panel2);

		exportAggregatedButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				firePropertyChange("EXPORT_AGGREGATED_SPOTSMEASURES", false, true);
			}
		});
		exportKymoAggButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				firePropertyChange("EXPORT_AGGREGATED_KYMO_SPOTSMEASURES", false, true);
			}
		});
	}
}
