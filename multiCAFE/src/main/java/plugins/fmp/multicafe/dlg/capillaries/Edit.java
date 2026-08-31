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
import plugins.fmp.multitools.experiment.Experiment;

public class Edit extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7582410775062671523L;

	private JButton editCapillariesButton = new JButton("Edit observation corridors with time");
	private JButton trackCapillariesButton = new JButton("Track physical capillaries");
	private MultiCAFE parent0 = null;
	private EditPositionWithTime editCapillariesTable = null;
	private TrackCapillaries trackCapillariesDialog = null;

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		flowLayout.setVgap(0);

		JPanel panel0 = new JPanel(flowLayout);
		panel0.add(new JLabel("* this dialog is experimental"));
		add(panel0);

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(editCapillariesButton);
		panel1.add(trackCapillariesButton);
		add(panel1);

		JPanel panel2 = new JPanel(flowLayout);
		panel2.add(new JLabel("Green = observation corridor; light blue = physical capillary"));
		add(panel2);

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
		trackCapillariesButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				openTrackCapillariesDialog();
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
			exp.getCapillaries().invalidateKymoIntervalsCache();
			exp.getCapillaries().transferDescriptionToCapillaries();
			if (editCapillariesTable == null)
				editCapillariesTable = new EditPositionWithTime();
			editCapillariesTable.initialize(parent0, getFramePosition());
		}
	}

	public void closeDialog() {
		if (editCapillariesTable != null)
			editCapillariesTable.close();
		if (trackCapillariesDialog != null)
			trackCapillariesDialog.close();
	}

	private void openTrackCapillariesDialog() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null) {
			exp.getCapillaries().invalidateKymoIntervalsCache();
			exp.getCapillaries().transferDescriptionToCapillaries();
			if (trackCapillariesDialog == null)
				trackCapillariesDialog = new TrackCapillaries();
			trackCapillariesDialog.initialize(parent0, getFramePosition());
		}
	}
}
