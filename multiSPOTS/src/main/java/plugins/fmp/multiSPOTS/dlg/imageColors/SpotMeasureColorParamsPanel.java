package plugins.fmp.multiSPOTS.dlg.imageColors;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import icy.util.XMLUtil;
import plugins.fmp.multiSPOTS.MultiSPOTS;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.series.options.BuildSeriesOptions;
import plugins.fmp.multitools.tools.Logger;

/**
 * Load/save UI for color-distance detection presets (XML {@code SpotMeasureColorLimits} /
 * {@code LimitsOptions}). Shown inside a modal dialog from {@link DetectColorPanel}.
 */
public class SpotMeasureColorParamsPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	public static final String ROOT_ELEMENT = "SpotMeasureColorLimits";
	public static final String DEFAULT_FILENAME = "SpotMeasureColorLimits.xml";

	private final MultiSPOTS plugin;
	private final DetectColorPanel detectColorPanel;

	public SpotMeasureColorParamsPanel(MultiSPOTS plugin, DetectColorPanel detectColorPanel) {
		this.plugin = plugin;
		this.detectColorPanel = detectColorPanel;
		setLayout(new BorderLayout(12, 12));
		setBorder(new EmptyBorder(12, 12, 12, 12));

		String html = "<html><body style='width:420px'>"
				+ "<p>Load or save an XML preset for <b>Color detect</b>: reference and exclude color lists, "
				+ "distance metric (L1/L2), color space, spot mask direction and threshold, and fly-filter transform/threshold/direction.</p>"
				+ "<p>Default file name when saving: <code>" + DEFAULT_FILENAME + "</code> (under the current experiment folder when available).</p>"
				+ "</body></html>";
		add(new JLabel(html), BorderLayout.NORTH);

		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton loadBtn = new JButton("Load from file…");
		JButton saveBtn = new JButton("Save to file…");
		row.add(loadBtn);
		row.add(saveBtn);
		add(row, BorderLayout.CENTER);

		loadBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				doLoad(SpotMeasureColorParamsPanel.this);
			}
		});
		saveBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				doSave(SpotMeasureColorParamsPanel.this);
			}
		});
	}

	private File suggestStartDirectory() {
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

	private void doLoad(Component parent) {
		JFileChooser fc = new JFileChooser();
		fc.setFileFilter(new FileNameExtensionFilter("XML (*.xml)", "xml"));
		File start = suggestStartDirectory();
		if (start != null) {
			fc.setCurrentDirectory(start);
		}
		int r = fc.showOpenDialog(parent);
		if (r != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File f = fc.getSelectedFile();
		if (f == null || detectColorPanel == null) {
			return;
		}
		try {
			Document doc = XMLUtil.loadDocument(f.getAbsolutePath());
			if (doc == null) {
				Logger.warn("SpotMeasureColorParamsPanel: could not parse " + f.getAbsolutePath());
				return;
			}
			Node root = XMLUtil.getRootElement(doc);
			if (root == null) {
				Logger.warn("SpotMeasureColorParamsPanel: empty document");
				return;
			}
			BuildSeriesOptions o = new BuildSeriesOptions();
			if (!o.loadLimitsOptionsFromParentNode(root)) {
				Logger.warn("SpotMeasureColorParamsPanel: no LimitsOptions in " + f.getAbsolutePath());
				return;
			}
			detectColorPanel.applyColorPipelineFromPreset(o);
		} catch (Exception ex) {
			Logger.error("SpotMeasureColorParamsPanel load failed: " + ex.getMessage(), ex);
		}
	}

	private void doSave(Component parent) {
		JFileChooser fc = new JFileChooser();
		fc.setFileFilter(new FileNameExtensionFilter("XML (*.xml)", "xml"));
		File start = suggestStartDirectory();
		if (start != null) {
			fc.setCurrentDirectory(start);
			fc.setSelectedFile(new File(start, DEFAULT_FILENAME));
		} else {
			fc.setSelectedFile(new File(DEFAULT_FILENAME));
		}
		int r = fc.showSaveDialog(parent);
		if (r != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File f = fc.getSelectedFile();
		if (f == null || detectColorPanel == null) {
			return;
		}
		if (!f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".xml")) {
			f = new File(f.getParentFile(), f.getName() + ".xml");
		}
		try {
			Document doc = XMLUtil.createDocument(true);
			if (doc == null) {
				Logger.warn("SpotMeasureColorParamsPanel: could not create XML document");
				return;
			}
			Node xmlRoot = XMLUtil.getRootElement(doc, true);
			Node spotRoot = XMLUtil.setElement(xmlRoot, ROOT_ELEMENT);
			BuildSeriesOptions o = new BuildSeriesOptions();
			detectColorPanel.copyColorPipelineToPreset(o);
			o.saveLimitsOptionsToParentNode(spotRoot);
			XMLUtil.saveDocument(doc, f.getAbsolutePath());
		} catch (Exception ex) {
			Logger.error("SpotMeasureColorParamsPanel save failed: " + ex.getMessage(), ex);
		}
	}
}
