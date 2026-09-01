package plugins.fmp.multicafe.dlg.browse;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

import icy.gui.viewer.Viewer;
import icy.sequence.Sequence;
import icy.sequence.SequenceEvent;
import icy.sequence.SequenceEvent.SequenceEventSourceType;
import icy.sequence.SequenceListener;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentDirectories;
import plugins.fmp.multitools.experiment.LazyExperiment;
import plugins.fmp.multitools.experiment.LazyExperiment.ExperimentMetadata;
import plugins.fmp.multitools.experiment.ui.ExperimentBrowseKeyboard;
import plugins.fmp.multitools.experiment.ui.ExperimentLoadLifecycle;
import plugins.fmp.multitools.experiment.ui.SelectFilesPanel;
import plugins.fmp.multitools.tools.Logger;

public class BrowsePanel extends JPanel implements PropertyChangeListener, ItemListener, SequenceListener {

	private static final long serialVersionUID = -690874563607080412L;

	private JButton openButton = new JButton("Open...");
	private JButton createButton = new JButton("Create...");
	private JButton searchButton = new JButton("Search...");
	private JButton closeButton = new JButton("Close");
	JToggleButton showFilterButton = new JToggleButton("Filter (off)");
	JToggleButton showEditButton = new JToggleButton("Edit");
	JToggleButton showFindButton = new JToggleButton("Find");
	private boolean listFiltered = false;

