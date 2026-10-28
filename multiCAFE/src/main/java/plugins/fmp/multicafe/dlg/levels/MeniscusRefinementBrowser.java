package plugins.fmp.multicafe.dlg.levels;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.series.RefineMeniscus;
import plugins.fmp.multitools.tools.JComponents.JComboBoxExperimentLazy;

/**
 * Browser-following window; each experiment owns its draft and undo history.
 */
final class MeniscusRefinementBrowser {
	private final Map<Experiment, MeniscusRefinementPanel> sessions = new IdentityHashMap<>();
	private final JPanel content = new JPanel(new BorderLayout());
	private final JPanel experimentView = new JPanel(new BorderLayout());
	private final JComboBox<String> seriesRange = new JComboBox<>(
			new String[] { "Current experiment", "Current to last experiment", "All experiments" });
	private final JButton runSeries = new JButton("Correlate, validate & save");
	private final JButton stopSeries = new JButton("Stop");
	private final JLabel seriesStatus = new JLabel(
			"Uses validated topraw and the current correlation settings; all capillaries.");
	private RefineMeniscus seriesWorker;
	private final JComboBox<?> browser;
	private final JDialog dialog;
	private final Consumer<Experiment> changed;
	private MeniscusRefinementPanel active;
	private Experiment selected;
	private boolean closed;

	static void open(Component owner, JComboBox<?> browser, Capillary initial, Consumer<Experiment> changed) {
		new MeniscusRefinementBrowser(owner, browser, initial, changed);
	}

	private MeniscusRefinementBrowser(Component owner, JComboBox<?> browser, Capillary initial,
			Consumer<Experiment> changed) {
		this.browser = browser;
		this.changed = changed;
		dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), Dialog.ModalityType.MODELESS);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.setContentPane(content);
		dialog.setSize(1100, 700);
		dialog.setLocationRelativeTo(owner);
		JPanel series = new JPanel(new BorderLayout());
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
		actions.add(new JLabel("Series"));
		actions.add(seriesRange);
		actions.add(runSeries);
		actions.add(stopSeries);
		series.add(actions, BorderLayout.NORTH);
		series.add(seriesStatus, BorderLayout.SOUTH);
		content.add(series, BorderLayout.NORTH);
		content.add(experimentView, BorderLayout.CENTER);
		stopSeries.setEnabled(false);
		runSeries.setToolTipText(
				"Run correlation, apply it to topraw, validate and save each experiment before continuing. Unvalidated drafts are not used.");
		runSeries.addActionListener(e -> startSeries());
		stopSeries.addActionListener(e -> {
			if (seriesWorker != null)
				seriesWorker.requestStop();
			stopSeries.setEnabled(false);
			seriesStatus.setText("Stopping after the current correlation or save finishes...");
		});
		// Defer until the browser's own selection/load listeners have run. Reading
		// the selection then also coalesces deselect/select event pairs.
		ItemListener listener = e -> SwingUtilities.invokeLater(() -> showSelected(null));
		browser.addItemListener(listener);
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				closed = true;
				if (seriesWorker != null)
					seriesWorker.requestStop();
				browser.removeItemListener(listener);
				for (MeniscusRefinementPanel panel : sessions.values())
					panel.close();
				sessions.clear();
			}
		});
		showSelected(initial);
		dialog.setVisible(true);
	}

	private void showSelected(Capillary initial) {
		if (closed || seriesWorker != null)
			return;
		Object item = browser.getSelectedItem();
		Experiment next = item instanceof Experiment ? (Experiment) item : null;
		if (next == selected && active != null)
			return;
		if (active != null)
			active.suspendSession();
		selected = next;
		active = null;
		experimentView.removeAll();
		if (next == null) {
			experimentView.add(new JLabel("Select an experiment in the browser."), BorderLayout.CENTER);
		} else {
			active = sessions.get(next);
			if (active == null) {
				Capillary cap = next.getCapillaries().getList().contains(initial) ? initial : null;
				active = new MeniscusRefinementPanel(next, cap, () -> {
					if (changed != null)
						changed.accept(next);
				});
				sessions.put(next, active);
			}
			experimentView.add(active, BorderLayout.CENTER);
			active.resumeSession();
		}
		dialog.setTitle("Refine meniscus from topraw - " + (next == null ? "No experiment selected" : next.toString()));
		content.revalidate();
		content.repaint();
	}

	private void startSeries() {
		if (active == null || seriesWorker != null || !(browser instanceof JComboBoxExperimentLazy))
			return;
		JComboBoxExperimentLazy experiments = (JComboBoxExperimentLazy) browser;
		int current = experiments.getSelectedIndex();
		if (current < 0)
			return;
		RefineMeniscus worker = active.newSeriesWorker();
		worker.options.expList = experiments;
		experiments.index0 = seriesRange.getSelectedIndex() == 2 ? 0 : current;
		experiments.index1 = seriesRange.getSelectedIndex() == 0 ? current : experiments.getItemCount() - 1;
		// Invalidate drafts/undo only for experiments that the series will replace.
		for (int i = experiments.index0; i <= experiments.index1; i++) {
			MeniscusRefinementPanel panel = sessions.remove(experiments.getItemAt(i));
			if (panel != null)
				panel.close();
		}
		active = null;
		selected = null;
		seriesWorker = worker;
		boolean browserWasEnabled = browser.isEnabled();
		browser.setEnabled(false);
		seriesRange.setEnabled(false);
		runSeries.setEnabled(false);
		stopSeries.setEnabled(true);
		experimentView.removeAll();
		experimentView.add(new JLabel("Refining all capillaries; each experiment is saved before advancing."),
				BorderLayout.CENTER);
		seriesStatus.setText("Starting refinement series...");
		content.revalidate();
		content.repaint();
		worker.addPropertyChangeListener(e -> {
			if ("refinement_experiment".equals(e.getPropertyName()) && !closed) {
				seriesStatus.setText("Correlating and saving: " + e.getNewValue());
			} else if (worker.THREAD_ENDED.equals(e.getPropertyName())) {
				String result;
				try {
					worker.get();
					result = (worker.stopFlag ? "Stopped. " : "Complete. ") + worker.getSavedExperiments()
							+ " experiment(s) saved.";
				} catch (Exception error) {
					Throwable cause = error.getCause() == null ? error : error.getCause();
					result = cause.getMessage() + " (" + worker.getSavedExperiments() + " experiment(s) saved.)";
				}
				seriesWorker = null;
				browser.setEnabled(browserWasEnabled);
				if (closed)
					return;
				seriesRange.setEnabled(true);
				runSeries.setEnabled(true);
				stopSeries.setEnabled(false);
				showSelected(null);
				seriesStatus.setText(result);
			}
		});
		worker.execute();
	}
}
