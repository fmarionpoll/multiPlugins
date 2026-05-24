package plugins.fmp.multiSPOTS96.dlg.spotsMeasures2;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import icy.util.XMLUtil;
import plugins.fmp.multiSPOTS96.MultiSPOTS96;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Logger;

/**
 * Manual load/save of color-distance threshold parameters (see {@link BuildSeriesOptions} LimitsOptions XML fragment).
 */
public final class SpotMeasureColorLimitsDialog {

	public static final String ROOT_ELEMENT = "SpotMeasureColorLimits";
	public static final String DEFAULT_FILENAME = "SpotMeasureColorLimits.xml";

	private SpotMeasureColorLimitsDialog() {
	}

	public static void show(Window owner, MultiSPOTS96 plugin, ThresholdColorsPanel panel) {
		JDialog dlg = owner == null ? new JDialog()
				: new JDialog(owner, "Color-distance parameters", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
		if (owner == null) {
			dlg.setTitle("Color-distance parameters");
			dlg.setModalityType(java.awt.Dialog.ModalityType.APPLICATION_MODAL);
		}
		dlg.setLayout(new BorderLayout(8, 8));

		JLabel hint = new JLabel("<html>Load or save reference colors, distances, spot mask thresholds, and fly filter settings.</html>");
		JPanel north = new JPanel(new BorderLayout());
		north.add(hint, BorderLayout.NORTH);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton loadBtn = new JButton("Load from file…");
		JButton saveBtn = new JButton("Save to file…");
		JButton closeBtn = new JButton("Close");
		buttons.add(loadBtn);
		buttons.add(saveBtn);
		buttons.add(closeBtn);

		dlg.add(north, BorderLayout.NORTH);
		dlg.add(buttons, BorderLayout.CENTER);

		loadBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				doLoad(plugin, panel, dlg);
			}
		});
		saveBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				doSave(plugin, panel, dlg);
			}
		});
		closeBtn.addActionListener(e -> dlg.dispose());

		dlg.pack();
		dlg.setLocationRelativeTo(owner);
		dlg.setVisible(true);
	}

	private static File suggestStartDirectory(MultiSPOTS96 plugin) {
		if (plugin == null || plugin.expListComboLazy == null) {
			return null;
		}
		Experiment exp = (Experiment) plugin.expListComboLazy.getSelectedItem();
		if (exp == null) {
			return null;
		}
		String dir = exp.getExperimentDirectory();
		if (dir != null) {
			File f = new File(dir);
			if (f.isDirectory()) {
				return f;
			}
		}
		return null;
	}

	private static void doLoad(MultiSPOTS96 plugin, ThresholdColorsPanel panel, JDialog dlg) {
		JFileChooser fc = new JFileChooser();
		fc.setFileFilter(new FileNameExtensionFilter("XML (*.xml)", "xml"));
		File start = suggestStartDirectory(plugin);
		if (start != null) {
			fc.setCurrentDirectory(start);
		}
		int r = fc.showOpenDialog(dlg);
		if (r != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File f = fc.getSelectedFile();
		if (f == null) {
			return;
		}
		try {
			Document doc = XMLUtil.loadDocument(f.getAbsolutePath());
			if (doc == null) {
				Logger.warn("SpotMeasureColorLimitsDialog: could not parse " + f.getAbsolutePath());
				return;
			}
			Node root = XMLUtil.getRootElement(doc);
			if (root == null) {
				Logger.warn("SpotMeasureColorLimitsDialog: empty document");
				return;
			}
			BuildSeriesOptions o = new BuildSeriesOptions();
			if (!o.loadLimitsOptionsFromParentNode(root)) {
				Logger.warn("SpotMeasureColorLimitsDialog: no LimitsOptions in " + f.getAbsolutePath());
				return;
			}
			panel.applyColorPipelineFromPreset(o);
		} catch (Exception ex) {
			Logger.error("SpotMeasureColorLimitsDialog load failed: " + ex.getMessage(), ex);
		}
	}

	private static void doSave(MultiSPOTS96 plugin, ThresholdColorsPanel panel, JDialog dlg) {
		JFileChooser fc = new JFileChooser();
		fc.setFileFilter(new FileNameExtensionFilter("XML (*.xml)", "xml"));
		File start = suggestStartDirectory(plugin);
		if (start != null) {
			fc.setCurrentDirectory(start);
			fc.setSelectedFile(new File(start, DEFAULT_FILENAME));
		} else {
			fc.setSelectedFile(new File(DEFAULT_FILENAME));
		}
		int r = fc.showSaveDialog(dlg);
		if (r != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File f = fc.getSelectedFile();
		if (f == null) {
			return;
		}
		if (!f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".xml")) {
			f = new File(f.getParentFile(), f.getName() + ".xml");
		}
		try {
			Document doc = XMLUtil.createDocument(true);
			if (doc == null) {
				Logger.warn("SpotMeasureColorLimitsDialog: could not create XML document");
				return;
			}
			Node xmlRoot = XMLUtil.getRootElement(doc, true);
			Node spotRoot = XMLUtil.setElement(xmlRoot, ROOT_ELEMENT);
			BuildSeriesOptions o = new BuildSeriesOptions();
			panel.copyColorPipelineToPreset(o);
			o.saveLimitsOptionsToParentNode(spotRoot);
			XMLUtil.saveDocument(doc, f.getAbsolutePath());
		} catch (Exception ex) {
			Logger.error("SpotMeasureColorLimitsDialog save failed: " + ex.getMessage(), ex);
		}
	}
}
