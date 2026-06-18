package plugins.fmp.multiSPOTS.dlg.browse;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import icy.sequence.SequenceEvent;
import icy.sequence.SequenceEvent.SequenceEventSourceType;
import icy.sequence.SequenceListener;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentDirectories;
import plugins.fmp.multitools.experiment.LazyExperiment;
import plugins.fmp.multitools.experiment.LazyExperiment.ExperimentMetadata;
import plugins.fmp.multitools.experiment.ui.ExperimentBrowseKeyboard;
import plugins.fmp.multitools.experiment.ui.ExperimentLoadLifecycle;
import plugins.fmp.multitools.experiment.ui.SelectFilesPanel;
import plugins.fmp.multitools.series.CageKymographViewerUtil;
import plugins.fmp.multitools.tools.Logger;

public class LoadSaveExperiment extends JPanel implements PropertyChangeListener, ItemListener, SequenceListener {

	private static final long serialVersionUID = -690874563607080412L;

	private JButton openButton = new JButton("Open...");
	private JButton searchButton = new JButton("Search...");
	private JButton closeButton = new JButton("Close");
	JToggleButton showFilterButton = new JToggleButton("Filter (off)");
	private boolean listFiltered = false;

	private static final String FILTER_BUTTON_OFF = "Filter (off)";
	private static final String FILTER_BUTTON_ON = "Filter (on)";

	public boolean isListFiltered() {
		return listFiltered;
	}

	public void setListFiltered(boolean selected) {
		if (listFiltered == selected)
			return;
		listFiltered = selected;
		updateFilterButtonLabel();
	}

	public JToggleButton getShowFilterButton() {
		return showFilterButton;
	}

	void updateFilterButtonLabel() {
		showFilterButton.setText(listFiltered ? FILTER_BUTTON_ON : FILTER_BUTTON_OFF);
		showFilterButton.setToolTipText(listFiltered
				? "Experiment list is filtered — click to show or hide filter panel"
				: "Show or hide experiment filter panel");
	}

	public List<String> selectedNames = new ArrayList<String>();
	private SelectFilesPanel dialogSelect = null;

	private JButton previousButton = new JButton("<");
	private JButton nextButton = new JButton(">");

	List<ExperimentMetadata> experimentMetadataList = new ArrayList<>();
	volatile boolean isProcessing = false;
	final AtomicInteger processingCount = new AtomicInteger(0);
	private volatile boolean suppressExperimentOpenDuringTransferReload = false;

	final ExperimentLoadLifecycle loadLifecycle = new ExperimentLoadLifecycle();

	MultiSPOTS parent0 = null;

	private MetadataScanCoordinator metadataScan;
	private ExperimentOpenPipeline openPipeline;
	private ExperimentClosePipeline closePipeline;

	public LoadSaveExperiment() {
	}

	/**
	 * Transfer helper: close all experiments and clear UI state so file handles are
	 * released (important on Windows).
	 */
	public void closeAllExperimentsForTransfer() {
		closeAllExperiments();
	}

	/**
	 * Transfer helper: rebuild the combo from a list of
	 * {@code results/Experiment.xml} paths (v2 format). This is intentionally
	 * minimal and avoids opening sequences.
	 */
	public void reloadExperimentsFromExperimentXml(List<String> experimentXmlPaths) {
		closeAllExperimentsForTransfer();
		if (experimentXmlPaths == null || experimentXmlPaths.isEmpty())
			return;

		ArrayList<LazyExperiment> lazy = new ArrayList<>();
		String subDir = parent0.expListComboLazy.expListBinSubDirectory;
		for (String xml : experimentXmlPaths) {
			if (xml == null || xml.isBlank())
				continue;
			Path xmlPath = Paths.get(xml).toAbsolutePath().normalize();
			Path resultsDir = xmlPath.getParent();
			if (resultsDir == null)
				continue;
			// For v2 datasets, Experiment.xml lives directly under results/
			String resultsDirectory = resultsDir.toString();
			// Infer camera/grabs directory from results directory path
			String camDataImagesDirectory = ExperimentDirectories
					.getImagesDirectoryAsParentFromFileName(resultsDirectory);

			ExperimentMetadata metadata = new ExperimentMetadata(camDataImagesDirectory, resultsDirectory, subDir);
			experimentMetadataList.add(metadata);
			lazy.add(new LazyExperiment(metadata));
		}
		suppressExperimentOpenDuringTransferReload = true;
		parent0.expListComboLazy.setSuppressLazyLoad(true);
		try {
			parent0.expListComboLazy.addLazyExperimentsBulk(lazy);
			parent0.expListComboLazy.setSelectedIndex(-1);
		} finally {
			parent0.expListComboLazy.setSuppressLazyLoad(false);
			suppressExperimentOpenDuringTransferReload = false;
		}
	}

