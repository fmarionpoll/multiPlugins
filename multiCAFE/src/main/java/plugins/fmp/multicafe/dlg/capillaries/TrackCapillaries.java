package plugins.fmp.multicafe.dlg.capillaries;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import icy.gui.frame.IcyFrame;
import icy.gui.frame.progress.ProgressFrame;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.series.ProgressReporter;
import plugins.fmp.multitools.series.TrackCapillariesAlongTime;

public class TrackCapillaries extends JPanel {

	private static final long serialVersionUID = 1L;

	private IcyFrame dialogFrame = null;
	private MultiCAFE parent0 = null;

	private JSpinner tStartSpinner;
	private JSpinner tEndSpinner;
	private JButton runFrameByFrameButton = new JButton("Run tracking");
	private JButton saveButton = new JButton("Save");

	public void initialize(MultiCAFE parent0, Point pt) {
		this.parent0 = parent0;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null)
			return;

		int nFrames = exp.getSeqCamData().getImageLoader().getNTotalFrames();
		if (nFrames <= 0)
			nFrames = 1000;

		tStartSpinner = new JSpinner(new SpinnerNumberModel(0, 0, nFrames - 1, 1));
		tEndSpinner = new JSpinner(new SpinnerNumberModel(Math.min(nFrames - 1, 100), 0, nFrames - 1, 1));

		JPanel topPanel = new JPanel(new GridLayout(4, 1));
		FlowLayout flow = new FlowLayout(FlowLayout.LEFT);

		JPanel p1 = new JPanel(flow);
		p1.add(new JLabel("Frame range:"));
		p1.add(new JLabel("from"));
		p1.add(tStartSpinner);
		p1.add(new JLabel("to"));
		p1.add(tEndSpinner);
		topPanel.add(p1);

		JPanel p2 = new JPanel(flow);
		p2.add(runFrameByFrameButton);
		p2.add(saveButton);
		topPanel.add(p2);

		JPanel p3 = new JPanel(flow);
		p3.add(new JLabel("Tracking will replace AlongT intervals in the selected range"));
		p3.add(new JLabel("Intervals outside the range are unchanged"));
		topPanel.add(p3);

		dialogFrame = new IcyFrame("Track capillaries along time", true, true);
		dialogFrame.add(topPanel, BorderLayout.NORTH);
		dialogFrame.setLocation(pt);
		dialogFrame.pack();
		dialogFrame.addToDesktopPane();
		dialogFrame.setVisible(true);

		runFrameByFrameButton.addActionListener(e -> runTrackingFrameByFrame());
		saveButton.addActionListener(e -> save());
	}

	private void runTrackingFrameByFrame() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null)
			return;

		int t0 = (Integer) tStartSpinner.getValue();
		int tEnd = (Integer) tEndSpinner.getValue();

		final ProgressFrame pf = new ProgressFrame("Tracking capillaries (dlg)");
		ProgressReporter progress = progressReporterFor(pf);

		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() {
				long t0Ms = System.currentTimeMillis();
				new TrackCapillariesAlongTime().run(exp, t0, tEnd, progress);
				long elapsed = System.currentTimeMillis() - t0Ms;
				System.out.println("Track capillaries: " + elapsed + " ms");
				return null;
			}

			@Override
			protected void done() {
				pf.close();
			}
		}.execute();
	}

	private ProgressReporter progressReporterFor(ProgressFrame pf) {
		return new ProgressReporter() {
			@Override
			public void updateMessage(String message) {
				SwingUtilities.invokeLater(() -> pf.setMessage(message));
			}

			@Override
			public void updateProgress(int percentage) {
				SwingUtilities.invokeLater(() -> pf.setMessage(percentage + "%"));
			}

			@Override
			public void updateProgress(String message, int current, int total) {
				SwingUtilities.invokeLater(() -> {
					pf.setMessage(message);
					pf.setLength(total);
					if (total > 0 && current >= 0)
						pf.setPosition((double) current / total);
				});
			}

			@Override
			public void completed() {
				SwingUtilities.invokeLater(() -> pf.close());
			}

			@Override
			public void failed(String errorMessage) {
				SwingUtilities.invokeLater(() -> {
					pf.setMessage("Failed: " + errorMessage);
					pf.close();
				});
			}

			@Override
			public boolean isCancelled() {
				return false;
			}
		};
	}

	private void save() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null)
			exp.save_capillaries_description_and_measures();
	}

	void close() {
		if (dialogFrame != null)
			dialogFrame.close();
	}
}
