package plugins.fmp.multicafe.dlg.browse;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.InputMap;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.text.JTextComponent;

import icy.gui.frame.progress.ProgressFrame;
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
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.sequence.ImageLoader;
import plugins.fmp.multitools.experiment.ui.SelectFilesPanel;
import plugins.fmp.multitools.tools.DescriptorsIO;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.tools.JComponents.SequenceNameListRenderer;

public class LoadSaveExperiment extends JPanel implements PropertyChangeListener, ItemListener, SequenceListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -690874563607080412L;

	// Performance constants for metadata-only processing
	private static final int METADATA_BATCH_SIZE = 20; // Process 20 experiments at a time
	private static final int PROGRESS_UPDATE_INTERVAL = 10; // Update progress every 10 experiments

	// UI Components
	private JButton openButton = new JButton("Open...");
	private JButton createButton = new JButton("Create...");
	private JButton searchButton = new JButton("Search...");
	private JButton closeButton = new JButton("Close");
	public JCheckBox filteredCheck = new JCheckBox("List filtered");

	public JCheckBox getFilteredCheck() {
		return filteredCheck;
	}

	// Data structures
	public List<String> selectedNames = new ArrayList<String>();
	private SelectFilesPanel dialogSelect = null;

	// Navigation buttons
	private JButton previousButton = new JButton("<");
	private JButton nextButton = new JButton(">");

	// Metadata storage - lightweight experiment information
	private List<ExperimentMetadata> experimentMetadataList = new ArrayList<>();
	private volatile boolean isProcessing = false;
	private final AtomicInteger processingCount = new AtomicInteger(0);
	private volatile boolean lastMetadataScanFailed = false;

	// Track currently loading experiment to prevent concurrent loads
	private volatile Experiment currentlyLoadingExperiment = null;
	private volatile int currentlyLoadingIndex = -1;

	// Parent reference
	private MultiCAFE parent0 = null;

	private transient KeyEventDispatcher globalBrowseKeyDispatcher = null;

	// -----------------------------------------
	public LoadSaveExperiment() {
	}

	public JPanel initPanel(MultiCAFE parent0) {
		this.parent0 = parent0;
		setLayout(new BorderLayout());
		setPreferredSize(new Dimension(400, 200));

		JPanel group2Panel = initUI();
		defineActionListeners();
		SwingUtilities.invokeLater(() -> installKeyBindings(group2Panel));
		parent0.expListComboLazy.addItemListener(this);

		return group2Panel;
	}

	private JPanel initUI() {
		JPanel group2Panel = new JPanel(new GridLayout(2, 1));
		JPanel navPanel = initNavigationPanel();
		JPanel buttonPanel = initButtonPanel();
		group2Panel.add(navPanel);
		group2Panel.add(buttonPanel);
		return group2Panel;
	}

	private JPanel initNavigationPanel() {
		JPanel navPanel = new JPanel(new BorderLayout());
		SequenceNameListRenderer renderer = new SequenceNameListRenderer();
		parent0.expListComboLazy.setRenderer(renderer);
		int bWidth = 30;
		int height = 20;
		previousButton.setPreferredSize(new Dimension(bWidth, height));
		nextButton.setPreferredSize(new Dimension(bWidth, height));

		navPanel.add(previousButton, BorderLayout.LINE_START);
		navPanel.add(parent0.expListComboLazy, BorderLayout.CENTER);
		navPanel.add(nextButton, BorderLayout.LINE_END);
		return navPanel;
	}

	private JPanel initButtonPanel() {
		JPanel buttonPanel = new JPanel(new BorderLayout());
		FlowLayout layout = new FlowLayout(FlowLayout.LEFT);
		layout.setVgap(1);
		JPanel subPanel = new JPanel(layout);
		subPanel.add(openButton);
		subPanel.add(createButton);
		subPanel.add(searchButton);
		subPanel.add(closeButton);
		subPanel.add(filteredCheck);
		buttonPanel.add(subPanel, BorderLayout.LINE_START);
		return buttonPanel;
	}

	private void defineActionListeners() {
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

		filteredCheck.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				parent0.paneExperiment.tabFilter.filterExperimentList(filteredCheck.isSelected());
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
				}
			}
		});

	}

	private void installKeyBindings(JComponent target) {
		InputMap im = target.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap am = target.getActionMap();

		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK), "browseNext");
		im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK), "browsePrevious");
		installGlobalKeyDispatcher(target);

		am.put("browseNext", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				if (nextButton.isEnabled()) {
					nextButton.doClick();
				}
			}
		});

		am.put("browsePrevious", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				if (previousButton.isEnabled()) {
					previousButton.doClick();
				}
			}
		});
	}

	private void installGlobalKeyDispatcher(JComponent target) {
		if (globalBrowseKeyDispatcher != null) {
			return;
		}

		globalBrowseKeyDispatcher = new KeyEventDispatcher() {
			@Override
			public boolean dispatchKeyEvent(KeyEvent e) {
				if (e.getID() != KeyEvent.KEY_PRESSED) {
					return false;
				}
				if (!e.isControlDown()) {
					return false;
				}
				if (!target.isDisplayable()) {
					return false;
				}
				if (parent0 == null || parent0.mainFrame == null || !parent0.mainFrame.isVisible()) {
					return false;
				}

				java.awt.Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
				if (focusOwner instanceof JTextComponent) {
					return false;
				}

				int code = e.getKeyCode();
				if (code == KeyEvent.VK_UP) {
					SwingUtilities.invokeLater(() -> {
						if (previousButton.isEnabled()) {
							previousButton.doClick();
						}
					});
					return true;
				}
				if (code == KeyEvent.VK_DOWN) {
					SwingUtilities.invokeLater(() -> {
						if (nextButton.isEnabled()) {
							nextButton.doClick();
						}
					});
					return true;
				}
				return false;
			}
		};

		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(globalBrowseKeyDispatcher);

		target.addHierarchyListener(new HierarchyListener() {
			@Override
			public void hierarchyChanged(HierarchyEvent e) {
				if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) == 0) {
					return;
				}
				if (!target.isDisplayable() && globalBrowseKeyDispatcher != null) {
					KeyboardFocusManager.getCurrentKeyboardFocusManager()
							.removeKeyEventDispatcher(globalBrowseKeyDispatcher);
					globalBrowseKeyDispatcher = null;
				}
			}
		});
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		if (evt.getPropertyName().equals("SELECT1_CLOSED")) {
			if (selectedNames.size() < 1) {
				return;
			}

			if (isProcessing) {
				Logger.warn("File processing already in progress, ignoring new request");
				return;
			}

			processSelectedFilesMetadataOnly();
		}
	}

	private void processSelectedFilesMetadataOnly() {
		isProcessing = true;
		processingCount.set(0);
		experimentMetadataList.clear();
		long startTime = System.nanoTime();

		ProgressFrame progressFrame = new ProgressFrame("Processing Experiment Metadata");
		progressFrame.setMessage("Scanning " + selectedNames.size() + " experiment directories...");

		SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				processMetadataOnly(progressFrame);
				return null;
			}

			@Override
			protected void done() {
				long endTime = System.nanoTime();
				SwingUtilities.invokeLater(() -> {
					finishMetadataScanAndUpdateUI(progressFrame, startTime, endTime);
				});
			}
		};

		worker.execute();
	}

	private void processMetadataOnly(ProgressFrame progressFrame) {
		lastMetadataScanFailed = false;
		final String subDir = parent0.expListComboLazy.expListBinSubDirectory;
		final int totalFiles = selectedNames.size();

		try {
			// Process files in batches for metadata only
			for (int i = 0; i < totalFiles; i += METADATA_BATCH_SIZE) {
				int endIndex = Math.min(i + METADATA_BATCH_SIZE, totalFiles);

				// Update progress
				final int currentBatch = i;
				final int currentEndIndex = endIndex;
				SwingUtilities.invokeLater(() -> {
					progressFrame.setMessage(String.format("Scanning experiments %d-%d of %d", currentBatch + 1,
							currentEndIndex, totalFiles));
					progressFrame.setPosition((double) currentBatch / totalFiles);
				});

				// Process batch for metadata only
				for (int j = i; j < endIndex; j++) {
					final String fileName = selectedNames.get(j);
					final int fileIndex = j;
					processSingleFileMetadataOnly(fileName, subDir, fileIndex);
					processingCount.incrementAndGet();

					// Update progress periodically
					if (j % PROGRESS_UPDATE_INTERVAL == 0) {
						final int currentProgress = j;
						SwingUtilities.invokeLater(() -> {
							progressFrame.setMessage(String.format("Found %d experiments...", currentProgress + 1));
						});
					}

					// Minimal delay to prevent UI freezing
					try {
						Thread.sleep(1); // Very small delay
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}

		} catch (Exception e) {
			lastMetadataScanFailed = true;
			Logger.error("Error processing experiment metadata: " + e.getMessage(), e);
			SwingUtilities.invokeLater(() -> {
				progressFrame.setMessage("Error: " + e.getMessage());
			});
		}
	}

	private void processSingleFileMetadataOnly(String fileName, String subDir, int fileIndex) {
		try {
			// Create lightweight ExperimentDirectories for metadata scanning only
			ExperimentDirectories expDirectories = new ExperimentDirectories();

			// Only check if the experiment directory exists and is valid
			if (expDirectories.getDirectoriesFromExptPath(subDir, fileName)) {
				String camDataImagesDirectory = expDirectories.getCameraImagesDirectory();
				String resultsDirectory = expDirectories.getResultsDirectory();
				ExperimentMetadata metadata = new ExperimentMetadata(camDataImagesDirectory, resultsDirectory, subDir);
				experimentMetadataList.add(metadata);
			}

		} catch (Exception e) {
			Logger.warn("Failed to process metadata for file [" + fileIndex + "] " + fileName + ": " + e.getMessage(),
					e);
		}
	}

	/**
	 * After directory scan: register experiments in the combo, index descriptors
	 * (with progress), then build missing descriptor cache files — then close
	 * progress and refresh the UI.
	 */
	private void finishMetadataScanAndUpdateUI(ProgressFrame progressFrame, long startTime, long endTime) {
		try {
			if (lastMetadataScanFailed) {
				progressFrame.close();
				selectedNames.clear();
				isProcessing = false;
				updateBrowseInterface();
				return;
			}
			if (experimentMetadataList.isEmpty()) {
				progressFrame.close();
				selectedNames.clear();
				isProcessing = false;
				Logger.debug("LoadExperiment: processSelectedFilesMetadataOnly took " + (endTime - startTime) / 1e6
						+ " ms (no new experiments)");
				updateBrowseInterface();
				return;
			}

			progressFrame.setMessage("Adding experiments to list...");
			progressFrame.setPosition(0);

			List<LazyExperiment> lazyExperiments = new ArrayList<>();
			for (ExperimentMetadata metadata : experimentMetadataList) {
				lazyExperiments.add(new LazyExperiment(metadata));
			}

			parent0.expListComboLazy.addLazyExperimentsBulk(lazyExperiments);
			parent0.paneExperiment.tabInfos.initCombos();

			parent0.descriptorIndex.preloadFromCombo(parent0.expListComboLazy, new Runnable() {
				@Override
				public void run() {
					parent0.paneExperiment.tabInfos.initCombos();
					parent0.paneExperiment.tabFilter.initCombos();
					runDescriptorsFileWorker(progressFrame, startTime, endTime);
				}
			}, progressFrame);

		} catch (Exception e) {
			Logger.warn("Error adding metadata to UI: " + e.getMessage(), e);
			progressFrame.close();
			selectedNames.clear();
			isProcessing = false;
			updateBrowseInterface();
		}
	}

	private void runDescriptorsFileWorker(ProgressFrame progressFrame, long scanStartTime, long scanEndTime) {
		final int n = parent0.expListComboLazy.getItemCount();
		new SwingWorker<Void, Integer>() {
			@Override
			protected Void doInBackground() throws Exception {
				for (int i = 0; i < n; i++) {
					Experiment exp = parent0.expListComboLazy.getItemAtNoLoad(i);
					if (exp == null) {
						continue;
					}
					String path = DescriptorsIO.getDescriptorsFullName(exp.getResultsDirectory());
					if (!new java.io.File(path).exists()) {
						DescriptorsIO.buildFromExperiment(exp);
					}
					publish(i);
				}
				return null;
			}

			@Override
			protected void process(java.util.List<Integer> chunks) {
				if (progressFrame == null || chunks.isEmpty()) {
					return;
				}
				int i = chunks.get(chunks.size() - 1);
				progressFrame.setMessage(String.format("Building descriptor cache %d / %d", i + 1, n));
				progressFrame.setPosition((double) (i + 1) / Math.max(1, n));
			}

			@Override
			protected void done() {
				progressFrame.close();
				selectedNames.clear();
				isProcessing = false;
				long totalEnd = System.nanoTime();
				Logger.debug(
						"LoadExperiment: processSelectedFilesMetadataOnly total " + (totalEnd - scanStartTime) / 1e6
								+ " ms (scan phase " + (scanEndTime - scanStartTime) / 1e6 + " ms)");
				updateBrowseInterface();
			}
		}.execute();
	}

	/**
	 * Counts the number of cages with fly positions.
	 * 
	 * @param exp The experiment to count cages for
	 * @return The count of cages with fly positions
	 */
	private int countCagesWithFlyPositions(Experiment exp) {
		int count = 0;
		for (Cage cage : exp.getCages().getCageList()) {
			if (cage.flyPositions != null && cage.flyPositions.flyPositionList != null
					&& !cage.flyPositions.flyPositionList.isEmpty()) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Logs cage load completion with timing and statistics.
	 * 
	 * @param exp       The experiment that was loaded
	 * @param expIndex  The index of the experiment
	 * @param startTime The start time in nanoseconds
	 * @param endTime   The end time in nanoseconds
	 */
	private void logCageLoadCompletion(Experiment exp, int expIndex, long startTime, long endTime) {
		int cageCount = exp.getCages().getCageList().size();
		int cagesWithFlyPositions = countCagesWithFlyPositions(exp);
		int totalFlyPositions = 0;
		for (Cage cage : exp.getCages().getCageList()) {
			if (cage.flyPositions != null && cage.flyPositions.flyPositionList != null
					&& !cage.flyPositions.flyPositionList.isEmpty()) {
				totalFlyPositions += cage.flyPositions.flyPositionList.size();
			}
		}
		int nFrames = 0;
		if (exp.getSeqCamData() != null && exp.getSeqCamData().getImageLoader() != null) {
			nFrames = exp.getSeqCamData().getImageLoader().getNTotalFrames();
		}
		Logger.debug("LoadExperiment: openSelectedExperiment [" + expIndex + "] load completed, total time: "
				+ (endTime - startTime) / 1e6 + " ms, cages: " + cageCount + ", with fly positions: "
				+ cagesWithFlyPositions + ", total fly positions: " + totalFlyPositions + ", frames: " + nFrames);
	}

	/**
	 * Validates that the experiment is still selected and not being saved.
	 * 
	 * @param exp           The experiment to validate
	 * @param expIndex      The index of the experiment
	 * @param progressFrame The progress frame to close if validation fails
	 * @return true if validation passes, false otherwise
	 */
	private boolean validateExperimentSelection(Experiment exp, int expIndex, ProgressFrame progressFrame) {
		if (parent0.expListComboLazy.getSelectedItem() != exp) {
			Logger.info("Skipping load for experiment [" + expIndex + "] - no longer selected");
			return false;
		}

		if (exp.isSaving()) {
			Logger.warn("Cannot load experiment [" + expIndex + "] - save operation in progress: " + exp.toString());
			progressFrame.close();
			return false;
		}

		return true;
	}

	/**
	 * Initializes the experiment load by setting flags and tracking state.
	 * 
	 * @param exp      The experiment being loaded
	 * @param expIndex The index of the experiment
	 */
	private void initializeExperimentLoad(Experiment exp, int expIndex) {
		exp.setLoading(true);
		currentlyLoadingExperiment = exp;
		currentlyLoadingIndex = expIndex;
	}

	/**
	 * Aborts the experiment load and cleans up state.
	 * 
	 * @param exp           The experiment being aborted
	 * @param expIndex      The index of the experiment
	 * @param progressFrame The progress frame to close
	 * @param reason        The reason for aborting (for logging)
	 * @return false to indicate load was aborted
	 */
	private boolean abortExperimentLoad(Experiment exp, int expIndex, ProgressFrame progressFrame, String reason) {
		Logger.info("Aborting load for experiment [" + expIndex + "] - " + reason);
		exp.setLoading(false);
		if (currentlyLoadingExperiment == exp) {
			currentlyLoadingExperiment = null;
			currentlyLoadingIndex = -1;
		}
		progressFrame.close();
		return false;
	}

	/**
	 * Loads experiment metadata (lazy or XML).
	 * 
	 * @param exp           The experiment to load metadata for
	 * @param expIndex      The index of the experiment
	 * @param progressFrame The progress frame to update
	 * @return true if successful, false if aborted
	 */
	private boolean loadExperimentMetadata(Experiment exp, int expIndex, ProgressFrame progressFrame) {
		if (exp instanceof LazyExperiment) {
			progressFrame.setMessage("Loading experiment metadata...");
			((LazyExperiment) exp).loadIfNeeded();
		} else {
			exp.xmlLoad_MCExperiment();
		}

		if (parent0.expListComboLazy.getSelectedItem() != exp) {
			return abortExperimentLoad(exp, expIndex, progressFrame, "different experiment selected after lazy load");
		}

		return true;
	}

	/**
	 * Loads experiment images and updates the viewer.
	 * 
	 * @param exp           The experiment to load images for
	 * @param expIndex      The index of the experiment
	 * @param progressFrame The progress frame to update
	 * @return true if successful, false if aborted or failed
	 */
	private boolean loadExperimentImages(Experiment exp, int expIndex, ProgressFrame progressFrame) {
		progressFrame.setMessage("Load image");
		exp.getSeqCamData().loadImages();

		// Fix: Recalculate nTotalFrames from actual image count if there's a mismatch
		// This handles the case where nFrames=1 was incorrectly saved to Experiment.xml
		ImageLoader imgLoader = exp.getSeqCamData().getImageLoader();
		int actualImageCount = imgLoader.getImagesCount();
		int loadedNFrames = imgLoader.getNTotalFrames();
		if (actualImageCount > 0 && loadedNFrames > 0 && actualImageCount != loadedNFrames) {
			long frameFirst = imgLoader.getAbsoluteIndexFirstImage();
			long nImages = actualImageCount + frameFirst;
			imgLoader.setFixedNumberOfImages(nImages);
			imgLoader.setNTotalFrames(actualImageCount);
		}

		if (parent0.expListComboLazy.getSelectedItem() != exp) {
			return abortExperimentLoad(exp, expIndex, progressFrame,
					"different experiment selected after loading images");
		}

		// Ensure sequence exists before creating viewer
		Sequence seq = exp.getSeqCamData().getSequence();
		if (seq == null) {
			Logger.warn("LoadSaveExperiment: Sequence is null after loadImages()");
			return abortExperimentLoad(exp, expIndex, progressFrame, "sequence is null after loading images");
		}

		// Create viewer - we're already on EDT from itemStateChanged, so call directly
		parent0.paneExperiment.updateViewerForSequenceCam(exp);

		parent0.paneExperiment.tabOptions.applyCentralViewOptionsToCamViewer(exp);

		if (parent0.expListComboLazy.getSelectedItem() != exp) {
			return abortExperimentLoad(exp, expIndex, progressFrame, "different experiment selected during load");
		}

		if (exp.getSeqCamData() == null) {
			Logger.error("LoadSaveExperiments:openSelectedExperiment() [" + expIndex
					+ "] Error: no jpg files found for this experiment\n");
			progressFrame.close();
			exp.setLoading(false);
			if (currentlyLoadingExperiment == exp) {
				currentlyLoadingExperiment = null;
				currentlyLoadingIndex = -1;
			}
			return false;
		}

		if (exp.getSeqCamData().getSequence() != null) {
			exp.getSeqCamData().getSequence().addListener(this);
		}

		return true;
	}

	/**
	 * Loads capillaries data.
	 * 
	 * @param exp           The experiment to load capillaries for
	 * @param progressFrame The progress frame to update
	 */
	private void loadCapillariesData(Experiment exp, ProgressFrame progressFrame) {
		progressFrame.setMessage("Load capillaries");
		exp.loadCamDataCapillaries();
	}

	/**
	 * Loads kymographs and capillary measures.
	 * 
	 * @param exp            The experiment to load kymographs for
	 * @param selectedBinDir The selected bin directory
	 * @param progressFrame  The progress frame to update
	 */
	private void loadKymographsAndMeasures(Experiment exp, String selectedBinDir, ProgressFrame progressFrame) {
		if (exp.getCapillaries() == null || exp.getCapillaries().getList().size() == 0) {
			return;
		}

		progressFrame.setMessage("Load kymographs");
		if (selectedBinDir != null) {
			parent0.paneKymos.tabLoadSave.loadDefaultKymos(exp);
		}

		progressFrame.setMessage("Load capillary measures");
		if (selectedBinDir != null && exp.getBinSubDirectory() != null) {
			String binFullDir = exp.getKymosBinFullDirectory();
			if (binFullDir != null) {
				exp.load_capillaries_description_and_measures();

				// Populate combo box AFTER measures are loaded to ensure capillaries are fully
				// populated
				if (exp.getSeqKymos() != null && exp.getCapillaries() != null
						&& exp.getCapillaries().getList().size() > 0) {
					parent0.paneKymos.tabIntervals.transferCapillaryNamesToComboBox(exp);
					parent0.paneKymos.tabIntervals.displayUpdateOnSwingThread();
				}
			}
		}

		if (exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null) {
			exp.getSeqKymos().getSequence().addListener(this);
		}
	}

	/**
	 * Displays graphs if enabled.
	 * 
	 * @param exp The experiment to display graphs for
	 */
	private void displayGraphsIfEnabled(Experiment exp) {
		if (parent0.paneExperiment.tabOptions.graphsCheckBox.isSelected()) {
			parent0.paneLevels.tabGraphs.displayChartPanels(exp);
			// Also refresh move-data graphs if they were already displayed.
			parent0.paneCages.tabGraphics.refreshIfDisplayed(exp);
		}
	}

	/**
	 * Legacy helper for old layouts where CagesMeasures.csv lived directly under
	 * the results directory. The current v2 format always uses the bin directory
	 * (results/bin_xx/CagesMeasures.csv), so this method is now a no-op to avoid
	 * creating or moving an extra file in results.
	 *
	 * @param exp The experiment (unused in current implementation)
	 */
	private void prepareCageMeasuresFile(Experiment exp) {
		// Intentionally left empty: CagesMeasures.csv is now stored only in the
		// bin directory (results/bin_xx) by Experiment.saveCagesMeasures().
	}

	boolean openSelectedExperiment(Experiment exp) {
		if (exp == null)
			return false;

		final long startTime = System.nanoTime();
		int expIndex = parent0.expListComboLazy.getSelectedIndex();

		Logger.debug("LoadSaveExperiment:openSelectedExperiment() START - exp="
				+ (exp != null ? exp.getResultsDirectory() : "null") + ", isLazy=" + (exp instanceof LazyExperiment)
				+ ", capillaries.count="
				+ (exp != null && exp.getCapillaries() != null ? exp.getCapillaries().getList().size() : "N/A"));

		ProgressFrame progressFrame = new ProgressFrame("Load Experiment Data");

		initializeExperimentLoad(exp, expIndex);

		try {
			if (!validateExperimentSelection(exp, expIndex, progressFrame)) {
				return abortExperimentLoad(exp, expIndex, progressFrame, "different experiment selected");
			}

			if (!loadExperimentMetadata(exp, expIndex, progressFrame)) {
				return false;
			}

			if (!loadExperimentImages(exp, expIndex, progressFrame)) {
				return false;
			}

			loadCapillariesData(exp, progressFrame);

			String selectedBinDir = selectBinDirectory(exp);
			if (selectedBinDir != null) {
				exp.setBinSubDirectory(selectedBinDir);
				parent0.expListComboLazy.expListBinSubDirectory = selectedBinDir;
			}

			loadKymographsAndMeasures(exp, selectedBinDir, progressFrame);

			if (!validateExperimentSelection(exp, expIndex, progressFrame)) {
				return abortExperimentLoad(exp, expIndex, progressFrame,
						"different experiment selected before cage load");
			}

			prepareCageMeasuresFile(exp);

			// Load cages synchronously (this method handles ROI transfer and preserves
			// capillaries)
			progressFrame.setMessage("Load cage measures...");
			boolean cagesLoaded = exp.load_cages_description_and_measures();

			if (cagesLoaded && exp.getCapillaries() != null && exp.getCapillaries().getList().size() > 0)
				exp.getCages().transferNFliesFromCapillariesToCageBox(exp.getCapillaries().getList());

			if (!cagesLoaded) {
				Logger.warn("Failed to load cages for experiment [" + expIndex + "]");
			}

			// Initialize absolute times (tMs) for fly positions so charts use real time
			// instead of frame indices on the X axis.
			if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null) {
				exp.initTmsForFlyPositions(exp.getCamImageFirst_ms());
			}

			exp.updateROIsAt(0);
			parent0.paneExperiment.tabOptions.applyCentralViewOptionsToCamViewer(exp);

			// Display graphs AFTER cages are loaded (dispatchCapillariesToCages needs cages
			// to be loaded)
			displayGraphsIfEnabled(exp);

			progressFrame.setMessage("Load data: update dialogs");

			parent0.paneExperiment.updateDialogs(exp);
			parent0.paneKymos.updateDialogs(exp);
			parent0.paneCapillaries.updateDialogs(exp);

			parent0.paneExperiment.tabInfos.transferPreviousExperimentInfosToDialog(exp, exp);

			long endTime = System.nanoTime();
			logCageLoadCompletion(exp, expIndex, startTime, endTime);

			exp.setLoading(false);
			if (currentlyLoadingExperiment == exp) {
				currentlyLoadingExperiment = null;
				currentlyLoadingIndex = -1;
			}

			progressFrame.close();
			return true;
		} catch (Exception e) {
			Logger.error("Error opening experiment [" + expIndex + "]: "
					+ (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
			Logger.error("Exception details [" + expIndex + "]: " + e.toString(), e);
			progressFrame.close();

			// Clear loading flag - loading failed but we're done trying
			exp.setLoading(false);
			if (currentlyLoadingExperiment == exp) {
				currentlyLoadingExperiment = null;
				currentlyLoadingIndex = -1;
			}

			long endTime = System.nanoTime();
			Logger.warn("LoadExperiment: openSelecteExperiment [" + expIndex + "] failed, took "
					+ (endTime - startTime) / 1e6 + " ms");
			return false;
		}
	}

	/**
	 * Selects the analysis interval directory for an experiment, delegating the
	 * decision to {@link plugins.fmp.multitools.experiment.BinDirectoryResolver}.
	 * When loading a series, the previously chosen directory (cached on the combo)
	 * is reused so the user is not prompted per file.
	 */
	private String selectBinDirectory(Experiment exp) {
		String resultsDir = exp.getResultsDirectory();
		if (resultsDir == null)
			return null;
		File resultsDirFile = new File(resultsDir);
		if (!resultsDirFile.exists() || !resultsDirFile.isDirectory())
			return null;

		boolean isFirstExperiment = (parent0.expListComboLazy.getSelectedIndex() == 0);
		boolean isSingleExperiment = (parent0.expListComboLazy.getItemCount() == 1);
		String previousBinDir = parent0.expListComboLazy.expListBinSubDirectory;

		plugins.fmp.multitools.experiment.BinDirectoryResolver.Context ctx = //
				new plugins.fmp.multitools.experiment.BinDirectoryResolver.Context();
		ctx.resultsDirectory = resultsDir;
		ctx.detectedIntervalMs = exp.getCamImageBin_ms() > 0 ? exp.getCamImageBin_ms() : exp.getKymoBin_ms();
		ctx.nominalIntervalSec = exp.getNominalIntervalSec();
		ctx.previouslySelected = previousBinDir;
		ctx.allowPrompt = (isSingleExperiment || isFirstExperiment);
		ctx.useSessionRemembered = true;
		ctx.parentForDialog = this;

		return plugins.fmp.multitools.experiment.BinDirectoryResolver.resolve(ctx);
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
			parent0.expListComboLazy.setSelectedIndex(selectedIndex);
			parent0.paneExperiment.tabInfos.initCombos();
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

	// ----------------------------

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
		if (e.getStateChange() == ItemEvent.SELECTED) {
			final Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null) {
				// Cancel any ongoing load for a different experiment
				if (currentlyLoadingExperiment != null && currentlyLoadingExperiment != exp) {
					Logger.info(
							"Cancelling load for experiment [" + currentlyLoadingIndex + "] - new experiment selected");
					// Clear loading flag for the previous experiment
					if (currentlyLoadingExperiment != null) {
						currentlyLoadingExperiment.setLoading(false);
					}
					currentlyLoadingExperiment = null;
					currentlyLoadingIndex = -1;
				}
				openSelectedExperiment(exp);
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
		parent0.paneExperiment.tabFilter.clearAllCheckBoxes();
		parent0.paneExperiment.tabFilter.filterExpList.removeAllItems();
		parent0.paneExperiment.tabInfos.clearCombos();
		filteredCheck.setSelected(false);
		experimentMetadataList.clear();
		if (parent0.descriptorIndex != null)
			parent0.descriptorIndex.clear();
	}

	public void closeViewsForCurrentExperiment(Experiment exp) {
		if (exp != null) {
			// Don't save if loading is still in progress (prevents race condition)
			if (exp.isLoading()) {
				Logger.warn("LoadSaveExperiment: Skipping save for experiment - loading still in progress: "
						+ exp.toString());
				return;
			}

			// Don't start a new save if one is already in progress (prevents concurrent
			// saves)
			if (exp.isSaving()) {
				Logger.warn("LoadSaveExperiment: Skipping save for experiment - save operation already in progress: "
						+ exp.toString());
				return;
			}

			// Set saving flag to prevent concurrent saves and loads
			exp.setSaving(true);

			try {
				// Clean up fly detection ROIs before closing
				if (exp.getSeqCamData() != null && exp.getSeqCamData().getSequence() != null) {
					exp.cleanPreviousDetectedFliesROIs();
				}

				// Always persist experiment-level metadata, even when sequence/camera data is
				// not currently loaded (e.g. metadata-only workflows).
				exp.saveExperimentDescriptors();

				if (exp.getSeqCamData() != null) {
					// Save capillaries using new dual-file system (descriptions + measures)
					int capCountBeforeSave = exp.getCapillaries() != null ? exp.getCapillaries().getList().size() : 0;
					Logger.debug(
							"LoadSaveExperiment:closeViewsForCurrentExperiment() About to save capillaries - count="
									+ capCountBeforeSave + ", exp=" + exp.getResultsDirectory());
					exp.save_capillaries_description_and_measures();

					// Update cages from sequence before saving
					exp.getCages().transferROIsFromSequence(exp.getSeqCamData());

					// Save cages descriptions synchronously
					exp.getCages().getPersistence().saveCages(exp.getCages(), exp.getResultsDirectory(), exp);

					// Save cage measures to bin directory
					String binDir = exp.getKymosBinFullDirectory();
					if (binDir != null) {
						exp.getCages().getPersistence().saveMeasures(exp.getCages(), binDir);
					}

					// multiCAFE persists only cages + capillaries (no spots)

					// Save MS96_descriptors.xml (synchronous, but quick)
					if (exp.getSeqCamData() != null) {
						DescriptorsIO.buildFromExperiment(exp);
					}
				}
				// Close sequences after all saves complete
				exp.closeSequences();

			} catch (Exception e) {
				Logger.error("Error in closeViewsForCurrentExperiment: " + e.getMessage(), e);
			} finally {
				// Always clear saving flag, even if save fails
				exp.setSaving(false);
			}
		}
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

	/**
	 * Gets memory usage statistics for monitoring.
	 * 
	 * @return Memory usage information
	 */
	public String getMemoryUsageInfo() {
		Runtime runtime = Runtime.getRuntime();
		long totalMemory = runtime.totalMemory();
		long freeMemory = runtime.freeMemory();
		long usedMemory = totalMemory - freeMemory;

		return String.format("Memory: %dMB used, %dMB total, %d experiments loaded", usedMemory / 1024 / 1024,
				totalMemory / 1024 / 1024, experimentMetadataList.size());
	}

	// ------------------------

	private int addExperimentFrom3NamesAnd2Lists(ExperimentDirectories eDAF) {
		Experiment exp = new Experiment(eDAF);
		int item = parent0.expListComboLazy.addExperiment(exp);
		return item;
	}

}
