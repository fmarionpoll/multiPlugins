package plugins.fmp.multicafe.dlg.capillaries;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import icy.canvas.IcyCanvas;
import icy.gui.frame.IcyFrame;
import icy.gui.viewer.Viewer;
import icy.painter.Overlay;
import icy.sequence.Sequence;
import plugins.kernel.roi.roi2d.ROI2DPolygon;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.service.tracking.PolygonEdgeDiagnostic;

/** Isolated image-0 diagnostic; never changes experiment ROIs or tracking. */
final class StructuralReferenceDialog {
    static void open(Experiment exp) {
        if(exp==null||exp.getSeqCamData()==null)return;
        new SwingWorker<StructuralReferenceDialog,Void>() {
            protected StructuralReferenceDialog doInBackground() throws Exception {
                String file=exp.getSeqCamData().getFileNameFromImageList(0);
                BufferedImage source=ImageIO.read(new File(file));
                if(source==null)throw new IOException("Cannot read image 0: "+file);
                Sequence seq=new Sequence(exp.getSeqCamData().getImageLoader().imageIORead(file));
                return new StructuralReferenceDialog(file,source,seq);
            }
            protected void done() {try {get().show();}catch(Exception e){JOptionPane.showMessageDialog(null,e.getMessage(),"Reference setup failed",JOptionPane.ERROR_MESSAGE);}}
        }.execute();
    }
    private final String file;
    private final BufferedImage image;
    private final Sequence sequence;
    private final List<ROI2DPolygon> bands=new ArrayList<>();
    private List<List<Point2D>> candidates=Collections.emptyList();
    private final Color[] colors={Color.ORANGE,Color.MAGENTA};
    private final JLabel status=new JLabel("Adjust all four corners around ONE edge per band, then preview.");
    private final Overlay overlay=new Overlay("Structural edge candidates (not validated)") {
        public void paint(Graphics2D g,Sequence s,IcyCanvas canvas) {
            if(g==null)return;
            Graphics2D copy=(Graphics2D)g.create();
            for(int i=0;i<candidates.size();i++) {
                copy.setColor(colors[i]);
                for(Point2D p:candidates.get(i))copy.fill(new java.awt.geom.Ellipse2D.Double(p.getX()-1,p.getY()-1,2,2));
            }
            copy.dispose();
        }
    };
    private StructuralReferenceDialog(String file,BufferedImage image,Sequence sequence) {
        this.file=file;this.image=image;this.sequence=sequence;
    }
    private void show() {
        sequence.setName("Structural reference at T=0 — diagnostic only");
        for(int i=0;i<2;i++) {
            double w=image.getWidth(), h=image.getHeight(),y=h*(i==0?.70:.25);
            ROI2DPolygon roi=new ROI2DPolygon(Arrays.asList(new Point2D.Double(.1*w,y),new Point2D.Double(.9*w,y),new Point2D.Double(.9*w,y+12),new Point2D.Double(.1*w,y+12)));
            roi.setName(i==0?"structure.rack-edge":"structure.slide-edge");roi.setColor(colors[i]);
            bands.add(roi);sequence.addROI(roi);
        }
        sequence.addOverlay(overlay);new Viewer(sequence,true);
        IcyFrame frame=new IcyFrame("Structural reference — polygon test",true,true);
        JPanel panel=new JPanel(new GridLayout(0,1));
        panel.add(new JLabel("Orange: rack edge. Magenta: slide boundary. Move the polygons in the reference viewer."));
        panel.add(new JLabel("Initial positions are placeholders. Enclose only the chosen edge, not both sides of the bar."));
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton preview=new JButton("Preview polygon edges"),save=new JButton("Save reference bands…"),load=new JButton("Load reference bands…"),clear=new JButton("Clear preview");
        buttons.add(preview);buttons.add(clear);buttons.add(save);buttons.add(load);panel.add(buttons);panel.add(status);
        preview.addActionListener(e->{
            try {
                final List<List<Point2D>> snapshots=new ArrayList<>();
                for(ROI2DPolygon band:bands) {
                    List<Point2D> points=new ArrayList<>();
                    for(Point2D p:band.getPoints())points.add(new Point2D.Double(p.getX(),p.getY()));
                    snapshots.add(points);
                }
                preview.setEnabled(false);status.setText("Finding edge candidates…");
                new SwingWorker<List<List<Point2D>>,Void>() {
                    protected List<List<Point2D>> doInBackground() {
                        List<List<Point2D>> result=new ArrayList<>();
                        for(List<Point2D> points:snapshots)result.add(PolygonEdgeDiagnostic.detect(image,points));
                        return result;
                    }
                    protected void done() {
                        try {candidates=get();overlay.painterChanged();status.setText("Rack: "+candidates.get(0).size()+" candidates; slide: "+candidates.get(1).size()+". Snapshot only; preview again after editing.");}
                        catch(Exception ex){error(ex);}finally{preview.setEnabled(true);}
                    }
                }.execute();
            }catch(Exception ex){error(ex);}
        });
        clear.addActionListener(e->{candidates=Collections.emptyList();overlay.painterChanged();});
        save.addActionListener(e->persist(true));load.addActionListener(e->persist(false));
        frame.add(panel);frame.pack();frame.addToDesktopPane();frame.setVisible(true);
    }
    private void error(Exception e) {status.setText("Diagnostic failed; no geometry changed.");JOptionPane.showMessageDialog(null,e.getCause()==null?e.getMessage():e.getCause().getMessage(),"Structural reference",JOptionPane.ERROR_MESSAGE);}
    private void persist(boolean save) {
        JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new File("StructuralReference.properties"));
        if((save?chooser.showSaveDialog(null):chooser.showOpenDialog(null))!=JFileChooser.APPROVE_OPTION)return;
        File target=chooser.getSelectedFile();
        try {
            if(save) {
                if(!target.getName().endsWith(".properties"))throw new IOException("Use a separate .properties reference file.");
                if(target.exists()&&JOptionPane.showConfirmDialog(null,"Replace "+target+"?","Save reference bands",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
                Properties p=new Properties();p.setProperty("source",new File(file).getCanonicalPath());
                for(int i=0;i<2;i++) {
                    List<Point2D> points=bands.get(i).getPoints();
                    PolygonEdgeDiagnostic.detect(image,points);
                    for(int j=0;j<4;j++)p.setProperty(i+"."+j,points.get(j).getX()+","+points.get(j).getY());
                }
                try(OutputStream out=new FileOutputStream(target)){p.store(out,"Image-0 structural reference bands; not tracking results");}
                status.setText("Reference bands saved separately; capillary geometry unchanged.");
            }else {
                Properties p=new Properties();try(InputStream in=new FileInputStream(target)){p.load(in);}
                if(!new File(file).getCanonicalPath().equals(p.getProperty("source")))throw new IOException("Reference belongs to another source image.");
                List<List<Point2D>> loaded=new ArrayList<>();
                for(int i=0;i<2;i++) {
                    List<Point2D> points=new ArrayList<>();
                    for(int j=0;j<4;j++){String[] xy=p.getProperty(i+"."+j,"").split(",");if(xy.length!=2)throw new IOException("Invalid reference file");points.add(new Point2D.Double(Double.parseDouble(xy[0]),Double.parseDouble(xy[1])));}
                    PolygonEdgeDiagnostic.detect(image,points);loaded.add(points);
                }
                for(int i=0;i<2;i++)bands.get(i).setPoints(loaded.get(i));
                candidates=Collections.emptyList();overlay.painterChanged();status.setText("Reference bands loaded. Preview to inspect candidates.");
            }
        }catch(Exception e){error(e);}
    }
}
