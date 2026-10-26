package plugins.fmp.multitools.tools.chart.builders;

import static org.junit.Assert.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Collections;
import org.junit.Test;
import org.jfree.data.xy.XYSeriesCollection;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.cage.Cage;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.capillary.geometry.CapillaryPixelScale;
import plugins.fmp.multitools.service.MeniscusRefinement;
import plugins.fmp.multitools.service.MeniscusRefinementPreview;
import plugins.fmp.multitools.tools.polyline.Level2D;
import plugins.fmp.multitools.tools.results.EnumResults;
import plugins.fmp.multitools.tools.results.ResultsOptions;
import plugins.fmp.multitools.tools.chart.style.SeriesStyleCodec;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class MeniscusComparisonChartTest {
    @Test public void proposedSeriesUsesSameTimeCalibrationAndAnchorWithoutChangingData() {
        icy.preferences.IcyPreferences.init();
        for(boolean resampled:new boolean[]{false,true}) {
            Experiment exp=new Experiment();
            exp.getActiveBinDescription().setKymoFromNormedBlue(resampled);
            java.awt.geom.Line2D green=new java.awt.geom.Line2D.Double(0,0,0,100);
            ROI2DLine roi=new ROI2DLine(green);roi.setName("line2L");
            Capillary cap=new Capillary(roi);cap.setKymographIndex(0);cap.setCageID(2);cap.setPixels(80);cap.setVolume(1);
            cap.getPhaseGeometry().initialize(0,green,new java.awt.geom.Line2D.Double(0,10,0,90));
            double[] raw={30,31,30,35,36,35};double[] x={0,1,2,3,4,5};
            cap.getTopRaw().polylineLevel=new Level2D(x,raw.clone(),raw.length);
            exp.getCapillaries().getList().add(cap);
            Cage cage=new Cage();cage.getProperties().setCageID(2);cage.addCapillaryIfUnique(cap);
            BufferedImage image=new BufferedImage(raw.length,80,BufferedImage.TYPE_INT_RGB);
            for(int t=0;t<raw.length;t++) for(int y=0;y<80;y++) {
                double position=t<3?30+t*.2:35+(t-3)*.2;
                double f=1/(1+Math.exp(-(y-position)/1.4));
                image.setRGB(t,y,((int)(150-65*f)<<16)|((int)(155-5*f)<<8)|(int)(145-5*f));
            }
            MeniscusRefinement.Proposal proposal=MeniscusRefinement.propose(cap.getTopRaw().polylineLevel,image,10,3,true);
            Object owner=new Object();
            ResultsOptions options=new ResultsOptions();options.resultType=EnumResults.TOPRAW;options.buildExcelStepMs=60000;
            CageCapillarySeriesBuilder builder=new CageCapillarySeriesBuilder();
            XYSeriesCollection original=builder.build(exp,cage,options);
            assertEquals(5.0/80,original.getYValue(0,5),1e-10);
            MeniscusRefinementPreview.publish(owner,Collections.singletonMap(cap,proposal));
            try {
                XYSeriesCollection compared=builder.build(exp,cage,options);
                assertEquals(original.getSeriesCount()+1,compared.getSeriesCount());
                int proposed=compared.getSeriesCount()-1;
                assertEquals(Color.MAGENTA,SeriesStyleCodec.tryParseColor(compared.getSeries(proposed).getDescription()).get());
                for(int i=0;i<raw.length;i++) {
                    assertEquals(original.getXValue(0,i),compared.getXValue(proposed,i),0);
                    assertEquals(original.getYValue(0,i),compared.getYValue(0,i),0);
                    double expected=CapillaryPixelScale.toUl(proposal.refined()[i]-raw[0],cap,i,
                            CapillaryPixelScale.sourceOf(exp),CapillaryPixelScale.expansionRatioOf(exp),false);
                    assertEquals(expected,compared.getYValue(proposed,i),1e-10);
                }
                assertArrayEquals(raw,cap.getTopRaw().polylineLevel.ypoints,0);
            } finally { MeniscusRefinementPreview.clear(owner); }
            assertEquals(original.getSeriesCount(),builder.build(exp,cage,options).getSeriesCount());
        }
    }
}
