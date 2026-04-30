package plugins.fmp.multitools.experiment.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import icy.gui.frame.IcyFrame;
import icy.gui.util.GuiUtil;
import icy.preferences.XMLPreferences;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentDirectories;
import plugins.fmp.multitools.tools.Logger;

/**
 * Shared file/directory selection dialog used by multiCAFE and multiSPOTS96.
 * Behaviour is parameterised through {@link Features}.
 */
public class SelectFilesPanel extends JPanel {

	private static final long serialVersionUID = 4172927636287523049L;
	public static final String PROPERTY_SELECT_CLOSED = "SELECT1_CLOSED";

	public interface PatternNormalizer {
		String normalize(String pattern);
	}

	/**
	 * Per-plugin feature flags controlling optional behaviour and presets.
	 */
	public static final class Features {
		public List<String> filterPresets = Arrays.asList("cam", "grabs", "experiment");
		public int defaultSelectedPresetIndex = 2;
		public boolean enableCamBootstrap = true;
		/**
		 * In "grabs" mode, avoid full tree traversal by only searching for cam*
		 * directories up to this depth from the chosen root, then jumping to
		 * camXX/grabs.
		 */
		public int maxDepthForCamSearch = 8;
		public Set<String> legacyExperimentFileNamesLower = new HashSet<>(Arrays.asList("mcexperiment.xml"));
		public PatternNormalizer patternNormalizer = p -> p;

		public static Features cafeDefaults() {
			Features f = new Features();
			f.filterPresets = Arrays.asList("capillarytrack", "multicafe", "roisline", "cam", "grabs", "MCcapillaries",
					"Experiment");
			f.defaultSelectedPresetIndex = 6;
			f.enableCamBootstrap = true;
			f.maxDepthForCamSearch = 8;
			f.legacyExperimentFileNamesLower = new HashSet<>(Arrays.asList("mcexperiment.xml"));
			f.patternNormalizer = pattern -> {
				if (pattern == null)
					return "";
				if (pattern.toLowerCase().contains("mccapillaries"))
					return "MCcapi";
				return pattern;
			};
			return f;
		}

		public static Features spots96Defaults() {
			Features f = new Features();
			f.filterPresets = Arrays.asList("cam", "grabs", "experiment");
			f.defaultSelectedPresetIndex = 2;
			f.enableCamBootstrap = true;
			f.maxDepthForCamSearch = 8;
			f.legacyExperimentFileNamesLower = new HashSet<>(Arrays.asList("mcexperiment.xml", "ms96_experiment.xml"));
			return f;
		}
	}

	private final Features features;

	private IcyFrame dialogFrame = null;
	private final JComboBox<String> filterCombo;
	private final JButton findButton = new JButton("Select root directory and search...");
	private final JButton clearSelectedButton = new JButton("Clear selected");
	private final JButton clearAllButton = new JButton("Clear all");
	private final JButton addSelectedButton = new JButton("Add selected");
	private final JButton addAllButton = new JButton("Add all");
	private final JRadioButton rbFile = new JRadioButton("file", true);
	private final JRadioButton rbDirectory = new JRadioButton("directory");
	private final JList<String> directoriesJList = new JList<>(new DefaultListModel<>());

	private XMLPreferences guiPrefs = null;
	private List<String> selectedNames = null;
	private volatile boolean scanInProgress = false;

	private static final class SearchProgress {
		private final IcyFrame frame;
		private final JLabel label;
		private volatile boolean cancelled = false;

		SearchProgress(String title) {
			label = new JLabel(" ");
			JButton cancel = new JButton("Cancel");
			cancel.addActionListener(e -> cancelled = true);

			JPanel p = new JPanel(new BorderLayout());
			p.add(label, BorderLayout.CENTER);
			p.add(cancel, BorderLayout.SOUTH);

			frame = new IcyFrame(title, true, true);
			frame.setLayout(new BorderLayout());
			frame.add(p, BorderLayout.CENTER);
			frame.pack();
			frame.addToDesktopPane();
			frame.center();
			frame.setVisible(true);
		}

		boolean isCancelled() {
			return cancelled;
		}

