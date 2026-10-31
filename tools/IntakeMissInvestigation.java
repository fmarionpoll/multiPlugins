/* Read-only reproduction of intake decisions on saved accepted topraw.
 * Args: output directory, experiment directory...; never saves experiment objects.
 * Set -Dintake.local=true to compare the optional local evaporation model.
 * Set -Dintake.image=true for read-only image-motion support diagnostics.
 * Set -Dintake.validateLevels=true to validate edited curves in memory only. */
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.experiment.timebase.FrameTimeScale;
import plugins.fmp.multitools.service.*;

public class IntakeMissInvestigation {
    static final Method SUM, MEDIAN, PLATEAU;
    static {
        try {
            SUM = IntakeEpisodes.class.getDeclaredMethod("sum", double[].class, double[].class, boolean[].class, int.class, int.class);
            MEDIAN = IntakeEpisodes.class.getDeclaredMethod("medianRange", double[].class, int.class, int.class);
            SUM.setAccessible(true); MEDIAN.setAccessible(true);
        } catch (Exception e) { throw new RuntimeException(e); }
        Method plateau = null;
        try {
            plateau = IntakeEpisodes.class.getDeclaredMethod("plateauContrast", double[].class, double[].class, boolean[].class, int.class, int.class);
            plateau.setAccessible(true);
        } catch (NoSuchMethodException legacy) { }
        PLATEAU = plateau;
    }
    static double sum(double[] rate, double[] time, boolean[] valid, int a, int b) throws Exception {
        return (Double)SUM.invoke(null, rate, time, valid, a, b);
    }
    static double median(double[] y, int a, int b) throws Exception { return (Double)MEDIAN.invoke(null, y, a, b); }
    public static void main(String[] args) throws Exception {
        System.setProperty("java.util.prefs.PreferencesFactory", CorrelationPanelSmoke.MemoryPreferencesFactory.class.getName());
        icy.preferences.IcyPreferences.init();
        Path output = Paths.get(args[0]); Files.createDirectories(output);
        List<String[]> annotations = new ArrayList<>();
        String annotationFile = System.getProperty("intake.annotations");
        if (annotationFile != null) for (String line : Files.readAllLines(Paths.get(annotationFile)))
            annotations.add(line.split(";")); // capillary;native bin;added/removed/resized
        try (BufferedWriter summary = Files.newBufferedWriter(output.resolve("summary.csv"));
             BufferedWriter windows = Files.newBufferedWriter(output.resolve("windows.csv"));
             BufferedWriter sweep = Files.newBufferedWriter(output.resolve("threshold-sweep.csv"))) {
            sweep.write("experiment;capillary;flies;threshold_factor;added_bins_recovered_exact;added_bins_recovered_within_one;annotated_added_bins;extra_unedited_bins;removed_bins_still_detected\n");
            summary.write("experiment;capillary;flies;automatic;stored_threshold;stored_options;stored_bins;detected_bins;net_px;usable;intervals;episodes;local_enabled;estimated_scale;scale_reliable;quiet_intervals;applied_scale\n");
            windows.write("experiment;capillary;first;last;first_min;last_min;amount;limit;persistent;anchored;gate\n");
            for (int f = 1; f < args.length; f++) {
                Path experimentPath = Paths.get(args[f]), root = experimentPath.resolve("results");
                String name = experimentPath.getFileName().toString();
                Experiment exp = new Experiment(); exp.setResultsDirectory(root.toString());
                new plugins.fmp.multitools.experiment.BinDescriptionPersistence().load(exp.getActiveBinDescription(), root.resolve("bin_60").toString());
                exp.setFrameTimeScale(FrameTimeScale.load(root.toString()));
                if (!exp.getCapillaries().getPersistence().loadDescriptions(exp.getCapillaries(), root.toString())
                        || !exp.getCapillaries().getPersistence().loadMeasures(exp.getCapillaries(), root.toString()))
                    throw new IllegalStateException("Cannot load " + root);
                Map<Integer, Integer> flies = new HashMap<>(); boolean section = false;
                for (String line : Files.readAllLines(root.resolve("CagesDescription.csv"), StandardCharsets.UTF_8)) {
                    if (line.startsWith("#;")) section = line.startsWith("#;CAGE;");
                    else if (section && !line.startsWith("cageID") && !line.trim().isEmpty()) {
                        String[] row = line.split(";"); flies.put(Integer.parseInt(row[0]), Integer.parseInt(row[1]));
                    }
                }
                Sequence seq = new Sequence();
                for (Capillary cap : exp.getCapillaries().getList()) {
                    if (!flies.containsKey(cap.getCageID())) throw new IllegalStateException("Missing fly count " + cap);
                    cap.setNFlies(flies.get(cap.getCageID()));
                    Path file = root.resolve("bin_60").resolve(cap.getKymographFileName());
                    if (!Files.exists(file)) file = root.resolve("bin_60").resolve(cap.getKymographName() + ".tif");
                    if (!Files.exists(file)) file = root.resolve("bin_60").resolve(cap.getKymographName() + ".tiff");
                    BufferedImage image = ImageIO.read(file.toFile());
                    seq.setImage(cap.getKymographIndex(), 0, IcyBufferedImage.createFrom(image));
                }
                SequenceKymos kymos = new SequenceKymos(); kymos.setSequence(seq); exp.setSeqKymos(kymos);
                if(Boolean.getBoolean("intake.validateLevels")) for(Capillary cap:exp.getCapillaries().getList()) {
                    if(cap.getTopRaw().polylineLevel==null) continue;
                    plugins.kernel.roi.roi2d.ROI2DPolyLine edited=new plugins.kernel.roi.roi2d.ROI2DPolyLine(cap.getTopRaw().polylineLevel);
                    plugins.fmp.multitools.tools.ROI2D.ROI2DUtilities.interpolateMissingPointsAlongXAxis(edited,
                            seq.getImage(cap.getKymographIndex(),0).getSizeX());
                    cap.getTopRaw().setFromROI(edited); // Never persists repairs.
                }
                for (Capillary cap : exp.getCapillaries().getList()) {
                    String id = cap.getLast2ofCapillaryName();
                    String selectedCapillary = System.getProperty("intake.capillary");
                    if (selectedCapillary != null && !selectedCapillary.equals(id)) continue;
                    if (cap.getTopRaw().polylineLevel == null || cap.getTopRaw().polylineLevel.npoints != exp.getFrameTimeScale().size()) {
                        System.out.println("Skipped " + name + " " + id + ": saved levels do not match native timestamps");
                        continue;
                    }
                    boolean local = Boolean.getBoolean("intake.local");
                    CorrelationGulpPreview p = CorrelationGulpPreview.computeAccepted(exp, cap, local);
                    VerticalCorrelation.Result imageMotion = Boolean.getBoolean("intake.image")
                            ? VerticalCorrelation.analyze(p.image,p.raw,10,3,true) : null;
                    IntakeEpisodes.Detection d;
                    try {
                        Method physical = IntakeEpisodes.class.getMethod("detect", double[].class, double[].class, boolean[].class,
                                List.class, double.class, double.class, double[].class, double[].class);
                        d = (IntakeEpisodes.Detection)physical.invoke(null, p.residualRate, p.minutes, p.motion.reliable,
                                p.controlRates, p.noise.automatic, p.noise.automatic, p.correctedLevels, p.evaporation);
                    } catch (NoSuchMethodException legacy) {
                        d = IntakeEpisodes.detect(p.residualRate, p.minutes, p.motion.reliable,
                                p.controlRates, p.noise.automatic, p.noise.automatic, p.correctedLevels,
                                p::supportsIntakeStep);
                    }
                    if (annotationFile != null) for (double factor : new double[] {1, .8, .6}) {
                        IntakeEpisodes.Detection variant = factor == 1 ? d : IntakeEpisodes.detect(p.residualRate,
                                p.minutes, p.motion.reliable, p.controlRates, p.noise.automatic,
                                factor * p.noise.automatic, p.correctedLevels, p::supportsIntakeStep);
                        boolean[] edited = new boolean[p.raw.length];
                        int recovered = 0, nearby = 0, added = 0, extra = 0, removed = 0;
                        for (String[] annotation : annotations) if (annotation.length >= 3 && annotation[0].equals(id)) {
                            int i = Integer.parseInt(annotation[1]);
                            edited[i] = true;
                            if (annotation[2].equals("added")) {
                                added++;
                                if (variant.net[i] > 0) recovered++;
                                boolean found = false;
                                for (int k = Math.max(1, i - 1); k <= Math.min(p.raw.length - 1, i + 1); k++)
                                    found |= variant.net[k] > 0;
                                if (found) nearby++;
                            } else if (annotation[2].equals("removed") && variant.net[i] > 0) removed++;
                        }
                        for (int i = 1; i < p.raw.length; i++)
                            if (!edited[i] && variant.net[i] > 0 && !(d.net[i] > 0)) extra++;
                        sweep.write(name + ";" + id + ";" + cap.getNFlies() + ";" + factor + ";" + recovered
                                + ";" + nearby + ";" + added + ";" + extra + ";" + removed + "\n");
                    }
                    int bins = 0, usable = 0; for (int i = 1; i < p.raw.length; i++) { if (d.net[i] > 0) bins++; if (p.motion.reliable[i]) usable++; }
                    summary.write(name + ";" + id + ";" + cap.getNFlies() + ";" + p.noise.automatic + ";"
                            + cap.getCorrelationGulps().getThresholdPxPerMin() + ";" + cap.getCorrelationGulps().getMethodOptions()
                            + ";" + cap.getCorrelationGulps().countEvents() + ";" + bins + ";" + Arrays.stream(d.net).sum()
                            + ";" + usable + ";" + (p.raw.length - 1) + ";" + d.episodes
                            + ";" + local + ";" + p.localScale.factor + ";" + p.localScale.reliable + ";" + p.localScale.quietIntervals
                            + ";" + (local ? p.localScale.factor : 1) + "\n");
                    String json = "{\"experiment\":\"" + name + "\",\"capillary\":\"" + id + "\",\"threshold\":" + p.noise.automatic
                            + ",\"noise_sigma\":" + p.noise.sigma + ",\"control_rates\":"
                            + p.controlRates.stream().map(Arrays::toString).collect(java.util.stream.Collectors.joining(",", "[", "]"))
                            + ",\"raw\":" + Arrays.toString(p.raw) + ",\"minutes\":" + Arrays.toString(p.minutes)
                            + ",\"evaporation\":" + Arrays.toString(p.evaporation) + ",\"rate\":" + Arrays.toString(p.residualRate)
                            + ",\"valid\":" + Arrays.toString(p.motion.reliable)
                            + ",\"border_fallback\":" + Arrays.toString(p.motion.toprawFallback)
                            + ",\"profile\":" + Arrays.stream(p.motion.profile).map(x->"\""+x.name()+"\"")
                                    .collect(java.util.stream.Collectors.joining(",","[","]"))
                            + ",\"net\":" + Arrays.toString(d.net)
                            + ",\"position\":" + Arrays.toString(d.position)
                            + ",\"estimated_scale\":" + p.localScale.factor + ",\"scale_reliable\":" + p.localScale.reliable
                            + ",\"quiet_intervals\":" + p.localScale.quietIntervals + ",\"applied_scale\":" + (local ? p.localScale.factor : 1) + "}";
                    if (imageMotion != null) json = json.substring(0,json.length()-1)
                            + ",\"image_shift\":" + Arrays.toString(imageMotion.shift)
                            + ",\"image_reliable\":" + Arrays.toString(imageMotion.reliable)
                            + ",\"image_correlation\":" + Arrays.toString(imageMotion.correlation) + "}";
                    Files.write(output.resolve(name + "-" + id + ".json"), json.getBytes(StandardCharsets.UTF_8));
                    boolean investigate = id.endsWith("L") && (name.startsWith("cam02") ? Arrays.asList("1L","3L","6L","7L","8L").contains(id)
                            : name.startsWith("cam03") ? Arrays.asList("4L","8L").contains(id) : Arrays.asList("2L","3L").contains(id));
                    if (!investigate && selectedCapillary == null && !Boolean.getBoolean("intake.windows.all")) continue;
                    for (int width = 1; width <= 6; width++) {
                        List<Double> background = new ArrayList<>();
                        for (double[] control : p.controlRates) {
                            double[] controlLevels = new double[control.length];
                            for (int i = 1; i < control.length; i++)
                                controlLevels[i] = Double.isFinite(control[i]) ? controlLevels[i-1] + control[i] * (p.minutes[i] - p.minutes[i-1]) : 0;
                            for (int a = 1; a + width <= p.raw.length; a++) {
                            double amount = sum(control, p.minutes, null, a, a + width - 1);
                            if (PLATEAU != null) amount = Math.min(amount, (Double)PLATEAU.invoke(null, controlLevels, control, null, a, a + width - 1));
                            if (Double.isFinite(amount)) background.add(amount);
                            }
                        }
                        if (width > 1 && background.size() < 30) continue;
                        background.sort(Double::compare);
                        double empirical = background.isEmpty() ? 0 : background.get(Math.min(background.size() - 1, (int)Math.ceil(.995 * background.size()) - 1));
                        for (int a = 1; a + width <= p.raw.length; a++) {
                            int b = a + width - 1;
                            double amount = sum(p.residualRate, p.minutes, p.motion.reliable, a, b);
                            double limit = Math.max(empirical, p.noise.automatic * (p.minutes[b] - p.minutes[a-1]) / width);
                            double persistent = amount;
                            if (PLATEAU != null) persistent = Math.min(amount,
                                    (Double)PLATEAU.invoke(null, p.correctedLevels, p.residualRate, p.motion.reliable, a, b));
                            else for (int k = Math.max(1,a-2); k <= Math.min(p.raw.length-1,b+2); k++) if (k < a || k > b) {
                                if (!p.motion.reliable[k] || !Double.isFinite(p.residualRate[k])) { persistent = Double.NaN; break; }
                                persistent += Math.min(0, p.residualRate[k] * (p.minutes[k] - p.minutes[k-1]));
                            }
                            double anchored = PLATEAU != null ? persistent : Math.min(persistent, median(p.correctedLevels,b,Math.min(p.raw.length-1,b+2))
                                    - median(p.correctedLevels,Math.max(0,a-3),a-1));
                            String gate = !Double.isFinite(amount) ? "invalid" : !(amount > limit) ? "threshold"
                                    : !(persistent > limit) ? "persistence" : !(anchored > limit) ? "anchor" : "passes";
                            windows.write(name + ";" + id + ";" + a + ";" + b + ";" + p.minutes[a] + ";" + p.minutes[b]
                                    + ";" + amount + ";" + limit + ";" + persistent + ";" + anchored + ";" + gate + "\n");
                        }
                    }
                }
                System.out.println("Read-only replay complete: " + name);
            }
        }
        System.exit(0);
    }
}
