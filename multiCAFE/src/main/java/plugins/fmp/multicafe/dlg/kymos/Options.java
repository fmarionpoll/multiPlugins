package plugins.fmp.multicafe.dlg.kymos;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToolBar;

import icy.canvas.Canvas2D;
import icy.canvas.IcyCanvas;
import icy.canvas.Layer;
import icy.gui.viewer.Viewer;
import icy.gui.viewer.ViewerEvent;
import icy.gui.viewer.ViewerListener;
import icy.main.Icy;
import icy.roi.ROI;
import icy.sequence.DimensionId;
import icy.sequence.Sequence;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multicafe.canvas2D.Canvas2DWithTransforms;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillaries.Capillaries;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.tools.Directories;
import plugins.fmp.multitools.tools.ViewerFMP;

public class Options extends JPanel implements ViewerListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2103052112476748890L;

	public int indexImagesCombo = -1;
	JComboBox<String> kymographsCombo = new JComboBox<String>(new String[] { "none" });
	JComboBox<String> viewsCombo = new JComboBox<String>();
	JButton previousButton = new JButton("<");
	JButton nextButton = new JButton(">");
	JCheckBox viewLevelsCheckbox = new JCheckBox("top/bottom level (green)", true);
	JCheckBox viewDerivativeCheckbox = new JCheckBox("derivative (yellow)", true);
	JCheckBox viewGulpsCheckbox = new JCheckBox("gulps (red)", true);

	private MultiCAFE parent0 = null;
	private boolean isActionEnabled = true;

	// Global window position - shared across all experiments
	private static Rectangle globalKymographViewerBounds = null;

	// ComponentListener to track window position changes
	private ComponentAdapter kymographViewerBoundsListener = null;

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		viewLevelsCheckbox.setSelected(parent0.viewOptions.isViewLevels());
		viewDerivativeCheckbox.setSelected(parent0.viewOptions.isViewDerivative());
		viewGulpsCheckbox.setSelected(parent0.viewOptions.isViewGulps());

		FlowLayout layout = new FlowLayout(FlowLayout.LEFT);
		layout.setVgap(0);

		JPanel panel1 = new JPanel(layout);
		panel1.add(new JLabel("bin size"));
		panel1.add(viewsCombo);
		panel1.add(new JLabel(" kymograph from"));
		int bWidth = 30;
		int bHeight = 21;
		panel1.add(previousButton, BorderLayout.WEST);
		previousButton.setPreferredSize(new Dimension(bWidth, bHeight));
		panel1.add(kymographsCombo, BorderLayout.CENTER);
		nextButton.setPreferredSize(new Dimension(bWidth, bHeight));
		panel1.add(nextButton, BorderLayout.EAST);
		add(panel1);

		JPanel panel2 = new JPanel(layout);
		panel2.add(viewLevelsCheckbox);
		panel2.add(viewDerivativeCheckbox);
		panel2.add(viewGulpsCheckbox);
		add(panel2);

		defineActionListeners();
	}

	private void defineActionListeners() {
		kymographsCombo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (isActionEnabled)
					displayUpdateOnSwingThread();
			}
		});

		viewDerivativeCheckbox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				boolean sel = viewDerivativeCheckbox.isSelected();
				parent0.viewOptions.setViewDerivative(sel);
				saveViewOptions();
				displayROIs("deriv", sel);
			}
		});

		viewGulpsCheckbox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				boolean sel = viewGulpsCheckbox.isSelected();
				parent0.viewOptions.setViewGulps(sel);
				saveViewOptions();
				displayROIs("gulp", sel);
			}
		});

		viewLevelsCheckbox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				boolean sel = viewLevelsCheckbox.isSelected();
				parent0.viewOptions.setViewLevels(sel);
				saveViewOptions();
				displayROIs("level", sel);
			}
		});

		nextButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				int isel = kymographsCombo.getSelectedIndex() + 1;
				if (isel < kymographsCombo.getItemCount()) {
					isel = selectKymographImage(isel);
					selectKymographComboItem(isel);
				}
			}
		});

		previousButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				int isel = kymographsCombo.getSelectedIndex() - 1;
				if (isel >= 0) {
					isel = selectKymographImage(isel);
					selectKymographComboItem(isel);
				}
			}
		});

		viewsCombo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				String localString = (String) viewsCombo.getSelectedItem();
				if (localString != null && localString.contains("."))
					localString = null;
				if (isActionEnabled)
					changeBinSubdirectory(localString);
			}
		});

	}

	/**
	 * Fills the combo in kymograph sequence order (index = t). Each item is the
	 * capillary/display name for that frame (from capillary or from kymograph file name, e.g. line0L).
	 */
	public void transferCapillaryNamesToComboBox(Experiment exp) {
		kymographsCombo.removeAllItems();
		if (exp == null || exp.getCapillaries() == null || exp.getSeqKymos() == null
				|| exp.getSeqKymos().getSequence() == null)
			return;
		SequenceKymos seqKymos = exp.getSeqKymos();
		Capillaries capillaries = exp.getCapillaries();
		int sizeT = seqKymos.getSequence().getSizeT();
		for (int t = 0; t < sizeT; t++) {
			Capillary cap = seqKymos.getCapillaryForFrame(t, capillaries);
			String name;
			if (cap != null && cap.getRoiName() != null) {
				name = cap.getRoiName();
			} else {
				String path = seqKymos.getFileNameFromImageList(t);
				if (path != null) {
					String base = new java.io.File(path).getName();
					int lastDot = base.lastIndexOf('.');
					if (lastDot > 0)
						base = base.substring(0, lastDot);
					name = base.replaceAll("1$", "L").replaceAll("2$", "R");
				} else {
					name = "t=" + t;
				}
			}
			kymographsCombo.addItem(name);
		}
	}

	private void saveViewOptions() {
		parent0.viewOptions.save(parent0.getPreferences("viewOptions"));
	}

	public void displayROIsAccordingToUserSelection() {
		displayROIs("deriv", viewDerivativeCheckbox.isSelected());
		displayROIs("gulp", viewGulpsCheckbox.isSelected());
		displayROIs("level", viewLevelsCheckbox.isSelected());
	}

	/**
	 * Applies central view options to the kymos viewer. Used on viewer T change and
	 * when opening kymos / selecting frame.
	 */
	public void applyCentralViewOptionsToKymosViewer(Viewer v) {
		if (v == null)
			return;
		plugins.fmp.multicafe.ViewOptionsHolder opts = parent0.viewOptions;
		displayROIsOnViewer(v, "deriv", opts.isViewDerivative());
		displayROIsOnViewer(v, "gulp", opts.isViewGulps());
		displayROIsOnViewer(v, "level", opts.isViewLevels());
	}

	private void displayROIsOnViewer(Viewer v, String filter, boolean visible) {
		if (v == null)
			return;
		IcyCanvas canvas = v.getCanvas();
		List<Layer> layers = canvas.getLayers(false);
		if (layers != null) {
			for (Layer layer : layers) {
				ROI roi = layer.getAttachedROI();
				if (roi != null) {
					String cs = roi.getName();
					if (cs != null && cs.contains(filter))
						layer.setVisible(visible);
				}
			}
		}
	}

	private void displayROIs(String filter, boolean visible) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		Viewer v = exp.getSeqKymos().getSequence().getFirstViewer();
		if (v == null)
			return;
		IcyCanvas canvas = v.getCanvas();
		List<Layer> layers = canvas.getLayers(false);
		if (layers != null) {
			for (Layer layer : layers) {
				ROI roi = layer.getAttachedROI();
				if (roi != null) {
					String cs = roi.getName();
					if (cs.contains(filter))
						layer.setVisible(visible);
				}
			}
		}
	}

	public void displayON() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();

		if (exp != null) {
			SequenceKymos seqKymographs = exp.getSeqKymos();
			if (seqKymographs == null || seqKymographs.getSequence() == null) {
				return;
			}

			icy.sequence.Sequence seq = seqKymographs.getSequence();
			if (seq.isUpdating()) {
				seq.endUpdate();
			}

			// Calculate position before any viewer operations to avoid flickering
			Rectangle initialBounds = calculateKymographViewerBounds(exp);

			ArrayList<Viewer> vList = seq.getViewers();
			// xMultiCAFE0 pattern: create new viewer only when none exist, else reuse (keep its canvas)
			if (vList == null || vList.size() == 0) {
				// Create viewer with visible=false to prevent flickering
				ViewerFMP viewerKymographs = new ViewerFMP(seqKymographs.getSequence(), false, true);

				List<String> list = IcyCanvas.getCanvasPluginNames();
				String pluginName = list.stream().filter(s -> s.contains("Canvas2DWithTransforms")).findFirst()
						.orElse(null);
				viewerKymographs.setCanvas(pluginName);
				viewerKymographs.setRepeat(false);
				viewerKymographs.addListener(this);

				JToolBar toolBar = viewerKymographs.getToolBar();
				Canvas2DWithTransforms canvas = (Canvas2DWithTransforms) viewerKymographs.getCanvas();
				canvas.customizeToolbarStep2(toolBar);

				// Set position before making viewer visible
				if (initialBounds != null) {
					viewerKymographs.setBounds(initialBounds);
					((Canvas2D) viewerKymographs.getCanvas()).setFitToCanvas(false);
				}

				// Now make the viewer visible with the correct position already set
				viewerKymographs.setVisible(true);

				// Add ComponentListener to track window position changes
				addKymographViewerBoundsListener(viewerKymographs);

				// Force sync for frame 0: LoadSaveLevels may have already called syncROIsForCurrentFrame(0)
				// and set currentFrame=0, so sync would be skipped. Reset so first display always syncs.
				seqKymographs.setCurrentFrame(-1);
				int isel = seqKymographs.getCurrentFrame();
				isel = selectKymographImage(isel);
				selectKymographComboItem(isel);
			} else {
				// Viewer already exists (e.g. auto-created by ICY when loading) - reposition, keep its canvas
				Viewer existingViewer = vList.get(0);
				if (initialBounds != null) {
					// Hide viewer, set bounds, then show to avoid flickering
					boolean wasVisible = existingViewer.isVisible();
					if (wasVisible) {
						existingViewer.setVisible(false);
					}
					existingViewer.setBounds(initialBounds);
					if (existingViewer.getCanvas() instanceof Canvas2D) {
						((Canvas2D) existingViewer.getCanvas()).setFitToCanvas(false);
					}
					if (wasVisible) {
						existingViewer.setVisible(true);
					}
				}
				// Ensure listener is added (safe to call even if already added)
				existingViewer.addListener(this);

				// Add ComponentListener to track window position changes
				addKymographViewerBoundsListener(existingViewer);

				// Sync ROIs and combo/title for current frame (e.g. frame 0) so name shows line0L and ROIs are visible
				int currentT = existingViewer.getPositionT();
				if (currentT < 0)
					currentT = 0;
				seqKymographs.setCurrentFrame(-1);
				selectKymographImage(currentT);
				selectKymographComboItem(currentT);
			}
		}
	}

	private Rectangle calculateKymographViewerBounds(Experiment exp) {
		// Use saved global position if available
		if (globalKymographViewerBounds != null) {
			return globalKymographViewerBounds;
		}

		// Initial positioning logic (original behavior)
		Sequence seqCamData = exp.getSeqCamData().getSequence();
		Viewer viewerCamData = seqCamData.getFirstViewer();
		if (viewerCamData == null)
			return null;

		Sequence seqKymograph = exp.getSeqKymos().getSequence();
		Rectangle rectViewerCamData = viewerCamData.getBounds();
		Rectangle rectImageKymograph = seqKymograph.getBounds2D();
		int desktopwidth = Icy.getMainInterface().getMainFrame().getDesktopWidth();

		Rectangle rectViewerKymograph = (Rectangle) rectViewerCamData.clone();
		rectViewerKymograph.width = (int) rectImageKymograph.getWidth();

		if ((rectViewerKymograph.width + rectViewerKymograph.x) > desktopwidth) {
			rectViewerKymograph.x = 0;
			rectViewerKymograph.y = rectViewerCamData.y + rectViewerCamData.height + 5;
			rectViewerKymograph.width = desktopwidth;
			rectViewerKymograph.height = rectImageKymograph.height;
		} else
			rectViewerKymograph.translate(5 + rectViewerCamData.width, 0);

		return rectViewerKymograph;
	}

	void placeKymoViewerNextToCamViewer(Experiment exp) {
		Sequence seqKymograph = exp.getSeqKymos().getSequence();
		Viewer viewerKymograph = seqKymograph.getFirstViewer();
		if (viewerKymograph == null)
			return;

		// Calculate and set position
		Rectangle bounds = calculateKymographViewerBounds(exp);
		if (bounds != null) {
			viewerKymograph.setBounds(bounds);
			((Canvas2D) viewerKymograph.getCanvas()).setFitToCanvas(false);
		}
	}

	void displayOFF() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqKymos() == null)
			return;
		ArrayList<Viewer> vList = exp.getSeqKymos().getSequence().getViewers();
		if (vList.size() > 0) {
			// Save window position before closing
			for (Viewer v : vList) {
				saveKymographViewerPosition(v);
				v.close();
			}
			vList.clear();
		}
	}

	private void saveKymographViewerPosition(Viewer viewer) {
		// Save position globally - this will be reused for all experiments
		if (viewer != null) {
			globalKymographViewerBounds = viewer.getBounds();
		}
	}

	private void addKymographViewerBoundsListener(Viewer viewer) {
		// Remove existing listener if any
		removeKymographViewerBoundsListener(viewer);

		if (viewer == null) {
			return;
		}

		// Create new listener to track window position changes
		kymographViewerBoundsListener = new ComponentAdapter() {
			@Override
			public void componentMoved(ComponentEvent e) {
				// Save position immediately when window is moved
				if (viewer != null && viewer.isVisible()) {
					saveKymographViewerPosition(viewer);
				}
			}

			@Override
			public void componentResized(ComponentEvent e) {
				// Save position immediately when window is resized
				if (viewer != null && viewer.isVisible()) {
					saveKymographViewerPosition(viewer);
				}
			}
		};

		// Try to get the frame using reflection (Viewer likely has an internal frame)
		try {
			// Try to get the frame using reflection
			java.lang.reflect.Method getFrameMethod = viewer.getClass().getMethod("getFrame");
			Object frameObj = getFrameMethod.invoke(viewer);
			if (frameObj instanceof Component) {
				((Component) frameObj).addComponentListener(kymographViewerBoundsListener);
				return;
			}
		} catch (Exception e) {
			// Reflection failed, try alternative approach
		}

		// Alternative: try to access frame through parent hierarchy
		try {
			// Viewer might extend IcyFrame or have a getParentFrame method
			java.lang.reflect.Method getParentFrameMethod = viewer.getClass().getMethod("getParentFrame");
			Object frameObj = getParentFrameMethod.invoke(viewer);
			if (frameObj instanceof Component) {
				((Component) frameObj).addComponentListener(kymographViewerBoundsListener);
				return;
			}
		} catch (Exception e) {
			// Method doesn't exist, continue
		}

		// Note: If reflection fails, we'll save bounds in viewerChanged instead
	}

	private void removeKymographViewerBoundsListener(Viewer viewer) {
		if (kymographViewerBoundsListener == null || viewer == null) {
			return;
		}

		// Try to get the frame using reflection
		try {
			java.lang.reflect.Method getFrameMethod = viewer.getClass().getMethod("getFrame");
			Object frameObj = getFrameMethod.invoke(viewer);
			if (frameObj instanceof Component) {
				((Component) frameObj).removeComponentListener(kymographViewerBoundsListener);
				kymographViewerBoundsListener = null;
				return;
			}
		} catch (Exception e) {
			// Reflection failed, try alternative approach
		}

		// Alternative: try to access frame through parent hierarchy
		try {
			java.lang.reflect.Method getParentFrameMethod = viewer.getClass().getMethod("getParentFrame");
			Object frameObj = getParentFrameMethod.invoke(viewer);
			if (frameObj instanceof Component) {
				((Component) frameObj).removeComponentListener(kymographViewerBoundsListener);
				kymographViewerBoundsListener = null;
				return;
			}
		} catch (Exception e) {
			// Method doesn't exist, continue
		}

		kymographViewerBoundsListener = null;
	}

	public void displayUpdateOnSwingThread() {
		isActionEnabled = false;
		try {
			int isel = selectKymographImage(displayUpdate());
			selectKymographComboItem(isel);
		} finally {
			isActionEnabled = true;
		}
	}

	int displayUpdate() {
		int item = -1;
		if (kymographsCombo.getItemCount() < 1) {
			return item;
		}

		displayON();

		item = kymographsCombo.getSelectedIndex();
		if (item < 0) {
			item = indexImagesCombo >= 0 ? indexImagesCombo : 0;
			indexImagesCombo = -1;
		}
		return item;
	}

	private void selectKymographComboItem(int isel) {
		if (kymographsCombo.getItemCount() == 0) {
			// Combo box is empty, cannot select any item
			return;
		}
		int icurrent = kymographsCombo.getSelectedIndex();
		if (isel >= 0 && isel < kymographsCombo.getItemCount() && isel != icurrent)
			kymographsCombo.setSelectedIndex(isel);
	}

	public int selectKymographImage(int isel) {
		int selectedImageIndex = -1;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return selectedImageIndex;

		SequenceKymos seqKymos = exp.getSeqKymos();
		if (seqKymos == null || seqKymos.getSequence() == null)
			return selectedImageIndex;
		if (seqKymos.getSequence().isUpdating())
			return selectedImageIndex;

		if (isel < 0)
			isel = 0;
		if (isel >= seqKymos.getSequence().getSizeT())
			isel = seqKymos.getSequence().getSizeT() - 1;

		Viewer v = seqKymos.getSequence().getFirstViewer();
		if (v == null) {
			displayON();
			v = seqKymos.getSequence().getFirstViewer();
		}
		Rectangle savedBounds = null;
		int icurrent = -1;
		icy.sequence.Sequence seq = seqKymos.getSequence();
		seq.beginUpdate();
		try {
			if (v != null) {
				savedBounds = v.getBounds();
				icurrent = v.getPositionT();
				if (icurrent != isel && icurrent >= 0) {
					seqKymos.validateRoisAtT(icurrent);
					Capillary capOld = seqKymos.getCapillaryForFrame(icurrent, exp.getCapillaries());
					if (capOld != null)
						seqKymos.transferKymosRoi_atT_ToCapillaries_Measures(icurrent, capOld);
				}
				if (icurrent != isel)
					v.setPositionT(isel);
			}
			seqKymos.syncROIsForCurrentFrame(isel, exp.getCapillaries());
		} finally {
			seq.endUpdate();
		}

		// Apply saved position if available, otherwise preserve current bounds
		if (v != null) {
			Rectangle boundsToApply = null;
			if (globalKymographViewerBounds != null) {
				boundsToApply = globalKymographViewerBounds;
			} else if (savedBounds != null) {
				boundsToApply = savedBounds;
			}

			if (boundsToApply != null) {
				Rectangle currentBounds = v.getBounds();
				// Only apply if bounds have changed (to avoid unnecessary updates)
				if (!boundsToApply.equals(currentBounds)) {
					v.setBounds(boundsToApply);
					if (v.getCanvas() instanceof Canvas2D) {
						((Canvas2D) v.getCanvas()).setFitToCanvas(false);
					}
				}
			}
		}

		selectedImageIndex = seqKymos.getCurrentFrame();
		applyCentralViewOptionsToKymosViewer(v);
		selectCapillary(exp, selectedImageIndex);
		return selectedImageIndex;
	}

	private void selectCapillary(Experiment exp, int isel) {
		Capillaries capillaries = exp.getCapillaries();

		// First deselect all capillaries
		for (Capillary cap : capillaries.getList()) {
			if (cap.getRoi() != null) {
				cap.getRoi().setSelected(false);
			}
		}

		// Find the capillary that corresponds to the kymograph at position isel
		// Don't use list index - find by kymograph name
		if (isel >= 0 && isel < exp.getSeqKymos().getImagesList().size()) {
			String kymographPath = exp.getSeqKymos().getFileNameFromImageList(isel);
			String kymographName = new java.io.File(kymographPath).getName();

			// Remove extension
			int lastDotIndex = kymographName.lastIndexOf('.');
			if (lastDotIndex > 0) {
				kymographName = kymographName.substring(0, lastDotIndex);
			}

			// Find capillary with matching kymograph name
			Capillary capSel = capillaries.getCapillaryFromKymographName(kymographName);
			if (capSel != null && capSel.getRoi() != null) {
				capSel.getRoi().setSelected(true);
			}
		}
	}

	@Override
	public void viewerChanged(ViewerEvent event) {
		if ((event.getType() == ViewerEvent.ViewerEventType.POSITION_CHANGED) && (event.getDim() == DimensionId.T)) {
			Viewer v = event.getSource();
			int tNew = v.getPositionT();
			if (v.getSequence() == null)
				return;
			Experiment exp = findExperimentOwningSequence(v.getSequence());
			if (exp != null)
				exp.onViewerTPositionChanged(v, tNew, false);
			applyCentralViewOptionsToKymosViewer(v);
			if (tNew >= 0 && tNew < kymographsCombo.getItemCount()) {
				selectKymographComboItem(tNew);
				String title = kymographsCombo.getItemAt(tNew) + "  :" + viewsCombo.getSelectedItem() + " s";
				v.setTitle(title);
			}
		}

		// Fallback: save bounds whenever viewer changes (as backup if ComponentListener
		// doesn't work)
		// This ensures position is saved even if the ComponentListener attachment
		// failed
		Viewer v = event.getSource();
		if (v != null && v.isVisible()) {
			// Only save if this is the kymograph viewer for the current experiment
			Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
			if (exp != null && exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null) {
				ArrayList<Viewer> vList = exp.getSeqKymos().getSequence().getViewers();
				if (vList.contains(v)) {
					saveKymographViewerPosition(v);
				}
			}
		}
	}

	@Override
	public void viewerClosed(Viewer viewer) {
		// Save window position before closing (global position, shared across all
		// experiments)
		saveKymographViewerPosition(viewer);
		// Remove ComponentListener to prevent memory leaks
		removeKymographViewerBoundsListener(viewer);
		viewer.removeListener(this);
	}

	/**
	 * Finds the experiment that owns the given sequence (e.g. its kymograph sequence).
	 * Used so we sync ROIs when the viewer position changes even if the experiment combo selection differs.
	 */
	private Experiment findExperimentOwningSequence(Sequence sequence) {
		if (sequence == null)
			return null;
		for (int i = 0; i < parent0.expListComboLazy.getItemCount(); i++) {
			Experiment exp = parent0.expListComboLazy.getItemAtNoLoad(i);
			if (exp != null && exp.getSeqKymos() != null && exp.getSeqKymos().getSequence() != null
					&& exp.getSeqKymos().getSequence().getId() == sequence.getId()) {
				return exp;
			}
		}
		return null;
	}

	public void updateResultsAvailable(Experiment exp) {
		isActionEnabled = false;
		viewsCombo.removeAllItems();
		List<String> list = Directories.getSortedListOfSubDirectoriesWithTIFF(exp.getExperimentDirectory());
		for (int i = 0; i < list.size(); i++) {
			String dirName = list.get(i);
			if (dirName == null || dirName.contains(Experiment.RESULTS))
				dirName = ".";
			viewsCombo.addItem(dirName);
		}
		isActionEnabled = true;

		String select = exp.getBinSubDirectory();
		if (select == null)
			select = ".";
		viewsCombo.setSelectedItem(select);
	}

	private void changeBinSubdirectory(String localString) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || localString == null || exp.getBinSubDirectory().contains(localString))
			return;

		parent0.expListComboLazy.expListBinSubDirectory = localString;
		exp.setBinSubDirectory(localString);
		exp.getSeqKymos().getSequence().close();
		exp.loadKymographs();
		parent0.paneKymos.updateDialogs(exp);
	}

}
