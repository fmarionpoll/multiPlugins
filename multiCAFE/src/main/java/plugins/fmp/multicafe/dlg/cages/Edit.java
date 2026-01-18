package plugins.fmp.multicafe.dlg.cages;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;

import icy.gui.dialog.MessageDialog;
import icy.gui.viewer.Viewer;
import icy.roi.ROI2D;
import icy.util.StringUtil;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cages.cage.Cage;
import plugins.kernel.roi.roi2d.ROI2DPoint;

public class Edit extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = -5257698990389571518L;
	private MultiCAFE parent0;
	private JButton findAllButton = new JButton(new String("Find all missed points"));
	private JButton findNextButton = new JButton(new String("Find next missed point"));
	private JButton validateButton = new JButton(new String("Validate selected ROI"));
	private JButton validateAndNextButton = new JButton(new String("Validate and find next"));
	private JComboBox<String> foundCombo = new JComboBox<String>();
	private int foundT = -1;
	private int foundCell = -1;

	// ----------------------------------------------------

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
