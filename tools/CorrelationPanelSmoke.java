/* Read-only integration smoke test using a real experiment; renders the actual Swing panel.
 * Compile with the Maven dependency classpath and run with -Djava.awt.headless=true.
 * Arguments: experiment directory, output directory. Never saves experiment objects.
 */
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import plugins.fmp.multicafe.dlg.levels.CorrelationIntakePanel;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.experiment.timebase.FrameTimeScale;
import plugins.fmp.multitools.service.CorrelationGulpPreview;
import plugins.fmp.multitools.tools.polyline.Level2D;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class CorrelationPanelSmoke {
    /** Isolate test preferences from the host application's Windows registry. */
    public static final class MemoryPreferencesFactory implements java.util.prefs.PreferencesFactory {
        private final java.util.prefs.Preferences root = new MemoryPreferences(null, "");
        public java.util.prefs.Preferences userRoot() { return root; }
        public java.util.prefs.Preferences systemRoot() { return root; }
    }
    public static final class MemoryPreferences extends java.util.prefs.AbstractPreferences {
        private final Map<String, String> values = new LinkedHashMap<>();
        MemoryPreferences(java.util.prefs.AbstractPreferences parent, String name) { super(parent, name); }
        protected void putSpi(String key, String value) { values.put(key,value); }
        protected String getSpi(String key) { return values.get(key); }
        protected void removeSpi(String key) { values.remove(key); }
        protected void removeNodeSpi() { values.clear(); }
        protected String[] keysSpi() { return values.keySet().toArray(new String[0]); }
        protected String[] childrenNamesSpi() { return new String[0]; }
        protected java.util.prefs.AbstractPreferences childSpi(String name) { return new MemoryPreferences(this,name); }
        protected void syncSpi() { }
        protected void flushSpi() { }
    }
    private static Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }
    private static void layout(Container c) { c.doLayout(); for (Component child : c.getComponents()) if (child instanceof Container) layout((Container) child); }
    private static void render(javax.swing.JPanel panel, Path path) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            panel.setSize(1160, 820); layout(panel);
            BufferedImage image = new BufferedImage(1160, 820, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics(); panel.printAll(g); g.dispose();
            try { ImageIO.write(image, "png", path.toFile()); } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    private static void compute(CorrelationIntakePanel panel) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try { ((JButton) field(panel, "compute")).doClick(); } catch (Exception e) { throw new RuntimeException(e); }
        });
        ((SwingWorker<?, ?>) field(panel, "worker")).get();
        for (int i = 0; i < 100; i++) {
            final boolean[] done = {false};
            SwingUtilities.invokeAndWait(() -> { try { done[0] = field(panel, "preview") != null; } catch (Exception e) { throw new RuntimeException(e); } });
            if (done[0]) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Panel did not publish its preview");
    }
    private static void collectCharts(Container parent,java.util.List<org.jfree.chart.ChartPanel> charts) {
        for(Component child:parent.getComponents()) {
            if(child instanceof org.jfree.chart.ChartPanel)charts.add((org.jfree.chart.ChartPanel)child);
            else if(child instanceof Container)collectCharts((Container)child,charts);
        }
    }
    private static void waitForRefinement(javax.swing.JPanel panel) throws Exception {
        long deadline=System.currentTimeMillis()+30000;
        while(System.currentTimeMillis()<deadline) {
            final boolean[] done={false};
            SwingUtilities.invokeAndWait(()->done[0]=uncheckedField(panel,"worker")==null
                    && !((javax.swing.Timer)uncheckedField(panel,"previewTimer")).isRunning()
                    && uncheckedField(panel,"proposal")!=null);
            if(done[0])return;
            Thread.sleep(25);
        }
        throw new AssertionError("Automatic refinement preview failed: "+((javax.swing.JLabel)field(panel,"status")).getText());
    }
    private static Object uncheckedField(Object object,String name) {
        try{return field(object,name);}catch(Exception error){throw new RuntimeException(error);}
    }
    private static void checkValidationScopes(Experiment exp,Map<String,Capillary> caps,Path output) throws Exception {
        Map<Capillary,double[]> originals=new LinkedHashMap<>();
        for(Capillary cap:caps.values())originals.put(cap,cap.getTopRaw().polylineLevel.ypoints.clone());
        final plugins.fmp.multicafe.dlg.levels.MeniscusRefinementPanel[] ref={null};
        SwingUtilities.invokeAndWait(()->ref[0]=new plugins.fmp.multicafe.dlg.levels.MeniscusRefinementPanel(exp,caps.get("4L"),null));
        javax.swing.JPanel refinement=ref[0];
        try {
            JComboBox<?> scope=(JComboBox<?>)field(refinement,"validationScope");
            if(!"Current cage".equals(scope.getSelectedItem().toString()))throw new AssertionError("Default validation scope changed");
            SwingUtilities.invokeAndWait(()->scope.setSelectedIndex(0));
            waitForRefinement(refinement);
            if(((Map<?,?>)field(refinement,"proposals")).size()!=1)throw new AssertionError("Current-capillary scope prepared other capillaries");
            render(refinement,output.resolve("scope_current_capillary.png"));
            SwingUtilities.invokeAndWait(()->((JButton)uncheckedField(refinement,"accept")).doClick());
            if(!plugins.fmp.multitools.service.MeniscusRefinement.canUndo(caps.get("4L")))throw new AssertionError("Single validation failed");
            for(Capillary cap:caps.values())if(cap!=caps.get("4L") && !java.util.Arrays.equals(originals.get(cap),cap.getTopRaw().polylineLevel.ypoints))
                throw new AssertionError("Single validation changed another capillary");
            SwingUtilities.invokeAndWait(()-> {
                ((JComboBox<?>)uncheckedField(refinement,"capillaries")).setSelectedItem(caps.get("2L"));
                scope.setSelectedIndex(1);
            });
            waitForRefinement(refinement);
            SwingUtilities.invokeAndWait(()->((JButton)uncheckedField(refinement,"undo")).doClick());
            for(Capillary cap:caps.values())if(!java.util.Arrays.equals(originals.get(cap),cap.getTopRaw().polylineLevel.ypoints))
                throw new AssertionError("Undo followed the new scope instead of the last validation");

            SwingUtilities.invokeAndWait(()->scope.setSelectedIndex(2));
            waitForRefinement(refinement);
            Map<?,?> proposals=(Map<?,?>)field(refinement,"proposals");
            if(proposals.size()!=caps.size())throw new AssertionError("All-cage scope missed capillaries");
            Object firstProposal=proposals.get(caps.get("0L"));
            SwingUtilities.invokeAndWait(()->((JComboBox<?>)uncheckedField(refinement,"capillaries")).setSelectedItem(caps.get("4L")));
            if(field(refinement,"worker")!=null || ((javax.swing.Timer)field(refinement,"previewTimer")).isRunning()
                    || ((Map<?,?>)field(refinement,"proposals")).get(caps.get("0L"))!=firstProposal)
                throw new AssertionError("Inspecting another cage restarted the experiment preview");
            Method comparison=refinement.getClass().getDeclaredMethod("comparisonContent");comparison.setAccessible(true);
            final javax.swing.JPanel[] content={null};
            SwingUtilities.invokeAndWait(()-> {
                try {content[0]=(javax.swing.JPanel)comparison.invoke(refinement);}
                catch(Exception error){throw new RuntimeException(error);}
            });
            java.util.List<org.jfree.chart.ChartPanel> charts=new java.util.ArrayList<>();collectCharts(content[0],charts);
            if(charts.get(1).getChart().getXYPlot().getDataset().getSeriesCount()!=2)
                throw new AssertionError("All-cage inspection should display the current cage only");
            render(refinement,output.resolve("scope_all_cages.png"));
            render(content[0],output.resolve("scope_all_cages_current_cage_graph.png"));
            SwingUtilities.invokeAndWait(()->((JButton)uncheckedField(refinement,"accept")).doClick());
            Object validation=field(refinement,"lastValidation");
            if(validation==null || ((plugins.fmp.multitools.service.MeniscusRefinement.Validation)validation).size()!=caps.size())
                throw new AssertionError("All-cage validation did not capture the entire experiment");
            for(Capillary cap:caps.values())if(!plugins.fmp.multitools.service.MeniscusRefinement.canUndo(cap))
                throw new AssertionError("Experiment validation missed "+cap.getLast2ofCapillaryName());
            SwingUtilities.invokeAndWait(()->scope.setSelectedIndex(0));
            waitForRefinement(refinement);
            SwingUtilities.invokeAndWait(()->((JButton)uncheckedField(refinement,"undo")).doClick());
            for(Capillary cap:caps.values())if(!java.util.Arrays.equals(originals.get(cap),cap.getTopRaw().polylineLevel.ypoints))
                throw new AssertionError("Experiment undo did not restore "+cap.getLast2ofCapillaryName());
            if(field(refinement,"lastValidation")!=null)throw new AssertionError("Undone validation remains active");
            System.out.println("PASS: all three validation scopes; exact undo after changing selection/scope; "+caps.size()+" capillaries validated/restored in memory.");
        } finally {SwingUtilities.invokeAndWait(ref[0]::close);}
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("java.util.prefs.PreferencesFactory", MemoryPreferencesFactory.class.getName());
        icy.preferences.IcyPreferences.init();
        Path root = Paths.get(args[0], "results"), output = Paths.get(args[1]);
        Files.createDirectories(output);
        Experiment exp = new Experiment();
        exp.setResultsDirectory(root.toString());
        new plugins.fmp.multitools.experiment.BinDescriptionPersistence().load(exp.getActiveBinDescription(),root.resolve("bin_60").toString());
        exp.setFrameTimeScale(FrameTimeScale.load(root.toString()));
        Map<String, Capillary> caps = new LinkedHashMap<>();
        Map<String, Integer> cageIds = new LinkedHashMap<>();
        boolean section = false;
        Sequence sequence = new Sequence();
        int measuredStart=-1;
        for (String line : Files.readAllLines(root.resolve("CapillariesDescription.csv"), StandardCharsets.UTF_8)) {
            if (line.startsWith("#;")) section = line.startsWith("#;CAPILLARIES;");
            else if(section && line.startsWith("cap_prefix")) {
                int column=java.util.Arrays.asList(line.split(";")).indexOf("cap_measured_x1");
                measuredStart=column<0?-1:column+4; // four variable ROI coordinates follow npoints
            }
            else if (section && !line.startsWith("cap_prefix")) {
                String[] row = line.split(";");
                java.awt.geom.Line2D green=new java.awt.geom.Line2D.Double(Double.parseDouble(row[14]),Double.parseDouble(row[15]),Double.parseDouble(row[16]),Double.parseDouble(row[17]));
                ROI2DLine roi = new ROI2DLine(green); roi.setName("line" + row[0]);
                Capillary cap = new Capillary(roi);
                cageIds.put(row[0], Integer.parseInt(row[4]));
                cap.setCageID(Integer.parseInt(row[4]));
                cap.setKymographIndex(Integer.parseInt(row[1])); cap.setNFlies(Integer.parseInt(row[5]));
                cap.setVolume(Double.parseDouble(row[6])); cap.setPixels(Integer.parseInt(row[7]));
                if(measuredStart>=0 && row.length>=measuredStart+4) {
                    double bx1=Double.parseDouble(row[measuredStart]), by1=Double.parseDouble(row[measuredStart+1]);
                    double bx2=Double.parseDouble(row[measuredStart+2]), by2=Double.parseDouble(row[measuredStart+3]);
                    if(Double.isFinite(bx1+by1+bx2+by2))
                        cap.getPhaseGeometry().initialize(0,green,new java.awt.geom.Line2D.Double(bx1,by1,bx2,by2));
                }
                cap.setKymographName(row[2]); caps.put(row[0], cap);
                BufferedImage image = ImageIO.read(root.resolve("bin_60").resolve(row[3]).toFile());
                sequence.setImage(cap.getKymographIndex(), 0, IcyBufferedImage.createFrom(image));
                exp.getCapillaries().getList().add(cap);
            }
        }
        section = false;
        for (String line : Files.readAllLines(root.resolve("CapillariesMeasures.csv"), StandardCharsets.UTF_8)) {
            if (line.startsWith("#;")) section = line.startsWith("#;TOPRAW;");
            else if (section && !line.startsWith("#") && !line.isEmpty()) {
                String[] row = line.split(";"); int n = Integer.parseInt(row[2]);
                double[] x = new double[n], y = new double[n];
                for (int i = 0; i < n; i++) { x[i] = i; y[i] = Double.parseDouble(row[i + 3]); }
                caps.get(row[0]).getTopRaw().polylineLevel = new Level2D(x, y, n);
            }
        }
        section = false;
        for (String line : Files.readAllLines(root.resolve("CagesDescription.csv"), StandardCharsets.UTF_8)) {
            if (line.startsWith("#;")) section = line.startsWith("#;CAGE;");
            else if (section && !line.startsWith("cageID") && !line.isEmpty()) {
                String[] row = line.split(";");
                int cage = Integer.parseInt(row[0]), flies = Integer.parseInt(row[1]);
                plugins.fmp.multitools.experiment.cage.Cage cageObject=new plugins.fmp.multitools.experiment.cage.Cage();
                cageObject.getProperties().setCageID(cage);
                cageObject.addCapillaryIfUniqueBulkFilteredOnCageID(exp.getCapillaries().getList());
                exp.getCages().addCageIfUnique(cageObject);
                for (Map.Entry<String, Capillary> entry : caps.entrySet())
                    if (cageIds.get(entry.getKey()) == cage) entry.getValue().setNFlies(flies);
            }
        }
        SequenceKymos kymos = new SequenceKymos(); kymos.setSequence(sequence); exp.setSeqKymos(kymos);
        System.out.println("Loaded real experiment images and measures in memory.");
        double[] before = caps.get("1L").getTopRaw().polylineLevel.ypoints.clone();
        final CorrelationIntakePanel[] ref = new CorrelationIntakePanel[1];
        SwingUtilities.invokeAndWait(() -> ref[0] = new CorrelationIntakePanel(exp, caps.get("1L")));
        CorrelationIntakePanel panel = ref[0]; compute(panel);
        CorrelationGulpPreview result = (CorrelationGulpPreview) field(panel, "preview");
        for(int i=1;i<result.raw.length;i++)
            if(Math.abs(result.motion.shift[i]-(result.raw[i]-result.raw[i-1]))>1e-10)
                throw new AssertionError("Phase 2 is not using accepted-level differences");
        if(!result.referenceDescription.contains("no image correlation"))throw new AssertionError("Wrong phase-2 source");
        Capillary control=caps.get("0L");
        if(control!=null){
            double original=control.getTopRaw().polylineLevel.ypoints[1];
            control.getTopRaw().polylineLevel.ypoints[1]=original+1;
            if(result.matchesSources(exp))throw new AssertionError("Control edit did not invalidate intake snapshot");
            control.getTopRaw().polylineLevel.ypoints[1]=original;
        }
        if(exp.getActiveBinDescription().isKymoFromNormedBlue() && !result.referenceDescription.contains("resampled RGB"))
            throw new AssertionError("Resampled calibration was not selected");
        render(panel, output.resolve("panel_1L_automatic.png"));
        Method updateRois = panel.getClass().getDeclaredMethod("updateRois"); updateRois.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> { try { updateRois.invoke(panel); } catch (Exception e) { throw new RuntimeException(e); } });
        SwingUtilities.invokeAndWait(() -> {
            plugins.fmp.multitools.experiment.sequence.CapillaryKymographMeasureBridge bridge =
                    new plugins.fmp.multitools.experiment.sequence.CapillaryKymographMeasureBridge(exp.getSeqKymos());
            bridge.validateROIs(); bridge.validateRoisAtT(caps.get("1L").getKymographIndex());
            for(icy.roi.ROI2D roi:sequence.getROI2Ds())
                if(roi.getName().matches("gulp[0-9]+")) throw new AssertionError("Prototype curve converted into a gulp");
        });
        Method amplitudesMethod = panel.getClass().getDeclaredMethod("candidateAmplitudes"); amplitudesMethod.setAccessible(true);
        double[] intakeAmplitudes = (double[]) amplitudesMethod.invoke(panel);
        Method bottomsMethod = panel.getClass().getDeclaredMethod("candidateBottoms"); bottomsMethod.setAccessible(true);
        double[] bottoms = (double[]) bottomsMethod.invoke(panel);
        Method reviewPanelMethod=panel.getClass().getDeclaredMethod("reviewPanel");reviewPanelMethod.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                javax.swing.JPanel reviewContent=(javax.swing.JPanel)reviewPanelMethod.invoke(panel);
                reviewContent.setSize(780,470);layout(reviewContent);
                BufferedImage reviewImage=new BufferedImage(780,470,BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics=reviewImage.createGraphics();reviewContent.printAll(graphics);graphics.dispose();
                ImageIO.write(reviewImage,"png",output.resolve("review_interval.png").toFile());
                if(!java.util.Arrays.equals(intakeAmplitudes,(double[])amplitudesMethod.invoke(panel)))
                    throw new AssertionError("Review estimate changed intake");
            } catch(Exception e){throw new RuntimeException(e);}
        });
        double lastBottom=Double.NEGATIVE_INFINITY;
        for(int i=1;i<intakeAmplitudes.length;i++) if(intakeAmplitudes[i]>0) {
            double top=bottoms[i]-intakeAmplitudes[i];
            if(top+1e-8<lastBottom) throw new AssertionError("Overlapping intake geometry at " + i);
            lastBottom=bottoms[i];
            String suffix=String.format(java.util.Locale.ROOT,"_xcorr%07d",i);
            boolean found=false;
            for(icy.roi.ROI2D roi:sequence.getROI2Ds()) if(roi.getName().endsWith(suffix)) {
                found=true;
                if(Math.abs(roi.getBounds2D().getMaxY()-bottoms[i])>1e-8 || Math.abs(roi.getBounds2D().getMinY()-top)>1e-8)
                    throw new AssertionError("Native ROI differs from fitted endpoints");
            }
            if(!found) throw new AssertionError("Missing ordered ROI");
        }
        double totalIntake = java.util.Arrays.stream(intakeAmplitudes).sum(), netIntake = 0;
        for (int i = 1; i < intakeAmplitudes.length; i++) if (intakeAmplitudes[i] > 0)
            netIntake += Math.max(0, intakeAmplitudes[i] - (result.evaporation[i] - result.evaporation[i - 1]));
        javax.swing.JCheckBox compensate = (javax.swing.JCheckBox) field(panel, "compensateEvaporation");
        if (!compensate.isSelected()) throw new AssertionError("Net-intake display must be enabled by default");
        double baselineEnd = result.raw[0] + result.evaporation[result.evaporation.length - 1] - result.evaporation[0];
        double expectedEnd = baselineEnd + netIntake;
        int projections = 0;
        for (icy.roi.ROI2D roi : sequence.getROI2Ds()) {
            if (roi.getName().contains("_xcorr_evap")) {
                projections++;
                if (!roi.isReadOnly() || roi.getStroke() != 1 || roi.getT() != 2) throw new AssertionError("Incorrect evaporation ROI style/lifetime");
                if (Math.abs(roi.getBounds2D().getMinY() - result.raw[0]) > 1e-7
                        || Math.abs(roi.getBounds2D().getMaxY() - expectedEnd) > 1e-7)
                    throw new AssertionError("Grey model is not initial raw + evaporation + cumulative intake");
            }
        }
        if (projections == 0) throw new AssertionError("Evaporation projections missing from raw kymograph");
        Map<String,String> originalBars = new LinkedHashMap<>();
        for (icy.roi.ROI2D roi : sequence.getROI2Ds()) if (roi.getName().contains("_xcorr") && !roi.getName().contains("_xcorr_evap"))
            originalBars.put(roi.getName(), roi.getBounds2D().toString());
        SwingUtilities.invokeAndWait(compensate::doClick);
        for (icy.roi.ROI2D roi : sequence.getROI2Ds()) {
            if (roi.getName().contains("_xcorr_evap") && Math.abs(roi.getBounds2D().getMaxY() - (baselineEnd + totalIntake)) > 1e-7)
                throw new AssertionError("Unchecking compensation did not restore full displacement curve");
            if (originalBars.containsKey(roi.getName()) && !originalBars.get(roi.getName()).equals(roi.getBounds2D().toString()))
                throw new AssertionError("Display compensation changed intake bars");
        }
        render(panel, output.resolve("panel_1L_total_amplitudes.png"));
        SwingUtilities.invokeAndWait(compensate::doClick);
        if (!java.util.Arrays.equals(intakeAmplitudes, (double[]) amplitudesMethod.invoke(panel)))
            throw new AssertionError("Display compensation changed intake amplitudes");
        JSpinner threshold = (JSpinner) field(panel, "threshold");
        double initial = ((Number) threshold.getValue()).doubleValue();
        Component plot = (Component) field(panel, "plot");
        Method y = plot.getClass().getDeclaredMethod("y", double.class); y.setAccessible(true);
        int initialY = (Integer) y.invoke(plot, initial);
        SwingUtilities.invokeAndWait(() -> {
            long now = System.currentTimeMillis();
            plot.dispatchEvent(new MouseEvent(plot, MouseEvent.MOUSE_PRESSED, now, 0, 400, initialY, 1, false));
            plot.dispatchEvent(new MouseEvent(plot, MouseEvent.MOUSE_DRAGGED, now+1, 0, 400, initialY-20, 0, false));
            plot.dispatchEvent(new MouseEvent(plot, MouseEvent.MOUSE_RELEASED, now+2, 0, 400, initialY-20, 1, false));
        });
        if (((Number) threshold.getValue()).doubleValue() <= initial) throw new AssertionError("Threshold drag failed");
        Method export = panel.getClass().getDeclaredMethod("writeCsv", Path.class); export.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> { try { export.invoke(panel, output.resolve("panel_preview.csv")); } catch (Exception e) { throw new RuntimeException(e); } });
        String csv = new String(Files.readAllBytes(output.resolve("panel_preview.csv")), StandardCharsets.UTF_8);
        if (!csv.contains("diagnostic_types") || !csv.contains("manual=true") || !csv.contains("grey_subtract_intake_evaporation=true")
                || csv.split("\n").length != result.raw.length + 4) throw new AssertionError("CSV preview incomplete");
        SwingUtilities.invokeAndWait(() -> threshold.setValue(100.));
        Method candidates = panel.getClass().getDeclaredMethod("candidates"); candidates.setAccessible(true);
        for (boolean b : (boolean[]) candidates.invoke(panel)) if (b) throw new AssertionError("Candidate preview did not update with threshold");
        SwingUtilities.invokeAndWait(() -> { try { updateRois.invoke(panel); } catch (Exception e) { throw new RuntimeException(e); } });
        int baselines = 0;
        for (icy.roi.ROI2D roi : sequence.getROI2Ds()) if (roi.getName().contains("_xcorr_evap")) {
            baselines++;
            double expectedBaseline = result.raw[0] + result.evaporation[result.evaporation.length - 1] - result.evaporation[0];
            if (Math.abs(roi.getBounds2D().getMaxY() - expectedBaseline) > 1e-7)
                throw new AssertionError("Stale intake offsets after removing all candidates");
        }
        if (baselines != 1) throw new AssertionError("Pure evaporation model should remain without candidates");
        SwingUtilities.invokeAndWait(() -> { try { ((JButton) field(panel,"automatic")).doClick(); } catch (Exception e) { throw new RuntimeException(e); } });
        if (Math.abs(((Number) threshold.getValue()).doubleValue()-initial)>1e-10) throw new AssertionError("Automatic reset failed");
        SwingUtilities.invokeAndWait(() -> { try { ((JComboBox<?>) field(panel,"capillaries")).setSelectedItem(caps.get("7L")); } catch (Exception e) { throw new RuntimeException(e); } });
        compute(panel); render(panel, output.resolve("panel_7L_automatic.png"));
        SwingUtilities.invokeAndWait(() -> panel.showCapillary(caps.get("2L")));
        compute(panel); render(panel, output.resolve("panel_2L_net_intakes.png"));
        SwingUtilities.invokeAndWait(compensate::doClick);
        render(panel, output.resolve("panel_2L_total_amplitudes.png"));
        javax.swing.JCheckBox episodes = (javax.swing.JCheckBox) field(panel,"persistentEpisodes");
        javax.swing.JCheckBox local = (javax.swing.JCheckBox) field(panel,"localEvaporation");
        SwingUtilities.invokeAndWait(episodes::doClick);
        CorrelationGulpPreview current = (CorrelationGulpPreview) field(panel,"preview");
        boolean[] legacy = plugins.fmp.multitools.service.VerticalCorrelation.candidates(current.residualRate,
                current.motion.reliable, ((Number) threshold.getValue()).doubleValue());
        if (!java.util.Arrays.equals(legacy, (boolean[]) candidates.invoke(panel)))
            throw new AssertionError("Turning episodes off did not restore the single-bin detector");
        SwingUtilities.invokeAndWait(episodes::doClick);
        SwingUtilities.invokeAndWait(compensate::doClick);
        SwingUtilities.invokeAndWait(() -> panel.showCapillary(caps.get("5L")));
        compute(panel); render(panel, output.resolve("panel_5L_episodes.png"));
        SwingUtilities.invokeAndWait(local::doClick);
        compute(panel);
        current = (CorrelationGulpPreview) field(panel,"preview");
        if(!current.localEvaporation) throw new AssertionError("Local evaporation checkbox did not recompute");
        render(panel, output.resolve("panel_5L_local_evaporation.png"));
        SwingUtilities.invokeAndWait(local::doClick);
        compute(panel);
        javax.swing.JCheckBox gaps = (javax.swing.JCheckBox) field(panel,"showGaps");
        double[] beforeGapToggle = (double[]) amplitudesMethod.invoke(panel);
        SwingUtilities.invokeAndWait(gaps::doClick);
        if(!java.util.Arrays.equals(beforeGapToggle,(double[])amplitudesMethod.invoke(panel)))
            throw new AssertionError("Gap markers changed detected consumption");
        for(icy.roi.ROI2D roi:sequence.getROI2Ds()) if(roi.getName().contains("_xcorr_gap_"))
            throw new AssertionError("Gap overlay did not hide");
        SwingUtilities.invokeAndWait(gaps::doClick);
        if(((CorrelationGulpPreview)field(panel,"preview")).localEvaporation)
            throw new AssertionError("Common evaporation did not return after toggling local mode off");
        for(String name:new String[]{"0L","0R","3L","6L","8L","9R"}) {
            if(!caps.containsKey(name)) continue;
            SwingUtilities.invokeAndWait(() -> panel.showCapillary(caps.get(name)));
            compute(panel); render(panel, output.resolve("panel_" + name + "_ordered.png"));
        }
        // Exercise both independent panels on the reported record-3 cases, in memory only.
        for(String name:new String[]{"2L","3L","4L"}) {
            Capillary selected=caps.get(name);if(selected==null)continue;
            double[] original=selected.getTopRaw().polylineLevel.ypoints.clone();
            Capillary partner=caps.get(name.replace("L","R"));
            double[] partnerOriginal=partner.getTopRaw().polylineLevel.ypoints.clone();

            final plugins.fmp.multicafe.dlg.levels.MeniscusRefinementPanel[] refinementRef=new plugins.fmp.multicafe.dlg.levels.MeniscusRefinementPanel[1];
            SwingUtilities.invokeAndWait(()->refinementRef[0]=new plugins.fmp.multicafe.dlg.levels.MeniscusRefinementPanel(exp,selected,null));
            javax.swing.JPanel refinement=refinementRef[0];
            Object guard=field(refinement,"tooltipGuard");
            Object guardListener=field(guard,"listener");
            waitForRefinement(refinement);
            Object initialProposal=field(refinement,"proposal");
            SwingUtilities.invokeAndWait(()->((JSpinner)uncheckedField(refinement,"radius")).setValue(11));
            waitForRefinement(refinement);
            if(field(refinement,"proposal")==initialProposal)throw new AssertionError("Settings did not refresh preview automatically");
            if(field(refinement,"proposal")==null)throw new AssertionError("Refinement preview failed: "+((javax.swing.JLabel)field(refinement,"status")).getText());
            if(!java.util.Arrays.equals(original,selected.getTopRaw().polylineLevel.ypoints))throw new AssertionError("Preview overwrote topraw");
            render(refinement,output.resolve("phase1_"+name+".png"));
            Method comparisonMethod=refinement.getClass().getDeclaredMethod("comparisonContent");comparisonMethod.setAccessible(true);
            final javax.swing.JPanel[] comparisonView={null};
            SwingUtilities.invokeAndWait(()-> {
                try { comparisonView[0]=(javax.swing.JPanel)comparisonMethod.invoke(refinement); }
                catch(Exception error){throw new RuntimeException(error);}
            });
            java.util.List<org.jfree.chart.ChartPanel> comparisonCharts=new java.util.ArrayList<>();
            collectCharts(comparisonView[0],comparisonCharts);
            if(comparisonCharts.size()!=2)throw new AssertionError("Expected level and correction graphs");
            org.jfree.data.xy.XYDataset corrections=comparisonCharts.get(1).getChart().getXYPlot().getDataset();
            if(corrections.getSeriesCount()!=2)throw new AssertionError("Both sides need visible correction curves");
            for(Capillary cap:java.util.Arrays.asList(selected,partner)) {
                plugins.fmp.multitools.service.MeniscusRefinement.Proposal correctionProposal=
                    plugins.fmp.multitools.service.MeniscusRefinementPreview.get(cap);
                int series=corrections.indexOf(cap.getLast2ofCapillaryName()+" correction");
                double[] raw=correctionProposal.original(), refined=correctionProposal.refined();
                for(int i=0;i<raw.length;i++)
                    if(Math.abs(corrections.getYValue(series,i)-(refined[i]-raw[i]))>1e-10)
                        throw new AssertionError("Correction plot does not match the actual proposal");
            }
            if(comparisonCharts.get(1).getChart().getXYPlot().getRangeAxis().getRange().getLowerBound()>=0)
                throw new AssertionError("Correction plot must show both signs around zero");
            render(comparisonView[0],output.resolve("phase1_cage_"+selected.getCageID()+"_comparison.png"));
            if(((Map<?,?>)field(refinement,"proposals")).size()!=2)throw new AssertionError("Did not preview both sides");
            if(!java.util.Arrays.equals(partnerOriginal,partner.getTopRaw().polylineLevel.ypoints))throw new AssertionError("Partner preview overwrote topraw");
            SwingUtilities.invokeAndWait(()-> {
                plugins.fmp.multitools.experiment.sequence.MeasureRoiSync.updateMeasureROIsAt(selected.getKymographIndex(),sequence,
                    plugins.fmp.multitools.experiment.sequence.MeasureRoiSync.MeasureRoiFilter.CAPILLARY_MEASURES,java.util.Collections.emptyList());
                long overlays=sequence.getROIs().stream().filter(r->"true".equals(r.getProperty(plugins.fmp.multitools.service.MeniscusRefinementPreview.ROI_PROPERTY))).count();
                if(overlays!=4)throw new AssertionError("Cage comparison ROIs lost during frame sync: "+overlays);
            });
            plugins.fmp.multitools.experiment.cage.Cage cage=exp.getCages().getCageList().stream().filter(c->c.getCageID()==selected.getCageID()).findFirst().get();
            plugins.fmp.multitools.tools.results.ResultsOptions chartOptions=new plugins.fmp.multitools.tools.results.ResultsOptions();
            chartOptions.resultType=plugins.fmp.multitools.tools.results.EnumResults.TOPRAW;
            chartOptions.buildExcelStepMs=(int)exp.getKymoBin_ms();
            System.out.println("Chart calibration "+name+": volume="+selected.getVolume()+" pixels="+selected.getPixels()+" source="+plugins.fmp.multitools.experiment.capillary.geometry.CapillaryPixelScale.sourceOf(exp));
            org.jfree.data.xy.XYSeriesCollection dataset=new plugins.fmp.multitools.tools.chart.builders.CageCapillarySeriesBuilder().build(exp,cage,chartOptions);
            if(dataset.getSeriesCount()!=4)throw new AssertionError("Cage graph must contain two original and two proposed curves");
            org.jfree.chart.JFreeChart comparison=org.jfree.chart.ChartFactory.createXYLineChart("Cage "+cage.getCageID()+" original/proposed", "Time (min)","Volume (ul)",dataset);
            plugins.fmp.multitools.tools.chart.style.SeriesStyleCodec.applySeriesPaintsFromDescription(dataset,
                (org.jfree.chart.renderer.xy.XYLineAndShapeRenderer)comparison.getXYPlot().getRenderer());
            ImageIO.write(comparison.createBufferedImage(1000,600),"png",output.resolve("cage_"+cage.getCageID()+"_comparison.png").toFile());
            SwingUtilities.invokeAndWait(()->((javax.swing.JButton)uncheckedField(refinement,"accept")).doClick());
            if(!plugins.fmp.multitools.service.MeniscusRefinement.canUndo(selected)
                || !plugins.fmp.multitools.service.MeniscusRefinement.canUndo(partner))throw new AssertionError("Cage acceptance failed");
            CorrelationGulpPreview accepted=CorrelationGulpPreview.computeAccepted(exp,selected,false);
            for(int i=1;i<accepted.raw.length;i++)if(Math.abs(accepted.motion.shift[i]-(accepted.raw[i]-accepted.raw[i-1]))>1e-10)
                throw new AssertionError("Refined input and phase-2 motion disagree");
            SwingUtilities.invokeAndWait(()->panel.showCapillary(selected));compute(panel);
            render(panel,output.resolve("phase2_"+name+"_accepted.png"));
            SwingUtilities.invokeAndWait(()->((javax.swing.JButton)uncheckedField(refinement,"undo")).doClick());
            if(!java.util.Arrays.equals(original,selected.getTopRaw().polylineLevel.ypoints))throw new AssertionError("Undo changed original levels");
            if(accepted.matchesSources(exp))throw new AssertionError("Undo did not invalidate phase 2");
            if(!java.util.Arrays.equals(partnerOriginal,partner.getTopRaw().polylineLevel.ypoints))throw new AssertionError("Cage undo did not restore partner");
            SwingUtilities.invokeAndWait(refinementRef[0]::close);
            for(java.awt.event.AWTEventListener listener:java.awt.Toolkit.getDefaultToolkit().getAWTEventListeners(java.awt.AWTEvent.CONTAINER_EVENT_MASK)) {
                Object actual=listener instanceof java.awt.event.AWTEventListenerProxy ? ((java.awt.event.AWTEventListenerProxy)listener).getListener() : listener;
                if(actual==guardListener)throw new AssertionError("Phase-1 tooltip guard leaked after close");
            }
            for(icy.roi.ROI roi:sequence.getROIs())if("true".equals(roi.getProperty(plugins.fmp.multitools.service.MeniscusRefinementPreview.ROI_PROPERTY)))
                throw new AssertionError("Refinement preview overlay leaked after close");
        }
        checkValidationScopes(exp,caps,output);
        for(icy.roi.ROI2D roi:sequence.getROI2Ds())if(roi.getName().contains("_xcorr_gap_"))
            throw new AssertionError("Horizontal diagnostic markers remain in phase 2");
        Capillary copyCheck = new Capillary();
        copyCheck.getCorrelationGulps().set(new double[]{0,2.25,0,1.5},10,6,1.4,false);
        copyCheck.getCorrelationGulps().setBottomPositions(new double[]{40,42.25,42.5,44});
        copyCheck.copyCorrelationGulpsToGulps();
        copyCheck.getCorrelationGulps().clear();
        java.util.List<icy.roi.ROI2D> copiedRois=copyCheck.getGulpROIsForCapillaryAtT(0);
        if(copiedRois.size()!=2 || Math.abs(copiedRois.get(0).getBounds2D().getMinY()-40)>1e-8
                || Math.abs(copiedRois.get(0).getBounds2D().getMaxY()-42.25)>1e-8
                || Math.abs(copiedRois.get(1).getBounds2D().getMaxY()-44)>1e-8)
            throw new AssertionError("Copy to gulps changed fitted geometry or lost the last bin");
        StringBuffer copiedCsv=new StringBuffer(); copyCheck.getGulps().csvExportDataFlatToRow(copiedCsv,";");
        copyCheck.getGulps().clear(); copyCheck.getGulps().csvImportDataFromRow(copiedCsv.toString().split(";"),0);
        if(Math.abs(copyCheck.getGulpROIsForCapillaryAtT(0).get(1).getBounds2D().getMinY()-42.5)>1e-8)
            throw new AssertionError("Reload changed copied geometry");
        SwingUtilities.invokeAndWait(panel::close);
        for (icy.roi.ROI2D roi : sequence.getROI2Ds()) if (roi.getName().contains("_xcorr_evap")) throw new AssertionError("Display-only projections left behind on close");
        if (!java.util.Arrays.equals(before, caps.get("1L").getTopRaw().polylineLevel.ypoints)) throw new AssertionError("Original levels modified");
        System.out.println("PASS: real 1L/2L/5L/7L preview, ordered geometry, episode/local-evaporation/gap toggles, intake/evaporation overlays, threshold updates, ROI cleanup, saved/copied geometry, CSV export, and unchanged raw levels.");
        sequence.close();
        System.exit(0);
    }
}
