package plugins.fmp.multicafe.dlg.capillaries;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.fmp_experiment.Experiment;

public class Edit extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7582410775062671523L;

	private JButton editCapillariesButton = new JButton("Edit capillaries position with time");
	private MultiCAFE parent0 = null;
	private EditPositionWithTime editCapillariesTable = null;

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		flowLayout.setVgap(0);

		JPanel panel0 = new JPanel(flowLayout);
		panel0.add(new JLabel("* this dialog is experimental"));
		add(panel0);

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(editCapillariesButton);
		add(panel1);

		defineActionListeners();
		this.parent0 = parent0;
	}

	private void defineActionListeners() {
		editCapillariesButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				openDialog();
			}
		});
	}

	private Point getFramePosition() {
		Point point = new Point();
		Component currComponent = (Component) editCapillariesButton;
		int index = 0;
		while (currComponent != null && index < 12) {
			Point relativeLocation = currComponent.getLocation();
			point.translate(relativeLocation.x, relativeLocation.y);
			currComponent = currComponent.getParent();
			index++;
		}
		return point;
	}

	public void openDialog() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
			exp.getCapillaries().transferDescriptionToCapillaries();
			if (editCapillariesTable == null)
				editCapillariesTable = new EditPositionWithTime();
			editCapillariesTable.initialize(parent0, getFramePosition());
		}
	}

	public void closeDialog() {
		editCapillariesTable.close();
	}
}