	/**
	 * Transfer helper: open experiment at index after a transfer reload. This is
	 * called after the transfer is completed, so it's OK if it triggers bin
	 * prompts.
	 */
	public void openExperimentAtIndex(int index) {
		if (parent0 == null || parent0.expListComboLazy == null)
			return;
		int n = parent0.expListComboLazy.getItemCount();
		if (n <= 0)
			return;
		int i = Math.min(Math.max(index, 0), n - 1);
		suppressExperimentOpenDuringTransferReload = false;
		parent0.expListComboLazy.setSelectedIndex(i);
		Experiment exp = parent0.expListComboLazy.getItemAt(i);
		if (exp != null) {
			openSelectedExperiment(exp);
		}
	}

	public JPanel initPanel(MultiSPOTS parent0, FilterPanel filterPanel) {
		this.parent0 = parent0;
		this.metadataScan = new MetadataScanCoordinator(this);
		this.openPipeline = new ExperimentOpenPipeline(this);
		this.closePipeline = new ExperimentClosePipeline();

		filterPanel.init(parent0);
		filterPanel.setVisible(false);

		JPanel browseRoot = new JPanel(new BorderLayout());
		JPanel group2Panel = initUI();
		browseRoot.add(group2Panel, BorderLayout.NORTH);
		browseRoot.add(filterPanel, BorderLayout.CENTER);

		defineActionListeners(filterPanel);
		updateFilterButtonLabel();
		SwingUtilities.invokeLater(() -> ExperimentBrowseKeyboard.install(group2Panel, previousButton, nextButton,
				() -> parent0 != null && parent0.mainFrame != null && parent0.mainFrame.isVisible()));
		parent0.expListComboLazy.addItemListener(this);

		return browseRoot;
	}

	private JPanel initUI() {
		JPanel navPanel = BrowseUi.createNavigationPanel(parent0, previousButton, nextButton);
		JPanel buttonPanel = BrowseUi.createButtonPanel(openButton, searchButton, closeButton, showFilterButton);
		return BrowseUi.createMainGrid(navPanel, buttonPanel);
	}

