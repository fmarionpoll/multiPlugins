package plugins.fmp.multitools.service;

import java.awt.geom.Line2D;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import icy.image.IcyBufferedImage;
import icy.type.collection.array.Array1DUtil;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.ExperimentDirectories;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.sequence.ImageLoader;
import plugins.kernel.roi.roi2d.ROI2DLine;

/** One-row, read-only image-0 diagnostic used by the temporary Adjust UI. */
public final class FrameScaleDiagnostic {
    public String recordName="", path="", status="ERROR", message="";
    public int dividers, groundTruthCapillaries;
    public double frameWidth=Double.NaN, medianPitch=Double.NaN, pitchMadPercent=Double.NaN;
    public double medianGroundTruthLength=Double.NaN, medianGreenLength=Double.NaN;

    public static FrameScaleDiagnostic analyze(Experiment exp) {
        FrameScaleDiagnostic row=new FrameScaleDiagnostic();
        row.path=exp.getResultsDirectory(); row.recordName=recordName(row.path);
        try {
            exp.loadExperimentDescriptors(); exp.loadMCCapillaries_Only();
            String images=exp.getImagesDirectory();
            if(images==null) images=new File(exp.getResultsDirectory()).getParentFile().getAbsolutePath();
            List<String> files=ExperimentDirectories.getImagesListFromPathV2(images,"jpg");
            if(files.isEmpty()) throw new IllegalArgumentException("no JPG image");
            IcyBufferedImage image=new ImageLoader().imageIORead(files.get(0));
            if(image==null) throw new IllegalArgumentException("image 0 could not be read");
            double[] pixels=Array1DUtil.arrayToDoubleArray(image.getDataXY(0),image.isSignedDataType());
            ArrayList<Double> capillaryX=new ArrayList<Double>();
            java.util.Map<Integer,ArrayList<Double>> xByCage=new java.util.TreeMap<Integer,ArrayList<Double>>();
            for(Capillary cap:exp.getCapillaries().getList()) if(cap.getRoi() instanceof ROI2DLine) {
                Line2D line=((ROI2DLine)cap.getRoi()).getLine();
                double x=(line.getX1()+line.getX2())/2.; capillaryX.add(x);
                xByCage.computeIfAbsent(cap.getCageID(),key->new ArrayList<Double>()).add(x);
            }
            int[] guidedX=FrameSupportBarDetector.internalDividerSearchBounds(capillaryX,image.getSizeX());
            ArrayList<Double> cageCenters=new ArrayList<Double>();
            for(ArrayList<Double> values:xByCage.values()){
                double sum=0.;for(double x:values)sum+=x;cageCenters.add(sum/values.size());}
            double[] frameGuide=FrameSupportBarDetector.frameGridGuideFromCageCenters(cageCenters,image.getSizeX());
            FrameSupportBarDetector detector=new FrameSupportBarDetector();
            FrameSupportBarDetector.Result detected=guidedX==null
                    ? detector.detectUsingFrameGrid(pixels,image.getSizeX(),image.getSizeY(),image.getSizeY()/5,
                            image.getSizeY()*4/5,frameGuide)
                    : detector.detect(pixels,image.getSizeX(),image.getSizeY(),image.getSizeY()/5,
                            image.getSizeY()*4/5,guidedX[0],guidedX[1],guidedX[2]);
            if(frameGuide!=null && (detected.dividers.isEmpty()
                    || FrameSupportBarDetector.pitchDisagreesWithGuide(detected,frameGuide,.15)))
                detected=detector.detectUsingFrameGrid(pixels,image.getSizeX(),image.getSizeY(),
                        image.getSizeY()/5,image.getSizeY()*4/5,frameGuide);
            row.dividers=detected.dividers.size();
            if(row.dividers==detected.expectedDividers) {
                row.frameWidth=detected.frameWidth;
                ArrayList<Double> pitch=new ArrayList<Double>();
                for(int i=1;i<detected.dividers.size();i++)
                    pitch.add(detected.dividers.get(i).getX1()-detected.dividers.get(i-1).getX1());
                row.medianPitch=median(pitch);
                ArrayList<Double> deviations=new ArrayList<Double>();
                for(double value:pitch)deviations.add(Math.abs(value-row.medianPitch));
                row.pitchMadPercent=100.*median(deviations)/row.medianPitch;
            }
            ArrayList<Double> green=new ArrayList<Double>();
            for(Capillary cap:exp.getCapillaries().getList()) if(cap.getRoi() instanceof ROI2DLine)
                green.add(((ROI2DLine)cap.getRoi()).getLine().getP1().distance(
                        ((ROI2DLine)cap.getRoi()).getLine().getP2()));
            row.medianGreenLength=median(green);
            try {
                File gt=CapillaryGroundTruthLoader.findFile(new File(exp.getResultsDirectory()));
                ArrayList<Double> lengths=readGroundTruthLengths(gt);
                row.groundTruthCapillaries=lengths.size(); row.medianGroundTruthLength=median(lengths);
            } catch(Exception noGroundTruth) { /* valid diagnostic without annotations */ }
            row.status=row.dividers==detected.expectedDividers ? "OK" : "UNCERTAIN";
			row.message=row.dividers==detected.expectedDividers ? ""
					: "expected "+detected.expectedDividers+" internal dividers";
        } catch(Exception e) { row.message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(); }
        return row;
    }

