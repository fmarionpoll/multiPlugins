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

public class CorrelationMethodsBenchmark {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.util.prefs.PreferencesFactory", CorrelationPanelSmoke.MemoryPreferencesFactory.class.getName());
        icy.preferences.IcyPreferences.init();
        for (String folder : args) {
        Path root=Paths.get(folder,"results");
        Experiment exp = new Experiment();
        exp.setResultsDirectory(root.toString());
        new plugins.fmp.multitools.experiment.BinDescriptionPersistence().load(exp.getActiveBinDescription(),root.resolve("bin_60").toString());
        exp.setFrameTimeScale(FrameTimeScale.load(root.toString()));
        Map<String, Capillary> caps = new LinkedHashMap<>();
        Map<String, Integer> cageIds = new LinkedHashMap<>();
        boolean section = false;
        Sequence sequence = new Sequence();
        for (String line : Files.readAllLines(root.resolve("CapillariesDescription.csv"), StandardCharsets.UTF_8)) {
            if (line.startsWith("#;")) section = line.startsWith("#;CAPILLARIES;");
            else if (section && !line.startsWith("cap_prefix")) {
                String[] row = line.split(";");
                ROI2DLine roi = new ROI2DLine(); roi.setName("line" + row[0]);
                Capillary cap = new Capillary(roi);
                cageIds.put(row[0], Integer.parseInt(row[4]));
                cap.setKymographIndex(Integer.parseInt(row[1])); cap.setNFlies(Integer.parseInt(row[5]));
                cap.setVolume(Double.parseDouble(row[6])); cap.setPixels(Integer.parseInt(row[7]));
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
                for (Map.Entry<String, Capillary> entry : caps.entrySet())
                    if (cageIds.get(entry.getKey()) == cage) entry.getValue().setNFlies(flies);
            }
        }
        SequenceKymos kymos = new SequenceKymos(); kymos.setSequence(sequence); exp.setSeqKymos(kymos);

        System.out.println("EXPERIMENT " + folder);
        for(String name : caps.keySet()) {
            Capillary cap=caps.get(name); if(cap==null) continue;
            if(cap.getTopRaw().polylineLevel == null || cap.getTopRaw().polylineLevel.npoints != exp.getFrameTimeScale().size()) {
                System.out.println(name + " SKIPPED: saved levels do not match native timestamps"); continue;
            }
            CorrelationGulpPreview p=CorrelationGulpPreview.computeAccepted(exp,cap,false);
            CorrelationGulpPreview old=p;
            plugins.fmp.multitools.service.IntakeEpisodes.Detection detection=plugins.fmp.multitools.service.IntakeEpisodes.detect(
                p.residualRate,p.minutes,p.motion.reliable,p.controlRates,p.noise.automatic,p.noise.automatic,p.correctedLevels);
            int oldCount=0,newCount=0; double oldNet=0, previousBottom=Double.NEGATIVE_INFINITY;
            for(int i=1;i<p.raw.length;i++) {
                if(old.motion.reliable[i] && old.residualRate[i]>old.noise.automatic) {oldCount++;oldNet+=old.residualRate[i]*(old.minutes[i]-old.minutes[i-1]);}
                if(detection.net[i]>0) {
                    newCount++;
                    double top=detection.position[i-1]+p.evaporation[i-1];
                    double bottom=detection.position[i]+p.evaporation[i];
                    if(top+1e-8<previousBottom) throw new AssertionError("Overlapping intakes: " + name + " bin " + i);
                    previousBottom=bottom;
                }
            }
            System.out.printf(java.util.Locale.ROOT,"%s singleBin=%d/%.2fpx episodes=%d bins=%d net=%.2fpx rawNet=%.2fpx local=%.3f reliable=%s quiet=%d firstAcceptedShift=%.2f%n",
                name,oldCount,oldNet,detection.episodes,newCount,java.util.Arrays.stream(detection.net).sum(),
                p.raw[p.raw.length-1]-p.raw[0]-p.evaporation[p.raw.length-1]+p.evaporation[0],
                p.localScale.factor,p.localScale.reliable,p.localScale.quietIntervals,p.motion.shift[2]);
            java.util.List<plugins.fmp.multitools.service.IntakeDiagnostics.Warning> warnings=
                plugins.fmp.multitools.service.IntakeDiagnostics.analyze(p.raw,p.correctedLevels,p.evaporation,p.minutes,
                    p.motion.reliable,detection,p.controlRates,p.noise.automatic,cap.getNFlies()==0);
            for(plugins.fmp.multitools.service.IntakeDiagnostics.Warning warning:warnings)
                System.out.println(" diagnostic: "+warning.label()+" ["+warning.first+"-"+warning.last+"] excess="+warning.excess);
            System.out.println(" gap review spans=" + detection.gaps.size() + "; ordered geometry PASS");
            if(name.equals("2R")) {
                CorrelationGulpPreview local=CorrelationGulpPreview.computeAccepted(exp,cap,true);
                System.out.printf(java.util.Locale.ROOT," local2R modelEnd=%.2f adjustedEnd=%.2f factor=%.3f reliable=%s%n",
                        p.evaporation[p.evaporation.length-1],local.evaporation[local.evaporation.length-1],
                        local.localScale.factor,local.localScale.reliable);
            }
            if(name.equals("5L")) for(int[] span:new int[][]{{43,48},{115,116},{128,130}}) {
                double sum=0; for(int i=span[0];i<=span[1];i++) sum+=detection.net[i];
                System.out.println(" group "+span[0]+"-"+span[1]+"="+sum);
            }
        }
        }
        System.exit(0);
    }
}