	private void defineActionListeners(FilterPanel filterPanel) {
		openButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				handleOpenButton();
			}
		});

		searchButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				handleSearchButton();
			}
		});

		closeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				handleCloseButton();
			}
		});

		previousButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				handlePreviousButton();
			}
		});

		nextButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				handleNextButton();
			}
		});

		parent0.expListComboLazy.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				updateBrowseInterface();
			}
		});

		showFilterButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				boolean show = showFilterButton.isSelected();
				filterPanel.setVisible(show);
				if (show)
					filterPanel.initCombos();
				if (parent0 != null && parent0.mainFrame != null) {
					parent0.mainFrame.revalidate();
					parent0.mainFrame.pack();
					parent0.mainFrame.repaint();
				}
			}
		});

	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (evt.getPropertyName().equals("SELECT1_CLOSED")) {
			metadataScan.onPropertyChangeSelectClosed();
		}
	}

	private void handleOpenButton() {
		ExperimentDirectories eDAF = new ExperimentDirectories();
		final String binDirectory = parent0.expListComboLazy.expListBinSubDirectory;
		if (eDAF.getDirectoriesFromDialog(binDirectory, null, false)) {
			String camDataImagesDirectory = eDAF.getCameraImagesDirectory();
			String resultsDirectory = eDAF.getResultsDirectory();
			ExperimentMetadata metadata = new ExperimentMetadata(camDataImagesDirectory, resultsDirectory,
					binDirectory);

			LazyExperiment lazyExp = new LazyExperiment(metadata);
			int selectedIndex = parent0.expListComboLazy.addLazyExperiment(lazyExp);
			parent0.dlgExperiment.infosPanel.initCombos();
			parent0.dlgBrowse.filterPanel.initCombos();
			parent0.expListComboLazy.setSelectedIndex(selectedIndex);
		}
	}

	private void handleSearchButton() {
		selectedNames = new ArrayList<String>();
		dialogSelect = new SelectFilesPanel(SelectFilesPanel.Features.spots96Defaults());
		dialogSelect.initialize(parent0.getPreferences("gui"), this, selectedNames);
	}

	private void handleCloseButton() {
		closeAllExperiments();
		parent0.expListComboLazy.removeAllItems();
		parent0.expListComboLazy.updateUI();
	}

	private void handlePreviousButton() {
		parent0.expListComboLazy.setSelectedIndex(parent0.expListComboLazy.getSelectedIndex() - 1);
		updateBrowseInterface();
	}

	private void handleNextButton() {
		parent0.expListComboLazy.setSelectedIndex(parent0.expListComboLazy.getSelectedIndex() + 1);
		updateBrowseInterface();
	}

	@Override
	public void sequenceChanged(SequenceEvent sequenceEvent) {
		if (sequenceEvent.getSourceType() == SequenceEventSourceType.SEQUENCE_DATA) {
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null && exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null
					&& sequenceEvent.getSequence() == exp.getSeqCamData().getSequence()) {
				Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
				if (v != null) {
					int t = v.getPositionT();
					v.setTitle(exp.getSeqCamData().getDecoratedImageName(t));
				}
			}
		}
	}

	@Override
	public void sequenceClosed(Sequence sequence) {
		if (sequence == null) {
			return;
		}
		sequence.removeListener(this);
		ArrayList<Viewer> listViewers = sequence.getViewers();
		if (listViewers != null) {
			for (Viewer v : listViewers) {
				if (v != null) {
					v.close();
				}
			}
		}
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		if (suppressExperimentOpenDuringTransferReload)
			return;
		if (parent0 != null && parent0.isSuppressExperimentOpenOnComboProgrammaticChange())
			return;
		if (e.getStateChange() == ItemEvent.SELECTED) {
			final Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				if (loadLifecycle.currentlyLoadingExperiment != null
						&& loadLifecycle.currentlyLoadingExperiment != exp) {
					Logger.info("Cancelling load for experiment [" + loadLifecycle.currentlyLoadingIndex
							+ "] - new experiment selected");
					if (loadLifecycle.currentlyLoadingExperiment != null) {
						loadLifecycle.currentlyLoadingExperiment.setLoading(false);
					}
					loadLifecycle.currentlyLoadingExperiment = null;
					loadLifecycle.currentlyLoadingIndex = -1;
				}
				openPipeline.openSelectedExperiment(exp);
			}
		} else if (e.getStateChange() == ItemEvent.DESELECTED) {
			Experiment exp = (Experiment) e.getItem();
			if (exp != null)
				closeViewsForCurrentExperiment(exp);
			else
				System.out.println("experiment = null");
		}
	}

	public boolean openSelectedExperiment(Experiment exp) {
		return openPipeline.openSelectedExperiment(exp);
	}

	public void closeViewsForCurrentExperiment(Experiment exp) {
		closePipeline.closeViewsForCurrentExperiment(exp);
	}

	public void closeCurrentExperiment() {
		if (parent0.expListComboLazy.getSelectedIndex() < 0)
			return;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp != null)
			closeViewsForCurrentExperiment(exp);
	}

	public void closeAllExperiments() {
		if (parent0 != null)
			parent0.closeAllGraphWindows();
		int n = parent0.expListComboLazy.getItemCount();
		for (int i = 0; i < n; i++) {
			Experiment exp = parent0.expListComboLazy.getItemAtNoLoad(i);
			if (exp != null)
				closeViewsForCurrentExperiment(exp);
		}
		parent0.expListComboLazy.removeAllItems();
		parent0.dlgBrowse.filterPanel.clearAllCheckBoxes();
		parent0.dlgBrowse.filterPanel.filterExpList.removeAllItems();
		parent0.dlgExperiment.infosPanel.clearCombos();
		setListFiltered(false);
		experimentMetadataList.clear();
		if (parent0.descriptorIndex != null)
			parent0.descriptorIndex.clear();
		CageKymographViewerUtil.detachSpotPickOverlay();
	}

	void updateBrowseInterface() {
		int isel = parent0.expListComboLazy.getSelectedIndex();
		boolean flag1 = (isel == 0 ? false : true);
		boolean flag2 = (isel == (parent0.expListComboLazy.getItemCount() - 1) ? false : true);
		previousButton.setEnabled(flag1);
		nextButton.setEnabled(flag2);
	}

	public String getMemoryUsageInfo() {
		Runtime runtime = Runtime.getRuntime();
		long totalMemory = runtime.totalMemory();
		long freeMemory = runtime.freeMemory();
		long usedMemory = totalMemory - freeMemory;

		return String.format("Memory: %dMB used, %dMB total, %d experiments loaded", usedMemory / 1024 / 1024,
				totalMemory / 1024 / 1024, experimentMetadataList.size());
	}
}
