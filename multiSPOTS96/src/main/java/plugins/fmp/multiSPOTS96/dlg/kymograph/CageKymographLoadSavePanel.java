package plugins.fmp.multiSPOTS96.dlg.kymograph;

import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import icy.file.Saver;
import icy.gui.util.FontUtil;
import icy.gui.frame.progress.ProgressFrame;
import icy.image.IcyBufferedImage;
import icy.system.thread.ThreadUtil;
import loci.formats.FormatException;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.ImageFileData;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.series.CageKymographViewerUtil;
import plugins.fmp.multitools.service.KymographService;
import plugins.fmp.multitools.tools.Logger;

/**
 * Load / save cage stacked kymograph TIFFs ({@code kymocage_*.tif*}) from the current results bin,
 * analogous to multiCAFE {@code dlg.kymos.LoadSave} for capillary line kymographs.
 */
public class CageKymographLoadSavePanel extends JPanel {

	private static final long serialVersionUID = 1L;

	private JButton openButtonKymos = new JButton("Load...");
	private JButton saveButtonKymos = new JButton("Save...");
	private MultiSPOTS96 parent0;

	void init(GridLayout capLayout, MultiSPOTS96 parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		JLabel loadsaveText = new JLabel("-> Cage kymographs (TIFF) ", SwingConstants.RIGHT);
		loadsaveText.setFont(FontUtil.setStyle(loadsaveText.getFont(), Font.ITALIC));

		FlowLayout flowLayout = new FlowLayout(FlowLayout.RIGHT);
		flowLayout.setVgap(0);
		JPanel panel1 = new JPanel(flowLayout);
		panel1.add(loadsaveText);
		panel1.add(openButtonKymos);
		panel1.add(saveButtonKymos);
		panel1.validate();
		add(panel1);

		defineActionListeners();
	}

	private void defineActionListeners() {
		openButtonKymos.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null && loadDefaultCageKymographs(exp)) {
					firePropertyChange("KYMOS_OPEN", false, true);
				}
			}
		});

		saveButtonKymos.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
				if (exp != null) {
					String path = exp.getExperimentDirectory();
					saveKymographFiles(path);
					firePropertyChange("KYMOS_SAVE", false, true);
				}
			}
		});
	}

	void saveKymographFiles(String directory) {
		ProgressFrame progress = new ProgressFrame("Save cage kymographs");
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null) {
			progress.close();
			return;
		}
		SequenceKymos seqKymos = exp.getSeqKymos();
		if (seqKymos == null || seqKymos.getSequence() == null) {
			progress.close();
			return;
		}
		if (directory == null) {
			directory = exp.getDirectoryToSaveResults();
			if (directory != null) {
				try {
					Files.createDirectories(Paths.get(directory));
				} catch (IOException e1) {
					Logger.warn("CageKymographLoadSavePanel: could not create directory " + directory, e1);
				}
			}
		}
		String outputpath = directory;
		if (outputpath == null) {
			outputpath = exp.getKymosBinFullDirectory();
		}
		if (outputpath == null) {
			progress.close();
			return;
		}
		JFileChooser f = new JFileChooser(outputpath);
		f.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		int returnedval = f.showSaveDialog(this);
		if (returnedval == JFileChooser.APPROVE_OPTION) {
			outputpath = f.getSelectedFile().getAbsolutePath();
			for (int t = 0; t < seqKymos.getSequence().getSizeT(); t++) {
				String srcPath = seqKymos.getFileNameFromImageList(t);
				String baseName = new File(srcPath).getName();
				int dot = baseName.lastIndexOf('.');
				String stem = dot > 0 ? baseName.substring(0, dot) : baseName;
				String destPath = outputpath + File.separator + stem + ".tiff";
				progress.setMessage("Save: " + stem);
				final File file = new File(destPath);
				final IcyBufferedImage image = seqKymos.getSeqImage(t, 0);
				ThreadUtil.bgRun(new Runnable() {
					@Override
					public void run() {
						try {
							Saver.saveImage(image, file, true);
						} catch (FormatException | IOException e) {
							Logger.warn("CageKymographLoadSavePanel: save failed " + file, e);
						}
					}
				});
			}
		}
		progress.close();
	}

	/**
	 * Loads {@code kymocage_*} files from the current kymographs bin into {@code seqKymos}, mirroring
	 * the multiCAFE kymograph Load/Save panel behaviour for capillary stacks.
	 */
	public boolean loadDefaultCageKymographs(Experiment exp) {
		if (exp.getCages() == null || exp.getCages().cagesList == null || exp.getCages().cagesList.isEmpty()) {
			Logger.warn("CageKymographLoadSavePanel: no cages — cannot resolve kymograph file names");
			return false;
		}
		if (exp.getSeqKymos() == null) {
			exp.setSeqKymos(new SequenceKymos());
		}
		SequenceKymos seqKymos = exp.getSeqKymos();

		String localString = parent0.expListComboLazy.expListBinSubDirectory;
		if (localString == null) {
			exp.checkKymosDirectory(exp.getBinSubDirectory());
			parent0.expListComboLazy.expListBinSubDirectory = exp.getBinSubDirectory();
		} else {
			exp.setBinSubDirectory(localString);
		}

		String binDir = exp.getKymosBinFullDirectory();
		if (binDir == null) {
			Logger.warn("CageKymographLoadSavePanel: kymographs bin directory is null");
			return false;
		}

		List<ImageFileData> myList = seqKymos.createCageSpotKymographFileList(binDir, exp.getCages());
		int nItems = ImageFileData.getExistingFileNames(myList);
		if (nItems > 0) {
			boolean flag = new KymographService().loadImagesFromList(exp.getSeqKymos(), myList, false);
			if (flag) {
				CageKymographViewerUtil.openIfPresent(exp);
			}
			return flag;
		}
		seqKymos.closeSequence();
		return false;
	}
}