    private static ArrayList<Double> readGroundTruthLengths(File file) throws Exception {
        ArrayList<Double> result=new ArrayList<Double>(); boolean table=false,header=false;
        try(BufferedReader r=new BufferedReader(new FileReader(file))) {String line;
            while((line=r.readLine())!=null){if(line.startsWith("cap_prefix;")){table=true;header=true;continue;}
                if(!table)continue;if(line.startsWith("#"))break;if(!header||line.trim().isEmpty())continue;
                String[] f=line.split(";",-1);if(f.length<18)continue;int i=f.length-4;
                double x1=Double.parseDouble(f[i]),y1=Double.parseDouble(f[i+1]);
                double x2=Double.parseDouble(f[i+2]),y2=Double.parseDouble(f[i+3]);
                double length=Math.hypot(x2-x1,y2-y1);if(Double.isFinite(length)&&length>0)result.add(length);
            }} return result;
    }
    private static double median(List<Double> values) {if(values==null||values.isEmpty())return Double.NaN;
        ArrayList<Double> copy=new ArrayList<Double>(values);Collections.sort(copy);int n=copy.size();
        return n%2==1?copy.get(n/2):(copy.get(n/2-1)+copy.get(n/2))/2.;}
    private static String recordName(String results) {if(results==null)return "";File f=new File(results).getParentFile();
        ArrayList<String> parts=new ArrayList<String>();while(f!=null&&parts.size()<4){String n=f.getName();
            if(!"grabs".equalsIgnoreCase(n))parts.add(n);if(n.toLowerCase().startsWith("multicafe_"))break;f=f.getParentFile();}
        Collections.reverse(parts);return String.join("/",parts);}
    public static String header() {return "record_name,path,status,message,dividers,frame_width_px,median_cage_pitch_px,pitch_mad_percent,ground_truth_caps,median_ground_truth_length_px,median_green_roi_length_px,ground_truth_per_pitch,ground_truth_per_frame";}
    public String csv() {return q(recordName)+","+q(path)+","+status+","+q(message)+","+dividers+","+n(frameWidth)+","+n(medianPitch)+","+n(pitchMadPercent)+","+groundTruthCapillaries+","+n(medianGroundTruthLength)+","+n(medianGreenLength)+","+n(medianGroundTruthLength/medianPitch)+","+n(medianGroundTruthLength/frameWidth);}
    private static String n(double v){return Double.isFinite(v)?Double.toString(v):"";}
    private static String q(String s){return "\""+(s==null?"":s.replace("\"","\"\""))+"\"";}
}
