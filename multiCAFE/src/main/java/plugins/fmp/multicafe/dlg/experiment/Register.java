package plugins.fmp.multicafe.dlg.experiment;

import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import icy.sequence.Sequence;
import icy.type.geom.Polygon2D;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.tools.registration.ImageRegistration;
import plugins.fmp.multitools.tools.registration.ImageRegistrationFeatures;
import plugins.fmp.multitools.tools.registration.ImageRegistrationFeaturesGPU;
import plugins.fmp.multitools.tools.registration.ImageRegistrationGaspard;
import plugins.kernel.roi.roi2d.ROI2DPolygon;

public class Register extends JPanel {

	private static final long serialVersionUID = -1234567890L;

	private MultiCAFE parent0 = null;
	private ROI2DPolygon referencePolygon = null;
	private JButton displayReferenceFrameButton = new JButton("Set reference points at frame corners");

	private JComboBox<String> typeCombo = new JComboBox<>(
			new String[] { "Gaspard Rigid", "Feature Tracking (CPU)", "Feature Tracking (GPU)" });
	private JComboBox<String> referenceCombo = new JComboBox<>(
			new String[] { "End (Last Frame)", "Start (First Frame)" });
	private JComboBox<String> directionCombo = new JComboBox<>(
			new String[] { "Backward (End -> Start)", "Forward (Start -> End)" });

	private JButton registerButton = new JButton("Register & Save");
	private JLabel statusLabel = new JLabel("Ready");

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel0 = new JPanel(layoutLeft);
		panel0.add(displayReferenceFrameButton);
		panel0.add(new JLabel("Algorithm: "));
		panel0.add(typeCombo);

		JPanel panel2 = new JPanel(layoutLeft);
		panel2.add(new JLabel("Reference: "));
		panel2.add(referenceCombo);
		panel2.add(new JLabel("Direction: "));
		panel2.add(directionCombo);

		JPanel panel3 = new JPanel(layoutLeft);
		panel3.add(registerButton);
		panel3.add(statusLabel);

		add(panel0);
		add(panel2);
		add(panel3);

		typeCombo.setSelectedIndex(1);

		defineActionListeners();
	}

	private void defineActionListeners() {
		displayReferenceFrameButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null)
					return;
				if (referencePolygon == null) {
					if (exp.getSeqCamData().getReferenceROI2DPolygon() == null)
						create_ROIPolygon(exp);
					else
						referencePolygon = exp.getSeqCamData().getReferenceROI2DPolygon();
				} else {
					exp.getSeqCamData().getSequence().removeROI(referencePolygon);
					referencePolygon = null;
				}
			}
		});

		registerButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				startRegistration();
			}
		});
	}

	private void startRegistration() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;

		registerButton.setEnabled(false);
		statusLabel.setText("Processing...");

		new Thread(() -> {
			exp.getSeqCamData().setReferenceROI2DPolygon(referencePolygon);

			int startFrame = 0;
			int endFrame = exp.getSeqCamData().getImageLoader().getNTotalFrames() - 1;

			boolean reverse = directionCombo.getSelectedIndex() == 0; // Backward
			int referenceFrame = (referenceCombo.getSelectedIndex() == 0) ? endFrame : startFrame;

			ImageRegistration reg = null;
			if (typeCombo.getSelectedIndex() == 0)
				reg = new ImageRegistrationGaspard();
			else if (typeCombo.getSelectedIndex() == 1)
				reg = new ImageRegistrationFeatures();
			else
				reg = new ImageRegistrationFeaturesGPU();

			boolean result = reg.runRegistration(exp, referenceFrame, startFrame, endFrame, reverse);

			SwingUtilities.invokeLater(() -> {
				statusLabel.setText(result ? "Done." : "Failed.");
				registerButton.setEnabled(true);
			});
		}).start();
	}

	private void create_ROIPolygon(Experiment exp) {
		Sequence seq = exp.getSeqCamData().getSequence();
		final String dummyname = "reference_polygon";

		referencePolygon = new ROI2DPolygon(getPolygon(seq));
		referencePolygon.setName(dummyname);
		seq.addROI(referencePolygon);

		seq.setSelectedROI(referencePolygon);
	}

	private Polygon2D getPolygon(Sequence seq) {
		Rectangle rect = seq.getBounds2D();
		List<Point2D> points = new ArrayList<Point2D>();
		points.add(new Point2D.Double(rect.x + rect.width / 5, rect.y + rect.height / 5));
		points.add(new Point2D.Double(rect.x + rect.width * 4 / 5, rect.y + rect.height / 5));
		points.add(new Point2D.Double(rect.x + rect.width * 4 / 5, rect.y + rect.height * 2 / 3));
		points.add(new Point2D.Double(rect.x + rect.width / 5, rect.y + rect.height * 2 / 3));
		return new Polygon2D(points);
	}

}
