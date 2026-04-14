package plugins.fmp.multicafe.dlg.cages;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.regex.Matcher;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

import icy.gui.dialog.MessageDialog;
import icy.gui.viewer.Viewer;
import icy.roi.ROI2D;
import icy.sequence.Sequence;
import icy.util.StringUtil;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.kernel.roi.roi2d.ROI2DPoint;

public class Edit extends JPanel implements AddFlyOverlay.Host {
	private static final long serialVersionUID = -5257698990389571518L;
	private static final Color ADD_FLY_ARMED_BG = new Color(255, 100, 100);
	private static final double DEFAULT_FLY_W = 10;
	private static final double DEFAULT_FLY_H = 5;

	private MultiCAFE parent0;
	private JButton findAllButton = new JButton(new String("Find all missed points"));
	private JButton findNextButton = new JButton(new String("Find next missed point"));
	private JButton validateButton = new JButton(new String("Validate selected ROI"));
	private JButton validateAndNextButton = new JButton(new String("Validate and find next"));
	private JToggleButton addFlyButton = new JToggleButton("Add fly");
	private JToggleButton moveFlyButton = new JToggleButton("Move fly");
	private JButton deleteFlyButton = new JButton("Delete fly");
	private JComboBox<String> foundCombo = new JComboBox<String>();
	private int foundT = -1;
	private int foundCell = -1;

	private AddFlyOverlay addFlyOverlay;
	private Sequence addFlyOverlaySequence;

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;
		FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
		flowLayout.setVgap(0);

		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(findAllButton);
		panel1.add(foundCombo);
		add(panel1);

		JPanel panel2 = new JPanel(flowLayout);
		panel2.add(findNextButton);
		panel2.add(validateButton);
		add(panel2);

		JPanel panel3 = new JPanel(flowLayout);
		panel3.add(validateAndNextButton);
		add(panel3);

		addFlyButton.setToolTipText("Click image to add flies; toggle off when done");
		deleteFlyButton.setToolTipText("Delete the selected fly rectangle (detR…)");
		JPanel panel4 = new JPanel(flowLayout);
		panel4.add(addFlyButton);
		panel4.add(moveFlyButton);
		panel4.add(deleteFlyButton);
		add(panel4);

