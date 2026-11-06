/* Read-only real-data Swing workflow check. Arguments: experiment folder, output PNG. */
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import plugins.fmp.multicafe.dlg.levels.MeniscusRefinementPanel;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.experiment.sequence.SequenceKymos;
import plugins.fmp.multitools.service.MeniscusRefinement;
import plugins.fmp.multitools.service.MeniscusReferenceTracking;
import plugins.fmp.multitools.tools.polyline.Level2D;
import plugins.kernel.roi.roi2d.ROI2DLine;

public class MeniscusReferenceSmoke {
    static Object field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o);
    }
    static void layout(Container c) { c.doLayout(); for (java.awt.Component ch : c.getComponents()) if (ch instanceof Container) layout((Container)ch); }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static void waitReady(MeniscusRefinementPanel panel) throws Exception {
        for (int attempt = 0; attempt < 1000; attempt++) {
            boolean[] ready = {false};
            SwingUtilities.invokeAndWait(() -> { try {
                ready[0] = field(panel, "worker") == null && !((javax.swing.Timer)field(panel, "previewTimer")).isRunning()
                        && !((Map<?,?>)field(panel, "proposals")).isEmpty();
            } catch (Exception e) { throw new RuntimeException(e); } });
            if (ready[0]) return; Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for preview");
    }
    static MeniscusRefinement.Proposal draft(MeniscusRefinementPanel p, Capillary c) throws Exception {
        return (MeniscusRefinement.Proposal)((Map<?,?>)field(p, "proposals")).get(c);
    }
    @SuppressWarnings("unchecked")
    static void checkAnchorDrag(MeniscusRefinementPanel panel, Capillary cap) throws Exception {
        JPanel plot = (JPanel)field(panel,"plot");
        Map<Integer,Double> anchors = (Map<Integer,Double>)((Map<?,?>)field(panel,"referenceAnchors")).get(cap);
        int oldColumn = anchors.keySet().iterator().next(), newColumn = oldColumn+5;
        double oldY = anchors.get(oldColumn);
        Method topMethod = panel.getClass().getDeclaredMethod("stripTop",int.class);
        Method bottomMethod = panel.getClass().getDeclaredMethod("stripBottom",int.class);
        topMethod.setAccessible(true); bottomMethod.setAccessible(true);
        int top = (Integer)topMethod.invoke(null,plot.getHeight()), bottom = (Integer)bottomMethod.invoke(null,plot.getHeight());
        int x = 10+(int)((oldColumn+.5)*(plot.getWidth()-20.0)/draft(panel,cap).columnCount());
        int newX = 10+(int)((newColumn+.5)*(plot.getWidth()-20.0)/draft(panel,cap).columnCount());
        int y = (int)Math.round((top+bottom)/2.0+(oldY-draft(panel,cap).refined()[oldColumn])*(bottom-top)/31.0);
        Object previousPreview = ((Map<?,?>)field(panel,"referenceResults")).get(cap);
        plot.dispatchEvent(new MouseEvent(plot,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),0,x+2,y+1,1,false,MouseEvent.BUTTON1));
        plot.dispatchEvent(new MouseEvent(plot,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,x+2,y+1,1,false,MouseEvent.BUTTON1));
        check(anchors.size()==1 && anchors.get(oldColumn)==oldY,"Clicking a dot moved or duplicated it");
        check(((Map<?,?>)field(panel,"referenceResults")).get(cap)==previousPreview,"Click without dragging invalidated preview");
        plot.dispatchEvent(new MouseEvent(plot,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),0,x,y,1,false,MouseEvent.BUTTON1));
        plot.dispatchEvent(new MouseEvent(plot,MouseEvent.MOUSE_DRAGGED,System.currentTimeMillis(),MouseEvent.BUTTON1_DOWN_MASK,newX,y+8,0,false,MouseEvent.NOBUTTON));
        check(anchors.containsKey(oldColumn),"Dragging committed before release");
        plot.dispatchEvent(new MouseEvent(plot,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,newX,y+8,1,false,MouseEvent.BUTTON1));
        check(anchors.size()==1 && !anchors.containsKey(oldColumn) && anchors.containsKey(newColumn),"Drag did not move the existing dot horizontally");
        double expectedY = draft(panel,cap).refined()[newColumn]+oldY-draft(panel,cap).refined()[oldColumn]+8*31.0/(bottom-top);
        check(Math.abs(anchors.get(newColumn)-expectedY)<1e-8,"Drag did not move the dot vertically");
        check(!((Map<?,?>)field(panel,"referenceResults")).containsKey(cap),"Drag left a stale preview");
        check(!((JButton)field(panel,"applyReference")).isEnabled(),"Apply remained enabled after dragging");
        ((JButton)field(panel,"previewReference")).doClick();
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("java.util.prefs.PreferencesFactory", CorrelationPanelSmoke.MemoryPreferencesFactory.class.getName());
        icy.preferences.IcyPreferences.init();
        Path root = Paths.get(args[0], "results");
        BufferedImage image = ImageIO.read(root.resolve("bin_60/line11.tiff").toFile());
        double[] raw = null;
        for (String line : Files.readAllLines(root.resolve("CapillariesMeasures.csv"))) {
            if (line.startsWith("1L;")) {
                String[] cells = line.split(";"); int n = Integer.parseInt(cells[2]); raw = new double[n];
                for (int i = 0; i < n; i++) raw[i] = Double.parseDouble(cells[i+3]); break;
            }
        }
        check(raw != null && raw.length == image.getWidth(), "Native image/topraw mismatch");
        double[] x = new double[raw.length]; for (int i = 0; i < x.length; i++) x[i] = i;
        Experiment exp = new Experiment(); ROI2DLine roi = new ROI2DLine(0, 0, 0, image.getHeight()); roi.setName("line1L");
        Capillary cap = new Capillary(roi); cap.setKymographIndex(0); cap.setVolume(5); cap.setPixels(411);
        cap.getTopRaw().polylineLevel = new Level2D(x, raw, raw.length); exp.getCapillaries().getList().add(cap);
        Sequence sequence = new Sequence(); sequence.setImage(0, 0, IcyBufferedImage.createFrom(image));
        SequenceKymos kymos = new SequenceKymos(); kymos.setSequence(sequence); exp.setSeqKymos(kymos);
        MeniscusRefinementPanel[] holder = {null};
        SwingUtilities.invokeAndWait(() -> holder[0] = new MeniscusRefinementPanel(exp, cap, null));
        MeniscusRefinementPanel panel = holder[0]; final double[] saved = raw.clone();
        try {
            waitReady(panel);
            SwingUtilities.invokeAndWait(() -> { try {
                panel.setSize(1100, 740); layout(panel);
                ((JCheckBox)field(panel, "pickReference")).doClick();
                JPanel plot = (JPanel)field(panel, "plot");
                Method stripTop = panel.getClass().getDeclaredMethod("stripTop", int.class); stripTop.setAccessible(true);
                Method stripBottom = panel.getClass().getDeclaredMethod("stripBottom", int.class); stripBottom.setAccessible(true);
                int top = (Integer)stripTop.invoke(null, plot.getHeight()), bottom = (Integer)stripBottom.invoke(null, plot.getHeight());
                int column = 80; double referenceY = 42;
                int clickX = 10+(int)((column+.5)*(plot.getWidth()-20.0)/saved.length);
                int clickY = (int)Math.round((top+bottom)/2.0+(referenceY-draft(panel,cap).refined()[column])*(bottom-top)/31.0);
                plot.dispatchEvent(new MouseEvent(plot, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, clickX, clickY, 1, false, MouseEvent.BUTTON1));
                check(((JButton)field(panel, "previewReference")).isEnabled(), "Click did not enable reference tracking");
                ((JButton)field(panel, "previewReference")).doClick();
            } catch (Exception e) { throw new RuntimeException(e); } });
            waitReady(panel);
            SwingUtilities.invokeAndWait(() -> { try { checkAnchorDrag(panel,cap); }
                catch (Exception e) { throw new RuntimeException(e); } });
            waitReady(panel);
            SwingUtilities.invokeAndWait(() -> { try {
                MeniscusReferenceTracking.Result result = (MeniscusReferenceTracking.Result)((Map<?,?>)field(panel, "referenceResults")).get(cap);
                check(result != null, "Reference preview missing: "+((JLabel)field(panel,"status")).getText());
                int good = 0; for (boolean b : result.reliable) if (b) good++;
                System.out.println("1L: "+good+"/"+saved.length+" columns tracked from one click");
                check(good > saved.length*.9, "Real-data tracking lost the reference");
                check(Arrays.equals(saved, cap.getTopRaw().polylineLevel.ypoints), "Preview changed saved topraw");
                BufferedImage render = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = render.createGraphics(); panel.printAll(g); g.dispose(); ImageIO.write(render, "png", Paths.get(args[1]).toFile());
                ((JButton)field(panel, "applyReference")).doClick();
                check(Arrays.equals(result.refined, draft(panel,cap).refined()), "Apply did not transfer preview");
                check(!draft(panel,cap).placedColumns()[80], "Automatic alignment became a manual pin");
                check(Arrays.equals(saved, cap.getTopRaw().polylineLevel.ypoints), "Apply changed saved topraw before validation");
                ((JCheckBox)field(panel,"pickReference")).doClick();
                Field from = panel.getClass().getDeclaredField("selectFrom"), to = panel.getClass().getDeclaredField("selectTo");
                from.setAccessible(true); to.setAccessible(true); from.setInt(panel,100); to.setInt(panel,120);
                ((JButton)field(panel,"nudgeDown")).doClick();
                ((JButton)field(panel,"previewReference")).doClick();
            } catch (Exception e) { throw new RuntimeException(e); } });
            waitReady(panel);
            SwingUtilities.invokeAndWait(() -> { try {
                double chosen = draft(panel,cap).refined()[110];
                ((JButton)field(panel,"applyReference")).doClick();
                check(draft(panel,cap).refined()[110] == chosen && draft(panel,cap).placedColumns()[110], "Rerun lost manual block");
                check(Arrays.equals(saved, cap.getTopRaw().polylineLevel.ypoints), "Workflow altered experiment data");
            } catch (Exception e) { throw new RuntimeException(e); } });
        } finally { SwingUtilities.invokeAndWait(panel::close); }
        System.out.println("PASS: click/drag/preview/apply/range shift/rerun preserves manual blocks and leaves experiment data untouched.");
        System.exit(0);
    }
}
