import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.io.*;
import javax.imageio.ImageIO;
import icy.image.*;
import icy.preferences.*;
import plugins.fmp.multitools.service.*;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
/**
 * Read-only replay of cage 1's strip analyzer and a JPEG round-trip control.
 * Arguments: kymograph bin directory, backed-up camera JPEG, output directory.
 * Run with multiTools classes and its Maven dependency classpath (Java 11+).
 * The only writes are diagnostic files under the output directory.
 */
public class KymoInvestigation {
 public static void main(String[] args) throws Exception {
 if(args.length!=3)throw new IllegalArgumentException("Expected: bin-directory backup-JPEG output-directory");
 IcyPreferences.init(); GeneralPreferences.setVirtualMode(false);
 Path dir=Paths.get(args[0]);
 Path output=Paths.get(args[2]); Files.createDirectories(output);
 IcyBufferedImage image=IcyBufferedImage.createFrom(ImageIO.read(dir.resolve("kymocage_1.tiff").toFile()));
 List<CageKymographSpotBands> bands=new ArrayList<>(); int y=0;
 for(String line:Files.readAllLines(dir.resolve("CageKymographStripLayout.csv"))){String[] f=line.split(";");if(!f[0].equals("kymocage_1"))continue;int h=Integer.parseInt(f[3]);bands.add(new CageKymographSpotBands(null,y,y+h,false));y+=h;}
 List<String> curves=new ArrayList<>(); curves.add("threshold,row_fill,column,aggregate_height_loss");
 for(int setting:new int[]{-35,35,70,100,150}){
  int threshold=Math.abs(setting); boolean rowFill=setting>0;
  CageKymoAnalyzer.Params p=new CageKymoAnalyzer.Params(ImageTransformEnums.RGB_DIFFS,threshold,0,1,false,15,true,false,true,ImageTransformEnums.B_RGB,50,false);
  p.rowwiseOcclusionFill=rowFill;
  IcyBufferedImage lifted=KymoStripRowwiseOcclusionFill.apply(image,bands,p);
  double[] sum=new double[image.getWidth()];
  for(CageKymographSpotBands band:bands){double[] r=CageKymoAnalyzer.computeColumnMetrics(lifted,band,p,image.getWidth()).greenHeightRatio;for(int x=0;x<sum.length;x++)if(Double.isFinite(r[x]))sum[x]+=1-r[x];}
  System.out.println("threshold="+threshold+" rowFill="+rowFill+" cols0,3,15,18,100,last="+Arrays.toString(new double[]{sum[0],sum[3],sum[15],sum[18],sum[100],sum[sum.length-1]}));
  for(int x=0;x<sum.length;x++)curves.add(threshold+","+rowFill+","+x+","+sum[x]);
 }
 Files.write(output.resolve("threshold-replay.csv"),curves);
 BufferedImage orig=ImageIO.read(Paths.get(args[1]).toFile());
 Path jpeg=output.resolve("jpeg-roundtrip-control.jpg"); ImageUtil.save(ImageUtil.toRGBImage(IcyBufferedImage.createFrom(orig)),"jpg",jpeg.toFile());
 BufferedImage rewritten=ImageIO.read(jpeg.toFile());
 for(BufferedImage im:new BufferedImage[]{orig,rewritten}){
  int total=0;for(int x=850;x<880;x++)for(int yy=75;yy<125;yy++){int px=im.getRGB(x,yy);int r=(px>>16)&255,g=(px>>8)&255,b=px&255;if(2*(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b)))>35)total++;}
  System.out.println("blue patch area="+total);
 }
 List<String> control=new ArrayList<>();control.add("spot,original_centerline_height,reencoded_centerline_height,apparent_loss_reencoded_to_original");
 double falseLoss=0;
 for(String line:Files.readAllLines(dir.getParent().resolve("SpotsDescription.csv"))){
  if(!line.startsWith("spot_001_"))continue;
  String[] f=line.split(";");int cx=(int)Math.rint(Double.parseDouble(f[17])),cy=(int)Math.rint(Double.parseDouble(f[18]));
  int originalHeight=countCenterline(orig,cx,cy),rewrittenHeight=countCenterline(rewritten,cx,cy);
  double loss=rewrittenHeight>0?1.0-(double)originalHeight/rewrittenHeight:Double.NaN;
  if(Double.isFinite(loss))falseLoss+=loss;
  control.add(f[0]+","+originalHeight+","+rewrittenHeight+","+loss);
 }
 Files.write(output.resolve("jpeg-centerline-control.csv"),control);
 System.out.println("JPEG-only apparent loss across 36 center lines="+falseLoss);
 System.exit(0);
 }
 private static int countCenterline(BufferedImage image,int x,int cy){
  int height=0;
  for(int y=Math.max(0,cy-25);y<Math.min(image.getHeight(),cy+25);y++){
   int px=image.getRGB(x,y),r=(px>>16)&255,g=(px>>8)&255,b=px&255;
   if(2*(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b)))>35)height++;
  }
  return height;
 }
}