		defineActionListeners();
	}

	private void defineActionListeners() {
		validateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					exp.saveDetRoisToPositions();
			}
		});

		findNextButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					findFirstMissed(exp);
			}
		});

		validateAndNextButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					exp.saveDetRoisToPositions();
					findFirstMissed(exp);
				}
			}
		});

		findAllButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null)
					findAllMissedPoints(exp);
			}
		});

		foundCombo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				if (foundCombo.getItemCount() == 0) {
					return;
				}
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp == null)
					return;
				String filter = (String) foundCombo.getSelectedItem();
				int indexT = StringUtil.parseInt(filter.substring(filter.indexOf("_") + 1), -1);
				if (indexT < 0)
					return;
				selectImageT(exp, indexT);
				List<ROI2D> roiList = exp.getSeqCamData().getSequence().getROI2Ds();
				for (ROI2D roi : roiList) {
					String csName = roi.getName();
					if (roi instanceof ROI2DPoint && csName.equals(filter)) {
						moveROItoCageCenter(exp, roi, indexT);
						selectImageT(exp, roi.getT());
						break;
					}
				}
			}
		});

		addFlyButton.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				if (addFlyButton.isSelected())
					moveFlyButton.setSelected(false);
				boolean armed = addFlyButton.isSelected();
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (armed) {
					if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
						addFlyButton.setSelected(false);
						MessageDialog.showDialog("Load an experiment with a camera sequence first.",
								MessageDialog.WARNING_MESSAGE);
						return;
					}
					Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
					if (v == null) {
						addFlyButton.setSelected(false);
						MessageDialog.showDialog("Open a camera viewer first.", MessageDialog.WARNING_MESSAGE);
						return;
					}
					updateAddFlyButtonAppearance(true);
					attachAddFlyOverlay(exp.getSeqCamData().getSequence());
				} else {
					updateAddFlyButtonAppearance(false);
					if (!moveFlyButton.isSelected())
						detachAddFlyOverlay();
				}
			}
		});

		moveFlyButton.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e) {
				if (moveFlyButton.isSelected())
					addFlyButton.setSelected(false);
				boolean armed = moveFlyButton.isSelected();
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (armed) {
					if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
						moveFlyButton.setSelected(false);
						MessageDialog.showDialog("Load an experiment with a camera sequence first.",
								MessageDialog.WARNING_MESSAGE);
						return;
					}
					Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
					if (v == null) {
						moveFlyButton.setSelected(false);
						MessageDialog.showDialog("Open a camera viewer first.", MessageDialog.WARNING_MESSAGE);
						return;
					}
					attachAddFlyOverlay(exp.getSeqCamData().getSequence());
				} else {
					if (!addFlyButton.isSelected())
						detachAddFlyOverlay();
				}
			}
		});

		deleteFlyButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				addFlyButton.setSelected(false);
				moveFlyButton.setSelected(false);
				deleteSelectedFlyRoi();
			}
		});
	}

	private void updateAddFlyButtonAppearance(boolean armed) {
		addFlyButton.setOpaque(true);
		if (armed)
			addFlyButton.setBackground(ADD_FLY_ARMED_BG);
		else {
			Color d = UIManager.getColor("Button.background");
			addFlyButton.setBackground(d != null ? d : null);
		}
	}

	private void attachAddFlyOverlay(Sequence seq) {
		detachAddFlyOverlay();
		if (seq == null)
			return;
		if (addFlyOverlay == null)
			addFlyOverlay = new AddFlyOverlay(this);
		seq.addOverlay(addFlyOverlay);
		addFlyOverlaySequence = seq;
	}

	private void detachAddFlyOverlay() {
		if (addFlyOverlay != null && addFlyOverlaySequence != null)
			addFlyOverlaySequence.removeOverlay(addFlyOverlay);
		addFlyOverlaySequence = null;
	}

	@Override
	public void onAddFlyImageClick(double imageX, double imageY) {
		if (!addFlyButton.isSelected() && !moveFlyButton.isSelected())
			return;
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null)
			return;
		Viewer v = exp.getSeqCamData().getSequence().getFirstViewer();
		if (v == null)
			return;
		int t = v.getPositionT();
		List<Cage> hits = exp.getCages().findCagesContainingImagePoint(imageX, imageY);
		if (hits.isEmpty()) {
			MessageDialog.showDialog("Click inside a cage to add a fly.", MessageDialog.WARNING_MESSAGE);
			return;
		}
		if (hits.size() > 1) {
			MessageDialog.showDialog("Several cages overlap here; click inside a single cage.",
					MessageDialog.WARNING_MESSAGE);
			return;
		}
		Cage cage = hits.get(0);
		if (addFlyButton.isSelected()) {
			cage.addManualFlyAtImageCoordinates(t, imageX, imageY, exp.getCamImages_ms());
			exp.updateROIsAt(t);
			return;
		}
		if (moveFlyButton.isSelected()) {
			moveSelectedFlyTo(exp, t, cage, imageX, imageY);
			return;
		}
	}

	private void moveSelectedFlyTo(Experiment exp, int t, Cage targetCage, double imageX, double imageY) {
		ROI2D selected = exp.getSeqCamData().getSequence().getSelectedROI2D();
		if (selected == null || selected.getName() == null) {
			MessageDialog.showDialog("Select a fly rectangle (detR…) first.", MessageDialog.WARNING_MESSAGE);
			return;
		}
		Matcher m = Cage.DETR_FLY_ROI_NAME_PATTERN.matcher(selected.getName());
		if (!m.matches()) {
			MessageDialog.showDialog("Selected ROI is not a fly rectangle (expected name detR…_t_idx).",
					MessageDialog.WARNING_MESSAGE);
			return;
		}
		int sourceCageId = Integer.parseInt(m.group(1));
		int roiT = Integer.parseInt(m.group(2));
		int idx = Integer.parseInt(m.group(3));
		if (roiT != t) {
			MessageDialog.showDialog("Selected fly is not at current time T. Go to T=" + roiT + " or re-select at T=" + t + ".",
					MessageDialog.WARNING_MESSAGE);
			return;
		}
		Cage sourceCage = exp.getCages().getCageFromID(sourceCageId);
		if (sourceCage == null) {
			MessageDialog.showDialog("Source cage not found for selected ROI.", MessageDialog.WARNING_MESSAGE);
			return;
		}
		boolean sameCage = (sourceCage.getCageID() == targetCage.getCageID());
		if (sameCage) {
			if (!sourceCage.moveFlyAtFrameCollectIndexTo(t, idx, imageX, imageY, DEFAULT_FLY_W, DEFAULT_FLY_H,
					exp.getCamImages_ms())) {
				MessageDialog.showDialog("Could not move fly position (index mismatch?).", MessageDialog.WARNING_MESSAGE);
				return;
			}
		} else {
			if (!sourceCage.removeFlyAtFrameCollectIndex(t, idx)) {
				MessageDialog.showDialog("Could not remove fly position from source cage (index mismatch?).",
						MessageDialog.WARNING_MESSAGE);
				return;
			}
			targetCage.addManualFlyAtImageCoordinatesWithSize(t, imageX, imageY, DEFAULT_FLY_W, DEFAULT_FLY_H,
					exp.getCamImages_ms());
		}
		exp.updateROIsAt(t);
	}

	private void deleteSelectedFlyRoi() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) {
			MessageDialog.showDialog("No experiment or camera sequence.", MessageDialog.WARNING_MESSAGE);
			return;
		}
		ROI2D selected = exp.getSeqCamData().getSequence().getSelectedROI2D();
		if (selected == null || selected.getName() == null) {
			MessageDialog.showDialog("Select a fly rectangle (detR…) first.", MessageDialog.WARNING_MESSAGE);
			return;
		}
		Matcher m = Cage.DETR_FLY_ROI_NAME_PATTERN.matcher(selected.getName());
		if (!m.matches()) {
			MessageDialog.showDialog("Selected ROI is not a fly rectangle (expected name detR…_t_idx).",
					MessageDialog.WARNING_MESSAGE);
			return;
		}
		int cageId = Integer.parseInt(m.group(1));
		int t = Integer.parseInt(m.group(2));
		int idx = Integer.parseInt(m.group(3));
		Cage cage = exp.getCages().getCageFromID(cageId);
		if (cage == null || cage.getCageRoi2D() == null) {
			MessageDialog.showDialog("Cage not found for this ROI.", MessageDialog.WARNING_MESSAGE);
			return;
		}
		if (!m.group(1).equals(cage.getCageNumberFromRoiName())) {
			MessageDialog.showDialog("ROI cage id does not match cage data.", MessageDialog.WARNING_MESSAGE);
			return;
		}
		int ok = JOptionPane.showConfirmDialog(this, "Delete this fly position?", "Confirm delete",
				JOptionPane.YES_NO_OPTION);
		if (ok != JOptionPane.YES_OPTION)
			return;
		if (!cage.removeFlyAtFrameCollectIndex(t, idx)) {
			MessageDialog.showDialog("Could not remove fly position (index mismatch?).", MessageDialog.WARNING_MESSAGE);
			return;
		}
		exp.updateROIsAt(t);
	}

	void findFirstMissed(Experiment exp) {
		if (findFirst(exp)) {
			selectImageT(exp, foundT);
			Cage cage = exp.getCages().getCageFromID(foundCell);
			String name = "det" + Integer.toString(cage.prop.getCageID()) + "_" + foundT;
			foundCombo.setSelectedItem(name);
		} else
			MessageDialog.showDialog("no missed point found", MessageDialog.INFORMATION_MESSAGE);
	}

	boolean findFirst(Experiment exp) {
		int dataSize = exp.getSeqCamData().getImageLoader().getNTotalFrames();
		foundT = -1;
		foundCell = -1;
		for (int frame = 0; frame < dataSize; frame++) {
			for (Cage cage : exp.getCages().getCageList()) {
				if (frame >= cage.getFlyPositions().getFlyPositionList().size())
					continue;
				Rectangle2D rect = cage.getFlyPositions().getFlyPositionList().get(frame).getRectangle2D();
				if (rect.getX() == -1 && rect.getY() == -1) {
					foundT = cage.getFlyPositions().getFlyPositionList().get(frame).getFlyIndexT();
					foundCell = cage.getCageID();
					return true;
				}
			}
		}
		return (foundT != -1);
	}

	void selectImageT(Experiment exp, int t) {
		Viewer viewer = exp.getSeqCamData().getSequence().getFirstViewer();
		viewer.setPositionT(t);
	}

	void findAllMissedPoints(Experiment exp) {
		foundCombo.removeAllItems();
		int dataSize = exp.getSeqCamData().getImageLoader().getNTotalFrames();
		for (int frame = 0; frame < dataSize; frame++) {
			for (Cage cage : exp.getCages().getCageList()) {
				if (frame >= cage.getFlyPositions().getFlyPositionList().size())
					continue;
				Rectangle2D rect = cage.getFlyPositions().getFlyPositionList().get(frame).getRectangle2D();
				if (rect.getX() == -1 && rect.getY() == -1) {
					String name = "det" + Integer.toString(cage.prop.getCageID()) + "_"
							+ cage.getFlyPositions().getFlyPositionList().get(frame).getFlyIndexT();
					foundCombo.addItem(name);
				}
			}
		}
		if (foundCombo.getItemCount() == 0)
			MessageDialog.showDialog("no missed point found", MessageDialog.INFORMATION_MESSAGE);
	}

	private int getCageNumberFromName(String name) {
		int cagenumber = -1;
		String strCageNumber = name.substring(4, 6);
		try {
			return Integer.parseInt(strCageNumber);
		} catch (NumberFormatException e) {
			return cagenumber;
		}
	}

	void moveROItoCageCenter(Experiment exp, ROI2D roi, int frame) {
		roi.setColor(Color.RED);
		exp.getSeqCamData().getSequence().setSelectedROI(roi);
		String csName = roi.getName();
		int cageNumber = getCageNumberFromName(csName);
		if (cageNumber >= 0) {
			Cage cage = exp.getCages().getCageFromID(cageNumber);
			Rectangle2D rect0 = cage.getFlyPositions().getFlyPositionList().get(frame).getRectangle2D();
			if (rect0.getX() == -1 && rect0.getY() == -1) {
				Rectangle rect = cage.getCageRoi2D().getBounds();
				Point2D point2 = new Point2D.Double(rect.x + rect.width / 2, rect.y + rect.height / 2);
				roi.setPosition2D(point2);
			}
		}
	}

}