		void setMessage(String msg) {
			label.setText("<html>" + (msg != null ? msg.replace("\n", "<br/>") : "") + "</html>");
		}

		void close() {
			frame.close();
		}
	}

	public SelectFilesPanel() {
		this(new Features());
	}

	public SelectFilesPanel(Features features) {
		this.features = (features != null) ? features : new Features();
		this.filterCombo = new JComboBox<>(this.features.filterPresets.toArray(new String[0]));
	}

	public void initialize(XMLPreferences guiPrefs, PropertyChangeListener listener, List<String> stringList) {
		this.guiPrefs = guiPrefs;
		addPropertyChangeListener(listener);
		selectedNames = stringList;

		JPanel mainPanel = GuiUtil.generatePanelWithoutBorder();
		dialogFrame = new IcyFrame("Select files", true, true);
		dialogFrame.setLayout(new BorderLayout());
		dialogFrame.add(mainPanel, BorderLayout.CENTER);

		FlowLayout layout1 = new FlowLayout(FlowLayout.LEFT);
		layout1.setVgap(1);
		JPanel topPanel = new JPanel(layout1);
		ButtonGroup bg = new ButtonGroup();
		bg.add(rbFile);
		bg.add(rbDirectory);
		topPanel.add(findButton);
		topPanel.add(filterCombo);
		topPanel.add(rbFile);
		topPanel.add(rbDirectory);
		mainPanel.add(GuiUtil.besidesPanel(topPanel));

		directoriesJList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		directoriesJList.setLayoutOrientation(JList.VERTICAL);
		directoriesJList.setVisibleRowCount(20);
		JScrollPane scrollPane = new JScrollPane(directoriesJList);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		mainPanel.add(GuiUtil.besidesPanel(scrollPane));

		mainPanel.add(GuiUtil.besidesPanel(clearSelectedButton, clearAllButton));
		mainPanel.add(GuiUtil.besidesPanel(addSelectedButton, addAllButton));

		filterCombo.setEditable(true);
		filterCombo.setSelectedIndex(Math.max(0, Math.min(features.defaultSelectedPresetIndex, filterCombo.getItemCount() - 1)));

		addActionListeners();

		dialogFrame.pack();
		dialogFrame.addToDesktopPane();
		dialogFrame.requestFocus();
		dialogFrame.center();
		dialogFrame.setVisible(true);
	}

	private void close() {
		if (dialogFrame != null)
			dialogFrame.close();
	}

