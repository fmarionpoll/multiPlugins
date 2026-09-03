package plugins.fmp.multicafe.dlg.capillaries;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.ArrayList;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JOptionPane;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.SpinnerNumberModel;

import icy.image.IcyBufferedImage;
import icy.gui.frame.progress.ProgressFrame;
import icy.system.thread.ThreadUtil;
import icy.roi.ROI2D;
import icy.roi.ROI;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multicafe.MultiCAFE;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.sequence.SequenceCamData;
import plugins.fmp.multitools.tools.Logger;
import plugins.fmp.multitools.service.FrameSupportBarDetector;
import plugins.fmp.multitools.service.FrameScaleDiagnostic;
import plugins.fmp.multitools.tools.polyline.Line2DPlus;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class Adjust extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1756354919434057560L;

	JSpinner jitterJSpinner = new JSpinner(new SpinnerNumberModel(10, 0, 500, 1));
	private JButton adjustButton = new JButton("Align");
	private JButton elongateButton = new JButton("+");
	private JButton shortenButton = new JButton("-");
	private JButton detectFrameBarButton = new JButton("Detect frame bar...");
	private JButton exportFrameScaleButton = new JButton("Save frame-scale CSV...");
	private JCheckBox allFrameScaleCheckBox = new JCheckBox("ALL (current to last)");
	private static final int ELONGATION_STEP = 1;
	private static final int MIN_LENGTH_PIXELS = 2;
	private MultiCAFE parent0 = null;
	private Line2D refLineUpper = null;
	private Line2D refLineLower = null;
	private ROI2DLine roiRefLineUpper = new ROI2DLine();
	private ROI2DLine roiRefLineLower = new ROI2DLine();
	private List<ROI2DLine> frameBarDiagnostics = new ArrayList<ROI2DLine>();

	void init(GridLayout capLayout, MultiCAFE parent0) {
		setLayout(capLayout);
		this.parent0 = parent0;

		FlowLayout layoutLeft = new FlowLayout(FlowLayout.LEFT);
		layoutLeft.setVgap(0);

		JPanel panel01 = new JPanel(layoutLeft);
		panel01.add(new JLabel("jitter "));
		panel01.add(jitterJSpinner);
		panel01.add(adjustButton);
		add(panel01);

		JPanel panel02 = new JPanel(layoutLeft);
		elongateButton.setPreferredSize(new Dimension(30, 20));
		shortenButton.setPreferredSize(new Dimension(30, 20));
		panel02.add(new JLabel("Change size of capillaries"));
		panel02.add(shortenButton);
		panel02.add(elongateButton);
		add(panel02);

		JPanel panel03 = new JPanel(layoutLeft);
		panel03.add(detectFrameBarButton);
		panel03.add(exportFrameScaleButton);
		panel03.add(allFrameScaleCheckBox);
		detectFrameBarButton.setToolTipText("Detect nine cage dividers and display inferred frame limits");
		exportFrameScaleButton.setToolTipText("Analyze image 0 without changing experiments and save a diagnostic CSV");
		add(panel03);

		defineActionListeners();
	}

	private void defineActionListeners() {
		adjustButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				Object o = e.getSource();
				if (o == adjustButton)
					roisCenterLinestoAllCapillaries();
			}
		});

		elongateButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				changeCapillariesLength(ELONGATION_STEP);
			}
		});

		shortenButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				changeCapillariesLength(-ELONGATION_STEP);
			}
		});

		detectFrameBarButton.addActionListener(e -> detectFrameBar());
		exportFrameScaleButton.addActionListener(e -> exportFrameScaleCsv());
	}

	private void exportFrameScaleCsv() {
		final int first=parent0.expListComboLazy.getSelectedIndex();
		if(first<0){JOptionPane.showMessageDialog(this,"Select an experiment first.","Frame-scale diagnostic",
				JOptionPane.WARNING_MESSAGE);return;}
		JFileChooser chooser=new JFileChooser();
		Experiment selected=parent0.expListComboLazy.getItemAt(first);
		chooser.setSelectedFile(new File(selected.getResultsDirectory(),"frame_scale_diagnostic.csv"));
		if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
		final File output=chooser.getSelectedFile().getName().toLowerCase().endsWith(".csv")
				?chooser.getSelectedFile():new File(chooser.getSelectedFile().getAbsolutePath()+".csv");
		if(output.exists()&&JOptionPane.showConfirmDialog(this,"Replace existing file?\n"+output,
				"Frame-scale diagnostic",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION)return;
		final int last=allFrameScaleCheckBox.isSelected()?parent0.expListComboLazy.getItemCount()-1:first;
		ThreadUtil.bgRun(() -> {
			ProgressFrame progress=new ProgressFrame("Testing frame scale");progress.setLength(last-first+1);
			int ok=0,uncertain=0,errors=0;
			try(BufferedWriter writer=Files.newBufferedWriter(output.toPath(),StandardCharsets.UTF_8)){
				writer.write(FrameScaleDiagnostic.header());writer.newLine();
				for(int i=first;i<=last;i++){
					progress.setMessage("Recording "+(i-first+1)+" of "+(last-first+1));
					FrameScaleDiagnostic row=FrameScaleDiagnostic.analyze(parent0.expListComboLazy.getItemAt(i));
					writer.write(row.csv());writer.newLine();
					if("OK".equals(row.status))ok++;else if("UNCERTAIN".equals(row.status))uncertain++;else errors++;
					progress.incPosition();
				}
			}catch(Exception ex){Logger.error("Frame-scale diagnostic failed",ex);errors++;}
			progress.close();final int nok=ok,nuncertain=uncertain,nerrors=errors;
			SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(Adjust.this,
					"Report saved:\n"+output.getAbsolutePath()+"\n\nOK: "+nok+"   Uncertain: "+nuncertain+"   Errors: "+nerrors,
					"Frame-scale diagnostic",nerrors==0?JOptionPane.INFORMATION_MESSAGE:JOptionPane.WARNING_MESSAGE));
		});
	}

	private void detectFrameBar() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null || exp.getSeqCamData() == null || exp.getSeqCamData().getSequence() == null) return;
		SequenceCamData data = exp.getSeqCamData();
		removeFrameBarDiagnostics(data);
		int t = data.getSequence().getFirstViewer().getPositionT();
		IcyBufferedImage image = data.getSequence().getImage(t, 0, 0);
		if (image == null) {
			JOptionPane.showMessageDialog(this, "The current image could not be read.", "Detect frame bar",
					JOptionPane.ERROR_MESSAGE); return;
		}
		double[] pixels = Array1DUtil.arrayToDoubleArray(image.getDataXY(0), image.isSignedDataType());
		int yMin = image.getSizeY() / 5, yMax = image.getSizeY() * 4 / 5;
		List<ROI2D> caps = data.findROIsMatchingNamePattern("line");
		ArrayList<Double> capillaryX = new ArrayList<Double>();
		if (caps != null && !caps.isEmpty()) {
			Rectangle bounds = new Rectangle(caps.get(0).getBounds());
			for (ROI2D roi : caps) {
				bounds.add(roi.getBounds());
				if (roi instanceof ROI2DLine) {
					Line2D line=((ROI2DLine)roi).getLine();
					capillaryX.add((line.getX1()+line.getX2())/2.);
				}
			}
			yMin = Math.max(2, bounds.y + bounds.height / 6);
			yMax = Math.min(image.getSizeY() - 3, bounds.y + bounds.height * 5 / 6);
		}
		int[] guidedX=FrameSupportBarDetector.internalDividerSearchBounds(capillaryX,image.getSizeX());
		FrameSupportBarDetector.Result result = guidedX==null
				? new FrameSupportBarDetector().detect(pixels,image.getSizeX(),image.getSizeY(),yMin,yMax)
				: new FrameSupportBarDetector().detect(pixels,image.getSizeX(),image.getSizeY(),yMin,yMax,
						guidedX[0],guidedX[1],guidedX[2]);
		if (!result.found()) {
			JOptionPane.showMessageDialog(this, "No sufficiently continuous dark support-bar edge was detected.",
					"Detect frame bar", JOptionPane.WARNING_MESSAGE); return;
		}
		// If the regular nine-divider fit fails, keep the individual validated
		// detections visible so the failure can be understood instead of showing
		// an apparently empty result.
		addDiagnosticLines(data, result.dividers.isEmpty() ? result.dividerCandidates : result.dividers,
				result.dividers.isEmpty() ? "frameBar_candidate_" : "frameBar_divider_");
		if (!result.dividers.isEmpty()) {
			ArrayList<Line2D> outer = new ArrayList<Line2D>();
			outer.add(new Line2D.Double(result.frameLeft, result.upperY-15, result.frameLeft, result.lowerY+40));
			outer.add(new Line2D.Double(result.frameRight, result.upperY-15, result.frameRight, result.lowerY+40));
			addDiagnosticLines(data, outer, "frameBar_outer_");
		}
		String spanText = result.dividers.size()==result.expectedDividers
				? String.format("Frame limits: x = %d to %d (%.0f native pixels)\n",
						result.frameLeft, result.frameRight, result.frameWidth)
				: "Frame span: uncertain\n";
		JOptionPane.showMessageDialog(this, String.format(
				"Upper edge: y = %d px\nDetected internal dividers: %d / %d\n%s"
				+ "Confidence: %.1f",
				result.upperY, result.dividers.size(), result.expectedDividers, spanText, result.confidence),
				"Detect frame bar", JOptionPane.INFORMATION_MESSAGE);
	}

	private void removeFrameBarDiagnostics(SequenceCamData data) {
		// The selected experiment may have changed since the previous click, so
		// object references held by this panel are not a reliable cleanup target.
		// Remove diagnostic ROIs by their reserved name in the current sequence.
		for (ROI roi : new ArrayList<ROI>(data.getSequence().getROIs())) {
			String name = roi.getName();
			if (name != null && name.startsWith("frameBar_"))
				data.getSequence().removeROI(roi);
		}
		frameBarDiagnostics.clear();
	}

	private void addDiagnosticLines(SequenceCamData data, List<Line2D> lines, String prefix) {
		int index = 0;
		for (Line2D line : lines) {
			ROI2DLine roi = new ROI2DLine(line);
			roi.setName(prefix + index++);
			roi.setColor(Color.RED);
			roi.setStroke(3);
			roi.setReadOnly(true);
			data.getSequence().addROI(roi);
			frameBarDiagnostics.add(roi);
		}
	}

	// -------------------------------------------------------
	private void changeCapillariesLength(int deltaY) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData == null)
			return;

		boolean modelChanged = false;
		for (plugins.fmp.multitools.experiment.capillary.Capillary cap : exp.getCapillaries().getList())
			modelChanged |= cap.changeCorridorLengthPixels(deltaY);
		if (modelChanged) {
			int t = seqCamData.getSequence().getFirstViewer().getPositionT();
			exp.getCapillaries().invalidateKymoIntervalsCache();
			exp.updateROIsAt(t);
			exp.save_capillaries_description_and_measures();
			return;
		}

		// Legacy experiments without measured blue endpoints retain the old behavior.
		List<ROI2D> capillaryRois = seqCamData.findROIsMatchingNamePattern("line");
		if (capillaryRois != null && !capillaryRois.isEmpty()) {
			elongateCapillaries(deltaY, capillaryRois);
			clampCapillariesToImageBounds(seqCamData, capillaryRois);
			exp.updateCapillaryRoisAtT(seqCamData.getSequence().getFirstViewer().getPositionT());
		}
	}

	private void elongateCapillaries(int deltaY, List<ROI2D> capillaryRois) {
		for (ROI2D roi : capillaryRois) {
			if (roi instanceof ROI2DLine) {
				ROI2DLine lineRoi = (ROI2DLine) roi;
				Line2D currentLine = lineRoi.getLine();

				double x1 = currentLine.getX1();
				double y1 = currentLine.getY1();
				double x2 = currentLine.getX2();
				double y2 = currentLine.getY2();

				double yTop = Math.min(y1, y2);
				double yBottom = Math.max(y1, y2);

				double deltaYHalf = deltaY / 2.0;
				double newYTop = yTop - deltaYHalf;
				double newYBottom = yBottom + deltaYHalf;
				double newLength = newYBottom - newYTop;

				if (newLength < MIN_LENGTH_PIXELS) {
					continue;
				}

				Line2DPlus linePlus = new Line2DPlus();
				linePlus.setLine(currentLine);

				double newXTop, newXBottom;

				if (Math.abs(x1 - x2) < 1e-10) {
					newXTop = x1;
					newXBottom = x2;
				} else {
					newXTop = linePlus.getXfromY(newYTop);
					newXBottom = linePlus.getXfromY(newYBottom);
				}

				Point2D newP1, newP2;
				if (y1 < y2) {
					newP1 = new Point2D.Double(newXTop, newYTop);
					newP2 = new Point2D.Double(newXBottom, newYBottom);
				} else {
					newP1 = new Point2D.Double(newXBottom, newYBottom);
					newP2 = new Point2D.Double(newXTop, newYTop);
				}

				Line2D newLine = new Line2D.Double(newP1, newP2);
				lineRoi.setLine(newLine);
			}
		}
	}

	private void clampCapillariesToImageBounds(SequenceCamData seqCamData, List<ROI2D> capillaryRois) {
		if (seqCamData == null || seqCamData.getSequence() == null)
			return;

		int imageWidth = seqCamData.getSequence().getWidth();
		int imageHeight = seqCamData.getSequence().getHeight();

		for (ROI2D roi : capillaryRois) {
			if (roi instanceof ROI2DLine) {
				ROI2DLine lineRoi = (ROI2DLine) roi;
				Line2D currentLine = lineRoi.getLine();

				double x1 = currentLine.getX1();
				double y1 = currentLine.getY1();
				double x2 = currentLine.getX2();
				double y2 = currentLine.getY2();

				double yTop = Math.min(y1, y2);
				double yBottom = Math.max(y1, y2);
				double xTop = (y1 < y2) ? x1 : x2;
				double xBottom = (y1 < y2) ? x2 : x1;

				Line2DPlus linePlus = new Line2DPlus();
				linePlus.setLine(currentLine);

				boolean topChanged = false;
				boolean bottomChanged = false;
				boolean isVertical = Math.abs(x1 - x2) < 1e-10;

				if (xTop < 0 && !isVertical) {
					xTop = 0;
					yTop = linePlus.getYfromX(xTop);
					topChanged = true;
				}
				if (yTop < 0) {
					yTop = 0;
					if (!isVertical) {
						xTop = linePlus.getXfromY(yTop);
					}
					topChanged = true;
				}

				if (xBottom < 0 && !isVertical) {
					xBottom = 0;
					yBottom = linePlus.getYfromX(xBottom);
					bottomChanged = true;
				}
				if (yBottom < 0) {
					yBottom = 0;
					if (!isVertical) {
						xBottom = linePlus.getXfromY(yBottom);
					}
					bottomChanged = true;
				}
				if (xBottom >= imageWidth && !isVertical) {
					xBottom = imageWidth - 1;
					yBottom = linePlus.getYfromX(xBottom);
					bottomChanged = true;
				}
				if (yBottom >= imageHeight) {
					yBottom = imageHeight - 1;
					if (!isVertical) {
						xBottom = linePlus.getXfromY(yBottom);
					}
					bottomChanged = true;
				}

				if (topChanged || bottomChanged) {
					Point2D newP1, newP2;
					if (y1 < y2) {
						newP1 = new Point2D.Double(xTop, yTop);
						newP2 = new Point2D.Double(xBottom, yBottom);
					} else {
						newP1 = new Point2D.Double(xBottom, yBottom);
						newP2 = new Point2D.Double(xTop, yTop);
					}

					Line2D clampedLine = new Line2D.Double(newP1, newP2);
					lineRoi.setLine(clampedLine);
				}
			}
		}
	}

	// -------------------------------------------------------
	private void roisCenterLinestoAllCapillaries() {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		SequenceCamData seqCamData = exp.getSeqCamData();
		refLineUpper = roiRefLineUpper.getLine();
		refLineLower = roiRefLineLower.getLine();

		int chan = 0;
		int jitter = (int) jitterJSpinner.getValue();
		int t = seqCamData.getSequence().getFirstViewer().getPositionT();
		seqCamData.getSequence().setPositionT(t);
		IcyBufferedImage vinputImage = seqCamData.getSequence().getImage(t, 0, chan);
		if (vinputImage == null) {
			Logger.warn("Adjust:roisCenterLinestoAllCapillaries() An error occurred while reading image: " + t);
			return;
		}
		int xwidth = vinputImage.getSizeX();
		double[] sourceValues = Array1DUtil.arrayToDoubleArray(vinputImage.getDataXY(0),
				vinputImage.isSignedDataType());

		// loop through all lines and center ROIs on the view
		List<ROI2D> viewerRois = seqCamData.findROIsMatchingNamePattern("line");
		for (ROI2D roi : viewerRois) {
			if (roi instanceof ROI2DLine) {
				Line2D line = roisCenterLinetoCapillary(sourceValues, xwidth, (ROI2DLine) roi, jitter);
				if (line != null)
					((ROI2DLine) roi).setLine(line);
			}
		}
		// transfer ROIs to capillaries
		if (!viewerRois.isEmpty() && !exp.getCapillaries().getList().isEmpty()
				&& !exp.getCapillaries().getList().get(0).getAlongTList().isEmpty()) {
			boolean modelChanged = false;
			for (ROI2D roi : viewerRois) {
				if (!(roi instanceof ROI2DLine))
					continue;
				plugins.fmp.multitools.experiment.capillary.Capillary cap = exp.getCapillaries()
						.getCapillaryFromRoiName(roi.getName());
				if (cap != null)
					modelChanged |= cap.alignPhaseGeometry(t, ((ROI2DLine) roi).getLine());
			}
			if (!modelChanged)
				exp.updateCapillaryRoisAtT(t);
			else {
				exp.getCapillaries().invalidateKymoIntervalsCache();
				exp.updateROIsAt(t);
			}
			exp.save_capillaries_description_and_measures();
		}
	}

	private Line2D roisCenterLinetoCapillary(double[] sourceValues, int xwidth, ROI2DLine roi, int jitter) {

		Line2DPlus line = new Line2DPlus();
		line.setLine(roi.getLine());

		// ----------------------------------------------------------
		// upper position (according to refBar)
		if (!refLineUpper.intersectsLine(line))
			return null;

		Point2D.Double pti = line.getIntersection(refLineUpper);
		double y = pti.getY();
		double x = pti.getX();

		int lowx = (int) x - jitter;
		if (lowx < 0)
			lowx = 0;
		int ixa = (int) x;
		int iya = (int) y;
		double sumVala = 0;
		double[] arrayVala = new double[2 * jitter + 1];
		int iarray = 0;
		for (int ix = lowx; ix <= (lowx + 2 * jitter); ix++, iarray++) {
			arrayVala[iarray] = sourceValues[iya * xwidth + ix];
			sumVala += arrayVala[iarray];
		}
		double avgVala = sumVala / (double) (2 * jitter + 1);

		// find first left < avg
		int ilefta = 0;
		for (int i = 0; i < 2 * jitter; i++) {
			if (arrayVala[i] < avgVala) {
				ilefta = i;
				break;
			}
		}

		// find first right < avg
		int irighta = 2 * jitter;
		for (int i = irighta; i >= 0; i--) {
			if (arrayVala[i] < avgVala) {
				irighta = i;
				break;
			}
		}
		if (ilefta > irighta)
			return null;
		int index = (ilefta + irighta) / 2;
		ixa = lowx + index;

		// find lower position
		if (!refLineLower.intersectsLine(line))
			return null;
		pti = line.getIntersection(refLineLower);
		y = pti.getY();
		x = pti.getX();

		lowx = (int) x - jitter;
		if (lowx < 0)
			lowx = 0;
		int ixb = (int) x;
		int iyb = (int) y;

		double sumValb = 0;
		double[] arrayValb = new double[2 * jitter + 1];
		iarray = 0;
		for (int ix = lowx; ix <= (lowx + 2 * jitter); ix++, iarray++) {
			arrayValb[iarray] = sourceValues[iyb * xwidth + ix];
			sumValb += arrayValb[iarray];
		}
		double avgValb = sumValb / (double) (2 * jitter + 1);

		// find first left < avg
		int ileftb = 0;
		for (int i = 0; i < 2 * jitter; i++) {
			if (arrayValb[i] < avgValb) {
				ileftb = i;
				break;
			}
		}
		// find first right < avg
		int irightb = 2 * jitter;
		for (int i = irightb; i >= 0; i--) {
			if (arrayValb[i] < avgValb) {
				irightb = i;
				break;
			}
		}
		if (ileftb > irightb)
			return null;

		index = (ileftb + irightb) / 2;
		ixb = lowx + index;

		// store result
		double y1 = line.getY1();
		double y2 = line.getY2();
		line.x1 = (double) ixa;
		line.y1 = (double) iya;
		line.x2 = (double) ixb;
		line.y2 = (double) iyb;
		double x1 = line.getXfromY(y1);
		double x2 = line.getXfromY(y2);
		Line2D line_out = new Line2D.Double(x1, y1, x2, y2);

		return line_out;
	}

	void roisDisplayrefBar(boolean display) {
		Experiment exp = (Experiment) parent0.expListComboLazy.getSelectedItem();
		if (exp == null)
			return;
		SequenceCamData seqCamData = exp.getSeqCamData();
		if (seqCamData == null)
			return;

		if (display) {
			// take as ref the whole image otherwise, we won't see the lines if the use has
			// not defined any capillaries
			int seqheight = seqCamData.getSequence().getHeight();
			int seqwidth = seqCamData.getSequence().getWidth();
			refLineUpper = new Line2D.Double(0, seqheight / 3, seqwidth, seqheight / 3);
			refLineLower = new Line2D.Double(0, 2 * seqheight / 3, seqwidth, 2 * seqheight / 3);

			List<ROI2D> capillaryRois = seqCamData.findROIsMatchingNamePattern("line");
			Rectangle extRect = new Rectangle(capillaryRois.get(0).getBounds());
			for (ROI2D roi : capillaryRois) {
				Rectangle rect = roi.getBounds();
				extRect.add(rect);
			}
			extRect.grow(extRect.width * 1 / 10, -extRect.height * 2 / 10);
			refLineUpper.setLine(extRect.getX(), extRect.getY(), extRect.getX() + extRect.getWidth(), extRect.getY());
			refLineLower.setLine(extRect.getX(), extRect.getY() + extRect.getHeight(),
					extRect.getX() + extRect.getWidth(), extRect.getY() + extRect.getHeight());

			roiRefLineUpper.setLine(refLineUpper);
			roiRefLineLower.setLine(refLineLower);

			roiRefLineUpper.setName("refBarUpper");
			roiRefLineUpper.setColor(Color.YELLOW);
			roiRefLineLower.setName("refBarLower");
			roiRefLineLower.setColor(Color.YELLOW);

			seqCamData.getSequence().addROI(roiRefLineUpper);
			seqCamData.getSequence().addROI(roiRefLineLower);
		} else {
			seqCamData.getSequence().removeROI(roiRefLineUpper);
			seqCamData.getSequence().removeROI(roiRefLineLower);
		}
	}
}
