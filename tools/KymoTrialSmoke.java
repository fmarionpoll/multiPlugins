import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import icy.image.IcyBufferedImage;
import icy.preferences.*;
import plugins.fmp.multitools.experiment.spot.Spot;
import plugins.fmp.multitools.service.*;
import plugins.fmp.multitools.tools.imageTransform.ImageTransformEnums;
/** Read TIFFs and their persisted bands directly, without starting the Icy application. */
public class KymoTrialSmoke {
 public static void main(String[] args)throws Exception {
  IcyPreferences.init();GeneralPreferences.setVirtualMode(false);
  Path bin=Paths.get(args[0]);
  List<String> clock=Files.readAllLines(bin.resolve("CageKymographColumnTimes.csv"));
  double[] minutes=new double[clock.size()-1];
  for(int i=1;i<clock.size();i++)minutes[i-1]=Double.parseDouble(clock.get(i).split(";")[1])/60000.;
  Map<String,Spot> spots=new HashMap<>();
  for(String line:Files.readAllLines(bin.getParent().resolve("SpotsDescription.csv"))){
   if(!line.startsWith("spot_"))continue;String[] f=line.split(";");Spot spot=new Spot();spot.getProperties().setName(f[0]);
   spot.getProperties().setStimulus(f[9]);spot.getProperties().setConcentration(f[10]);spots.put(f[0],spot);
  }
  Map<String,List<CageKymographSpotBands>> layout=new LinkedHashMap<>();Map<String,Integer> ends=new HashMap<>();
  for(String line:Files.readAllLines(bin.resolve("CageKymographStripLayout.csv"))){
   if(line.startsWith("#"))continue;String[] f=line.split(";");int y=ends.getOrDefault(f[0],0),h=Integer.parseInt(f[3]);
   layout.computeIfAbsent(f[0],k->new ArrayList<>()).add(new CageKymographSpotBands(spots.get(f[2]),y,y+h,"1".equals(f[4])));ends.put(f[0],y+h);
  }
  CageKymoAnalyzer.Params p=new CageKymoAnalyzer.Params(ImageTransformEnums.RGB_DIFFS,35,0,1,false,15,true,false,true,ImageTransformEnums.B_RGB,50,false);
  CageKymoAnalyzer_new.Settings settings=new CageKymoAnalyzer_new.Settings(10,20);
  List<CageKymoAnalyzer_new.Row> rows=new ArrayList<>();
  for(Map.Entry<String,List<CageKymographSpotBands>> e:layout.entrySet()){
   IcyBufferedImage image=IcyBufferedImage.createFrom(ImageIO.read(bin.resolve(e.getKey()+".tiff").toFile()));
   int cage=Integer.parseInt(e.getKey().substring("kymocage_".length()));
   rows.addAll(CageKymoAnalyzer_new.analyzeImage(cage,image,e.getValue(),p,minutes,settings));
  }
  CageKymoAnalyzer_new.Result result=new CageKymoAnalyzer_new.Result(minutes,rows,settings);
  KymoAnalysisCsvExport_new.write(Paths.get(args[1]),result,p);
  long valid=result.rows.stream().filter(r->Double.isFinite(r.signal.baseline)).count();
  System.out.println("rows="+result.rows.size()+" baseline-valid="+valid+" groups="+result.groups.size());
  for(CageKymoAnalyzer_new.Group group:result.groups)if(group.cageId==1)for(int x:new int[]{3,12,15,18,30,100,368})System.out.println("cage1 min="+result.minutes[x]+" old="+group.oldLoss[x]+" new="+group.newLoss[x]+" valid="+group.newValid[x]);
  System.exit(0);
 }
}

