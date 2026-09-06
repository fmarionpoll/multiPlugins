package plugins.fmp.multitools.service.tracking;

import java.awt.geom.Line2D;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.geometry.CorridorExtensionRatios;
import plugins.fmp.multitools.service.FrameSupportBarDetector;
import plugins.fmp.multitools.service.SequenceLoaderService;

/** Full-stack prototype. Scanning is read-only; apply only after successful completion. */
public final class RackTrackingService {
    public static final class Scan {
        public final Map<Capillary, Map<Long, Line2D>> geometry = new LinkedHashMap<>();
        public final List<String> report = new ArrayList<>();
        public int frames, uncertain, phases = 1;
        public boolean cancelled;
        public Path reportPath;
        private double phaseX, phaseY;
        private int phaseStart;

        void accept(int frame, RackTranslationTracker.Estimate estimate, double threshold) {
            if (!estimate.reliable) uncertain++;
            else if (Math.hypot(estimate.dx-phaseX, estimate.dy-phaseY) >= threshold) {
                phaseX = estimate.dx; phaseY = estimate.dy; phaseStart = frame; phases++;
                for (Map<Long, Line2D> poses : geometry.values())
                    poses.put((long) frame, RackTranslationTracker.translate(poses.get(0L), phaseX, phaseY));
            }
            report.add(frame + "," + estimate.dx + "," + estimate.dy + "," + estimate.confidence
                    + "," + (estimate.reliable ? "ACCEPTED" : "UNCERTAIN") + "," + phaseStart + "," + estimate.reason);
        }

        public void apply() {
            if (cancelled || frames == 0) throw new IllegalStateException("Incomplete scan");
            for (Map.Entry<Capillary, Map<Long, Line2D>> e : geometry.entrySet()) {
                if (e.getKey().getPhaseGeometry().getExtensions() == null) {
                    icy.roi.ROI2D green = e.getKey().getRoiAtFrameT(0);
                    if (green instanceof plugins.kernel.roi.roi2d.ROI2DLine)
                        e.getKey().getPhaseGeometry().initialize(0,
                                ((plugins.kernel.roi.roi2d.ROI2DLine) green).getLine(), e.getValue().get(0L));
                    else e.getKey().getPhaseGeometry().setExtensions(new CorridorExtensionRatios(0, 0));
                }
                e.getKey().getPhaseGeometry().replaceBlueKeyframes(e.getValue());
            }
        }
    }

    public Scan scan(Experiment experiment, double phaseThreshold, BooleanSupplier cancelled,
            IntConsumer progress) throws Exception {
        if (!Double.isFinite(phaseThreshold) || phaseThreshold <= 0)
            throw new IllegalArgumentException("Phase threshold must be positive");
        Scan result = new Scan();
        Map<Integer, List<Double>> cages = new TreeMap<>();
        for (Capillary cap : experiment.getCapillaries().getList()) {
            // Never fall back to a green corridor or a future blue phase.
            Line2D blue = cap.getPhaseGeometry().getBlueStartingAt(0);
            if (blue == null && cap.getProperties().hasMeasuredEndpoints())
                blue = new Line2D.Double(cap.getProperties().getMeasuredStart(), cap.getProperties().getMeasuredEnd());
            if (blue == null || !(blue.getP1().distance(blue.getP2()) > 0)) continue;
            Map<Long, Line2D> phases = new TreeMap<>(); phases.put(0L, blue);
            result.geometry.put(cap, phases);
            cages.computeIfAbsent(cap.getCageID(), k -> new ArrayList<>()).add((blue.getX1()+blue.getX2())/2);
        }
        if (result.geometry.isEmpty()) throw new IllegalArgumentException("No measured image-zero blue ROIs");
        SequenceLoaderService loader = new SequenceLoaderService();
        IcyBufferedImage first = loader.imageIORead(experiment.getSeqCamData().getFileNameFromImageList(0));
        if (first == null) throw new IllegalArgumentException("Cannot read image 0");
        int width = first.getSizeX(), height = first.getSizeY();
        List<Double> centers = new ArrayList<>();
        for (List<Double> x : cages.values()) centers.add(x.stream().mapToDouble(Double::doubleValue).average().getAsDouble());
        double[] guide = FrameSupportBarDetector.frameGridGuideFromCageCenters(centers, width);
        if (guide == null) throw new IllegalArgumentException("Insufficient cage layout for rack detection");
        double[] pixels = pixels(first);
        FrameSupportBarDetector.Result rack = new FrameSupportBarDetector().detectUsingFrameGrid(
                pixels, width, height, height/5, height*4/5, guide);
        RackTranslationTracker tracker = new RackTranslationTracker(pixels, width, height, rack);
        int count = experiment.getSeqCamData().getImageLoader().getNTotalFrames();
        result.report.add("frame,dx_px,dy_px,confidence,status,phase_start,reason");
        result.report.add("0,0,0,1,REFERENCE,0,image-zero");
        for (int frame = 1; frame < count; frame++) {
            if (cancelled.getAsBoolean()) { result.cancelled = true; return result; }
            RackTranslationTracker.Estimate estimate;
            try {
                IcyBufferedImage image = loader.imageIORead(experiment.getSeqCamData().getFileNameFromImageList(frame));
                estimate = image == null ? RackTranslationTracker.Estimate.uncertain("unreadable image")
                        : tracker.estimate(pixels(image), image.getSizeX(), image.getSizeY());
            } catch (Exception e) { estimate = RackTranslationTracker.Estimate.uncertain("image read failed"); }
            result.accept(frame, estimate, phaseThreshold);
            progress.accept(frame);
        }
        if (cancelled.getAsBoolean()) { result.cancelled = true; return result; }
        result.frames = count;
        Path directory = Paths.get(experiment.getResultsDirectory());
        Files.createDirectories(directory);
        result.reportPath = Files.createTempFile(directory, "RackTracking-", ".csv");
        Files.write(result.reportPath, result.report, StandardCharsets.UTF_8);
        return result;
    }

    private static double[] pixels(IcyBufferedImage image) {
        return Array1DUtil.arrayToDoubleArray(image.getDataXY(0), image.isSignedDataType());
    }
}