	private static final String FILTER_BUTTON_OFF = "Filter (off)";
	private static final String FILTER_BUTTON_ON = "Filter (on)";
	private static final String TIP_EDIT = "Bulk-edit experiment or capillary descriptors for the current list (respects active Filter).";
	private static final String TIP_FIND = "Find measure outliers / anomalies (noisy bottom MAD, missing baseline, runaway tops…). Scan keeps matching experiments in the browse list.";
	private static final String TIP_CLOSE = "Close all open experiments and clear the browse list (releases viewers and file handles).";
	private static final String TIP_SEARCH = "Search disk for experiment folders and add them to the list.";
	private static final String TIP_FILTER_OFF = "Show filter panel: keep experiments by metadata (stim, strain, …).";
	private static final String TIP_FILTER_ON = "Experiment list is filtered — click to show or hide the filter panel.";

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
		showFilterButton.setToolTipText(listFiltered ? TIP_FILTER_ON : TIP_FILTER_OFF);
	}

	public List<String> selectedNames = new ArrayList<String>();
	private SelectFilesPanel dialogSelect = null;

	private JButton previousButton = new JButton("<");
	private JButton nextButton = new JButton(">");

	List<ExperimentMetadata> experimentMetadataList = new ArrayList<>();
	volatile boolean isProcessing = false;
	final AtomicInteger processingCount = new AtomicInteger(0);
	volatile boolean lastMetadataScanFailed = false;
	private volatile boolean suppressExperimentOpenDuringTransferReload = false;

	final ExperimentLoadLifecycle loadLifecycle = new ExperimentLoadLifecycle();

	MultiCAFE parent0 = null;

	private CafeMetadataScanCoordinator metadataScan;
	private CafeExperimentOpenPipeline openPipeline;
	private CafeExperimentClosePipeline closePipeline;

	public BrowsePanel() {
	}

	public JPanel initPanel(MultiCAFE parent0, FilterPanel filterPanel, EditCapillariesConditional editPanel,
			MeasureSearchPanel findPanel) {
		this.parent0 = parent0;
		this.metadataScan = new CafeMetadataScanCoordinator(this);
		this.openPipeline = new CafeExperimentOpenPipeline(this);
		this.closePipeline = new CafeExperimentClosePipeline();

		filterPanel.init(parent0);
		filterPanel.setVisible(false);
		editPanel.init(new GridLayout(4, 1), parent0);
		editPanel.setVisible(false);
		findPanel.init(parent0);
		findPanel.setVisible(false);

		JPanel seriesTools = new JPanel();
		seriesTools.setLayout(new BoxLayout(seriesTools, BoxLayout.Y_AXIS));
		filterPanel.setAlignmentX(0f);
		editPanel.setAlignmentX(0f);
		findPanel.setAlignmentX(0f);
		seriesTools.add(filterPanel);
		seriesTools.add(editPanel);
		seriesTools.add(findPanel);

		JPanel browseRoot = new JPanel(new BorderLayout());
		JPanel group2Panel = initUI();
		browseRoot.add(group2Panel, BorderLayout.NORTH);
		browseRoot.add(seriesTools, BorderLayout.CENTER);

		defineActionListeners(filterPanel, editPanel, findPanel);
		updateFilterButtonLabel();
		SwingUtilities.invokeLater(() -> ExperimentBrowseKeyboard.install(group2Panel, previousButton, nextButton,
				() -> parent0 != null && parent0.mainFrame != null && parent0.mainFrame.isVisible()));
		parent0.expListComboLazy.addItemListener(this);

		return browseRoot;
	}

	private JPanel initUI() {
		JPanel navPanel = CafeBrowseUi.createNavigationPanel(parent0, previousButton, nextButton);
		closeButton.setToolTipText(TIP_CLOSE);
		searchButton.setToolTipText(TIP_SEARCH);
		showEditButton.setToolTipText(TIP_EDIT);
		showFindButton.setText("Find");
		showFindButton.setToolTipText(TIP_FIND);
		updateFilterButtonLabel();
		JPanel buttonPanel = CafeBrowseUi.createButtonPanel(openButton, createButton, searchButton, closeButton,
				showFilterButton, showEditButton, showFindButton);
		return CafeBrowseUi.createMainGrid(navPanel, buttonPanel);
	}

	private void defineActionListeners(FilterPanel filterPanel, EditCapillariesConditional editPanel,
			MeasureSearchPanel findPanel) {
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
				if (show) {
					showEditButton.setSelected(false);
					showFindButton.setSelected(false);
					editPanel.setVisible(false);
					findPanel.setVisible(false);
					filterPanel.initCombos();
				}
				filterPanel.setVisible(show);
				repackMainFrame();
			}
		});

		showEditButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				boolean show = showEditButton.isSelected();
				if (show) {
					showFilterButton.setSelected(false);
					showFindButton.setSelected(false);
					filterPanel.setVisible(false);
					findPanel.setVisible(false);
					editPanel.initEditCombos();
				}
				editPanel.setVisible(show);
				repackMainFrame();
			}
		});

		showFindButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				boolean show = showFindButton.isSelected();
				if (show) {
					showFilterButton.setSelected(false);
					showEditButton.setSelected(false);
					filterPanel.setVisible(false);
					editPanel.setVisible(false);
				}
				findPanel.setVisible(show);
				repackMainFrame();
			}
		});

		createButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				ExperimentDirectories eDAF = new ExperimentDirectories();
				if (eDAF.getDirectoriesFromDialog(parent0.expListComboLazy, null, true)) {
					int item = addExperimentFrom3NamesAnd2Lists(eDAF);
					parent0.expListComboLazy.setSelectedIndex(item);
					parent0.paneExperiment.tabInfos.initCombos();
					parent0.paneBrowse.filterPanel.initCombos();
				}
			}
		});

	}

	private void repackMainFrame() {
		if (parent0 != null && parent0.mainFrame != null) {
			parent0.mainFrame.revalidate();
			parent0.mainFrame.pack();
			parent0.mainFrame.repaint();
		}
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
			String resolvedBin = eDAF.getBinSubDirectory();
			ExperimentMetadata metadata = new ExperimentMetadata(camDataImagesDirectory, resultsDirectory, resolvedBin);

			LazyExperiment lazyExp = new LazyExperiment(metadata);
			int selectedIndex = parent0.expListComboLazy.addLazyExperiment(lazyExp);
			parent0.paneExperiment.tabInfos.initCombos();
			parent0.paneBrowse.filterPanel.initCombos();
			parent0.expListComboLazy.setSelectedIndex(selectedIndex);
		}
	}

	private void handleSearchButton() {
		selectedNames = new ArrayList<String>();
		dialogSelect = new SelectFilesPanel(SelectFilesPanel.Features.cafeDefaults());
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
			if (exp != null) {
				if (exp.getSeqCamData().getSequence() != null
						&& sequenceEvent.getSequence() == exp.getSeqCamData().getSequence()) {
					Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
					int t = v.getPositionT();
					v.setTitle(exp.getSeqCamData().getDecoratedImageName(t));
				}
			}
		}
	}

	@Override
	public void sequenceClosed(Sequence sequence) {
		sequence.removeListener(this);
		ArrayList<Viewer> listViewers = sequence.getViewers();
		for (Viewer v : listViewers) {
			v.close();
		}
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		if (suppressExperimentOpenDuringTransferReload)
			return;
		if (e.getStateChange() == ItemEvent.SELECTED) {
			final Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				if (loadLifecycle.currentlyLoadingExperiment != null
						&& loadLifecycle.currentlyLoadingExperiment != exp) {
					Logger.info(
							"Cancelling load for experiment [" + loadLifecycle.currentlyLoadingIndex + "] - new experiment selected");
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
				Logger.warn("experiment = null");
		}
	}

	void closeAllExperiments() {
		closeCurrentExperiment();
		parent0.expListComboLazy.removeAllItems();
		parent0.paneBrowse.filterPanel.clearAllCheckBoxes();
		parent0.paneBrowse.filterPanel.filterExpList.removeAllItems();
		parent0.paneExperiment.tabInfos.clearCombos();
		setListFiltered(false);
		experimentMetadataList.clear();
		if (parent0.descriptorIndex != null)
			parent0.descriptorIndex.clear();
	}

	/**
	 * Transfer helper: close all experiments and clear UI state so file handles are
	 * released (important on Windows).
	 */
	public void closeAllExperimentsForTransfer() {
		closeAllExperiments();
	}

	/**
	 * Transfer helper: rebuild the combo from a list of {@code results/Experiment.xml}
	 * paths (v2 format). This is intentionally minimal and avoids opening sequences.
	 */
	public void reloadExperimentsFromExperimentXml(List<String> experimentXmlPaths) {
		closeAllExperiments();
		if (experimentXmlPaths == null || experimentXmlPaths.isEmpty())
			return;

		ArrayList<LazyExperiment> lazy = new ArrayList<>();
		String subDir = parent0.expListComboLazy.expListBinSubDirectory;
		for (String xml : experimentXmlPaths) {
			if (xml == null || xml.isBlank())
				continue;
			java.nio.file.Path xmlPath = java.nio.file.Paths.get(xml).toAbsolutePath().normalize();
			java.nio.file.Path resultsDir = xmlPath.getParent();
			if (resultsDir == null)
				continue;
			String resultsDirectory = resultsDir.toString();
			String camDataImagesDirectory = ExperimentDirectories.getImagesDirectoryAsParentFromFileName(resultsDirectory);

			ExperimentMetadata metadata = new ExperimentMetadata(camDataImagesDirectory, resultsDirectory, subDir);
			experimentMetadataList.add(metadata);
			lazy.add(new LazyExperiment(metadata));
		}
		suppressExperimentOpenDuringTransferReload = true;
		parent0.expListComboLazy.setSuppressLazyLoad(true);
		try {
			parent0.expListComboLazy.addLazyExperimentsBulk(lazy);
			// Do not auto-select an experiment: selecting triggers open + bin prompts.
			parent0.expListComboLazy.setSelectedIndex(-1);
		} finally {
			parent0.expListComboLazy.setSuppressLazyLoad(false);
			suppressExperimentOpenDuringTransferReload = false;
		}
	}

	/**
	 * Transfer helper: open experiment at index after a transfer reload.
	 * This is called after the transfer is completed, so it's OK if it triggers bin prompts.
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
			openPipeline.openSelectedExperiment(exp);
		}
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

	void updateBrowseInterface() {
		int isel = parent0.expListComboLazy.getSelectedIndex();
		boolean flag1 = (isel == 0 ? false : true);
		boolean flag2 = (isel == (parent0.expListComboLazy.getItemCount() - 1) ? false : true);
		previousButton.setEnabled(flag1);
		nextButton.setEnabled(flag2);
		if (parent0 != null && parent0.paneCages != null) {
			parent0.paneCages.refreshInfosFromCurrentExperiment();
		}
	}

	public String getMemoryUsageInfo() {
		Runtime runtime = Runtime.getRuntime();
		long totalMemory = runtime.totalMemory();
		long freeMemory = runtime.freeMemory();
		long usedMemory = totalMemory - freeMemory;

		return String.format("Memory: %dMB used, %dMB total, %d experiments loaded", usedMemory / 1024 / 1024,
				totalMemory / 1024 / 1024, experimentMetadataList.size());
	}

	private int addExperimentFrom3NamesAnd2Lists(ExperimentDirectories eDAF) {
		Experiment exp = new Experiment(eDAF);
		int item = parent0.expListComboLazy.addExperiment(exp);
		return item;
	}
}