	private void addActionListeners() {
		findButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				String pattern = (String) filterCombo.getSelectedItem();
				pattern = features.patternNormalizer != null ? features.patternNormalizer.normalize(pattern) : pattern;
				pattern = (pattern != null) ? pattern.toLowerCase() : "";

				boolean isFileName = rbFile.isSelected();
				if (pattern.contains("grabs") || pattern.contains("cam"))
					isFileName = false;

				startScan(pattern, isFileName);
			}
		});

		clearSelectedButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				List<String> selectedItems = directoriesJList.getSelectedValuesList();
				removeListofNamesFromList(selectedItems);
			}
		});

		clearAllButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				((DefaultListModel<String>) directoriesJList.getModel()).removeAllElements();
			}
		});

		addSelectedButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				List<String> selectedItems = directoriesJList.getSelectedValuesList();
				addNamesToSelectedList(selectedItems);
				removeListofNamesFromList(selectedItems);
				firePropertyChange(PROPERTY_SELECT_CLOSED, false, true);
				close();
			}
		});

		addAllButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				List<String> allItems = new ArrayList<>(directoriesJList.getModel().getSize());
				for (int i = 0; i < directoriesJList.getModel().getSize(); i++) {
					String name = directoriesJList.getModel().getElementAt(i);
					allItems.add(name);
				}
				addNamesToSelectedList(allItems);
				((DefaultListModel<String>) directoriesJList.getModel()).removeAllElements();
				firePropertyChange(PROPERTY_SELECT_CLOSED, false, true);
				close();
			}
		});
	}

	private void removeListofNamesFromList(List<String> selectedItems) {
		for (String oo : selectedItems)
			((DefaultListModel<String>) directoriesJList.getModel()).removeElement(oo);
	}

	private void setPreferencesPath(String pathString) {
		if (guiPrefs != null) {
			guiPrefs.put("lastUsedPath", pathString);
		}
	}

	private String getPreferencesPath() {
		if (guiPrefs != null) {
			return guiPrefs.get("lastUsedPath", "");
		}
		return "";
	}

	private boolean isLegacyExperimentFile(Path path) {
		if (path == null || path.getFileName() == null)
			return false;
		String fileName = path.getFileName().toString().toLowerCase();
		return features.legacyExperimentFileNamesLower != null && features.legacyExperimentFileNamesLower.contains(fileName);
	}

	private boolean isNewFormatExperimentFile(Path path) {
		if (path == null || path.getFileName() == null)
			return false;
		String fileName = path.getFileName().toString().toLowerCase();
		return fileName.equals("experiment.xml");
	}

	private List<Path> deduplicateExperimentFiles(List<Path> files) {
		if (files == null || files.isEmpty()) {
			return files;
		}

		Map<Path, List<Path>> filesByDirectory = files.stream().collect(Collectors.groupingBy(Path::getParent));

		List<Path> deduplicated = new ArrayList<>();
		for (Map.Entry<Path, List<Path>> entry : filesByDirectory.entrySet()) {
			List<Path> dirFiles = entry.getValue();
			List<Path> newFormatFiles = dirFiles.stream().filter(this::isNewFormatExperimentFile).collect(Collectors.toList());
			List<Path> legacyFiles = dirFiles.stream().filter(this::isLegacyExperimentFile).collect(Collectors.toList());

			if (!newFormatFiles.isEmpty() && !legacyFiles.isEmpty()) {
				deduplicated.addAll(newFormatFiles);
			} else {
				deduplicated.addAll(dirFiles);
			}
		}
		deduplicated.sort((p1, p2) -> p1.toString().compareToIgnoreCase(p2.toString()));
		return deduplicated;
	}

	private void startScan(String pattern, boolean isFileName) {
		if (scanInProgress) {
			Logger.warn("SelectFilesPanel: scan already running");
			return;
		}

		File dir = chooseDirectory(getPreferencesPath());
		if (dir == null)
			return;
		final String lastUsedPathString = dir.getAbsolutePath();
		setPreferencesPath(lastUsedPathString);

		scanInProgress = true;
		final long scanStartNs = System.nanoTime();
		final SearchProgress progress = new SearchProgress("Searching...");
		progress.setMessage("Starting scan in\n" + lastUsedPathString);

		final List<String> found = new ArrayList<>();

		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				try {
					if (isFileName) {
						scanFilesByName(pattern, dir, progress, found);
					} else if (features.enableCamBootstrap && pattern != null && pattern.equalsIgnoreCase("cam")) {
						scanCamDirectoriesAndBootstrapExperiments(dir.toPath(), progress, found);
					} else if (pattern != null && pattern.equalsIgnoreCase("grabs")) {
						scanGrabsDirectories(dir.toPath(), progress, found);
					} else {
						scanDirectoriesByName(pattern, dir, progress, found);
					}
				} catch (Exception e) {
					Logger.warn("SelectFilesPanel: scan failed: " + e.getMessage(), e);
				}
				return null;
			}

			@Override
			protected void done() {
				final long scanEndNs = System.nanoTime();
				try {
					if (!found.isEmpty()) {
						SwingUtilities.invokeLater(() -> {
							for (String s : found)
								addNameToListIfNew(s);
						});
					}
				} finally {
					double elapsedSec = (scanEndNs - scanStartNs) / 1e9;
					System.out.println(String.format("SelectFilesPanel: scan finished pattern='%s' fileNameMode=%s found=%d cancelled=%s time=%.3fs",
							pattern, Boolean.toString(isFileName), Integer.valueOf(found.size()),
							Boolean.toString(progress.isCancelled()), Double.valueOf(elapsedSec)));
					progress.close();
					scanInProgress = false;
				}
			}
		}.execute();
	}

	private boolean scanFilesByName(String pattern, File directory, SearchProgress progress, List<String> found) {
		final String lastUsedPathString = directory.getAbsolutePath();
		Path lastPath = Paths.get(lastUsedPathString);

		final AtomicInteger visited = new AtomicInteger(0);
		List<Path> result = null;
		try (Stream<Path> walk = Files.walk(lastPath)) {
			result = walk.filter(p -> {
				if (progress != null && progress.isCancelled())
					return false;
				int n = visited.incrementAndGet();
				if (progress != null && n % 500 == 0) {
					String msg = "Visiting " + n + " paths\n" + p.toString();
					SwingUtilities.invokeLater(() -> progress.setMessage(msg));
				}
				return Files.isRegularFile(p);
			}).filter(p -> p.getFileName().toString().toLowerCase().contains(pattern)).collect(Collectors.toList());
		} catch (IOException e) {
			Logger.warn("SelectFilesPanel: failed to scan files under " + lastUsedPathString + ": " + e.getMessage(), e);
		}
		if (progress != null && progress.isCancelled())
			return false;

		if (result != null && pattern.toLowerCase().contains("experiment")) {
			result = deduplicateExperimentFiles(result);
		}
		boolean flag = false;
		if (result != null && !result.isEmpty()) {
			flag = true;
			for (Path path : result) {
				if (progress != null && progress.isCancelled())
					break;
				found.add(path.toString());
			}
		}
		if (progress != null) {
			final int nVisited = visited.get();
			final int nFound = (result != null) ? result.size() : 0;
			SwingUtilities.invokeLater(
					() -> progress.setMessage(progress.isCancelled() ? "Cancelled (visited " + nVisited + ")"
							: "Done (visited " + nVisited + ", found " + nFound + ")"));
		}
		return flag;
	}

	private boolean scanDirectoriesByName(String pattern, File directory, SearchProgress progress, List<String> found) {
		final String lastUsedPathString = directory.getAbsolutePath();
		Path lastPath = Paths.get(lastUsedPathString);

		final AtomicInteger visited = new AtomicInteger(0);
		List<Path> result = null;
		try (Stream<Path> walk = Files.walk(lastPath)) {
			result = walk.filter(p -> {
				if (progress != null && progress.isCancelled())
					return false;
				int n = visited.incrementAndGet();
				if (progress != null && n % 500 == 0) {
					String msg = "Visiting " + n + " paths\n" + p.toString();
					SwingUtilities.invokeLater(() -> progress.setMessage(msg));
				}
				return Files.isDirectory(p);
			}).filter(p -> p.getFileName() != null
					&& p.getFileName().toString().toLowerCase().contains(pattern.toLowerCase()))
					.collect(Collectors.toList());
		} catch (IOException e) {
			Logger.warn("SelectFilesPanel: failed to scan directories under " + lastUsedPathString + ": " + e.getMessage(), e);
		}

		if (progress != null && progress.isCancelled())
			return false;

		if (result != null) {
			for (Path path : result) {
				if (progress != null && progress.isCancelled())
					break;
				File dir = path.toFile();
				if (!scanFilesByName("MCexpe", dir, null, new ArrayList<>())) {
					String experimentName = createEmptyExperiment(path);
					if (experimentName != null)
						found.add(experimentName);
				}
			}
		}
		if (progress != null) {
			final int nVisited = visited.get();
			final int nMatched = (result != null) ? result.size() : 0;
			SwingUtilities.invokeLater(
					() -> progress.setMessage(progress.isCancelled() ? "Cancelled (visited " + nVisited + ")"
							: "Done (visited " + nVisited + ", matched " + nMatched + ")"));
		}
		return (result != null);
	}

	/**
	 * Specialized scan for "grabs": only considers directories named exactly "grabs"
	 * that actually contain JPG images. This avoids huge match sets when the
	 * substring appears in unrelated paths.
	 */
	private void scanGrabsDirectories(Path root, SearchProgress progress, List<String> found) {
		if (root == null || !Files.isDirectory(root)) {
			return;
		}

		final AtomicInteger visited = new AtomicInteger(0);
		final int maxDepth = Math.max(1, features.maxDepthForCamSearch);

		// Depth-limited search for cam* directories; avoids walking deep trees.
		try (Stream<Path> stream = Files.find(root, maxDepth, (p, attrs) -> {
			if (progress != null && progress.isCancelled())
				return false;
			if (attrs == null || !attrs.isDirectory())
				return false;
			int n = visited.incrementAndGet();
			if (progress != null && n % 500 == 0) {
				String msg = "Searching cam* (depth<=" + maxDepth + ")... visited " + n + "\n" + p.toString();
				SwingUtilities.invokeLater(() -> progress.setMessage(msg));
			}
			Path fn = p.getFileName();
			return fn != null && fn.toString().toLowerCase().startsWith("cam");
		})) {
			stream.forEach(camDir -> {
				if (progress != null && progress.isCancelled())
					return;

				Path grabs = camDir.resolve("grabs");
				if (!Files.isDirectory(grabs)) {
					// In grabs-mode, we only want cam*/grabs (avoid cam directories without grabs).
					return;
				}

				bootstrapEmptyExperimentIfMissing(grabs);
				found.add(grabs.toString());
			});
		} catch (IOException e) {
			Logger.warn("SelectFilesPanel: failed to scan grabs directories: " + e.getMessage(), e);
		}

		if (progress != null) {
			final int nVisited = visited.get();
			SwingUtilities.invokeLater(
					() -> progress.setMessage(progress.isCancelled() ? "Cancelled (visited " + nVisited + ")"
							: "Done (visited " + nVisited + ", found " + found.size() + ")"));
		}
	}

	private String createEmptyExperiment(Path path) {
		ExperimentDirectories eADF = new ExperimentDirectories();
		eADF.getDirectoriesFromGrabPath(path.toString());

		if (eADF.cameraImagesList == null || eADF.cameraImagesList.isEmpty())
			return null;

		Experiment exp = new Experiment(eADF);
		return exp.getExperimentDirectory();
	}

	private void scanCamDirectoriesAndBootstrapExperiments(Path root, SearchProgress progress, List<String> found) {
		if (root == null || !Files.isDirectory(root)) {
			return;
		}

		final AtomicInteger visited = new AtomicInteger(0);
		try {
			Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					if (progress != null && progress.isCancelled())
						return FileVisitResult.TERMINATE;

					int n = visited.incrementAndGet();
					if (progress != null && n % 300 == 0) {
						String msg = "Scanning cam folders... visited " + n + "\n" + dir.toString();
						SwingUtilities.invokeLater(() -> progress.setMessage(msg));
					}

					Path dirName = dir.getFileName();
					if (dirName == null)
						return FileVisitResult.CONTINUE;

					String name = dirName.toString();
					if (name.toLowerCase().startsWith("cam")) {
						// Key optimisation: once we are inside cam*/ we don't need to descend (it contains grabs/images).
						Path imagesDir = autoDetectImagesDirectory(dir);
						if (imagesDir != null) {
							bootstrapEmptyExperimentIfMissing(imagesDir);
							found.add(imagesDir.toString());
						}
						return FileVisitResult.SKIP_SUBTREE;
					}

					// Skip common heavy/irrelevant subtrees
					String lower = name.toLowerCase();
					if (lower.equals("results") || lower.startsWith("bin_") || lower.equals("grabs")) {
						return FileVisitResult.SKIP_SUBTREE;
					}

					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			Logger.warn("SelectFilesPanel: failed to scan cam directories: " + e.getMessage(), e);
		}

		if (progress != null) {
			final int nVisited = visited.get();
			SwingUtilities.invokeLater(
					() -> progress.setMessage(progress.isCancelled() ? "Cancelled (visited " + nVisited + ")"
							: "Done (visited " + nVisited + ", found " + found.size() + ")"));
		}
	}

	private Path autoDetectImagesDirectory(Path camDir) {
		if (camDir == null || !Files.isDirectory(camDir)) {
			return null;
		}

		Path grabs = camDir.resolve("grabs");
		// Prefer the conventional layout camXX/grabs/ when it exists.
		if (Files.isDirectory(grabs)) {
			return grabs;
		}
		// Fallback: some datasets store images directly under camXX/.
		return camDir;
	}

	private void bootstrapEmptyExperimentIfMissing(Path imagesDir) {
		if (imagesDir == null || !Files.isDirectory(imagesDir)) {
			return;
		}

		// Some acquisitions contain one or more result*/ directories (results, results1, results_...)
		// under grabs. If any already contains an experiment descriptor, don't bootstrap.
		if (hasAnyExperimentDescriptor(imagesDir)) {
			return;
		}

		Path resultsDir = imagesDir.resolve(Experiment.RESULTS);
		try {
			Files.createDirectories(resultsDir);
		} catch (IOException e) {
			Logger.warn("SelectFilesPanel: cannot create results directory: " + resultsDir + " (" + e.getMessage() + ")", e);
			return;
		}

		try {
			ExperimentDirectories eADF = new ExperimentDirectories();
			eADF.getDirectoriesFromGrabPath(imagesDir.toString());
			if (eADF.cameraImagesList == null || eADF.cameraImagesList.isEmpty()) {
				return;
			}
			Experiment exp = new Experiment(eADF);
			exp.saveExperimentDescriptors();
		} catch (Exception e) {
			Logger.warn("SelectFilesPanel: failed to bootstrap Experiment.xml for " + imagesDir + ": " + e.getMessage(), e);
		}
	}

	private boolean hasAnyExperimentDescriptor(Path imagesDir) {
		if (imagesDir == null || !Files.isDirectory(imagesDir))
			return false;

		try (Stream<Path> stream = Files.list(imagesDir)) {
			List<Path> resultDirs = stream.filter(Files::isDirectory).filter(p -> {
				Path fn = p.getFileName();
				return fn != null && fn.toString().toLowerCase().startsWith("result");
			}).collect(Collectors.toList());

			for (Path rd : resultDirs) {
				Path v2 = rd.resolve("Experiment.xml");
				Path legacyMc = rd.resolve("MCexperiment.xml");
				Path legacyMs96 = rd.resolve("MS96_experiment.xml");
				if (Files.isRegularFile(v2) || Files.isRegularFile(legacyMc) || Files.isRegularFile(legacyMs96))
					return true;
			}
		} catch (IOException e) {
			// Ignore listing errors; fall back to attempting bootstrap.
		}

		return false;
	}

	private void addNameToListIfNew(String fileName) {
		int ilast = ((DefaultListModel<String>) directoriesJList.getModel()).getSize();
		for (int i = 0; i < ilast; i++) {
			String oo = ((DefaultListModel<String>) directoriesJList.getModel()).getElementAt(i);
			if (oo.equalsIgnoreCase(fileName)) {
				return;
			}
		}
		((DefaultListModel<String>) directoriesJList.getModel()).addElement(fileName);
	}

	private File chooseDirectory(String rootdirectory) {
		File dummy_selected = null;
		JFileChooser fc = new JFileChooser();
		if (rootdirectory != null)
			fc.setCurrentDirectory(new File(rootdirectory));
		fc.setDialogTitle("Select a root directory...");
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fc.setAcceptAllFileFilterUsed(false);
		if (fc.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
			dummy_selected = fc.getSelectedFile();
		} else {
			Logger.warn("SelectFilesPanel: chooseDirectory() no directory selected");
		}
		return dummy_selected;
	}

	private void addNamesToSelectedList(List<String> stringList) {
		if (selectedNames == null)
			return;
		for (String name : stringList) {
			Path p = Paths.get(name);
			String directoryName = null;
			File f = p.toFile();
			if (f.isDirectory()) {
				directoryName = p.toString();
			} else if (p.getParent() != null) {
				directoryName = p.getParent().toString();
			}
			if (directoryName == null) {
				continue;
			}
			if (isDirectoryWithJpg(directoryName))
				selectedNames.add(directoryName);
		}
		Collections.sort(selectedNames);
	}

	private boolean isDirectoryWithJpg(String directoryName) {
		String imageDirectory = ExperimentDirectories.getImagesDirectoryAsParentFromFileName(directoryName);
		File dir = new File(imageDirectory);
		File[] files = dir.listFiles((d, name) -> name != null && name.toLowerCase().endsWith(".jpg"));
		return files != null && files.length > 0;
	}
}

