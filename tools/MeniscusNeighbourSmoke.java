/* Headless check of Run -> Apply -> Run with a hand-placed column; no disk saves. */
import java.lang.reflect.*;
import java.util.Map;
import javax.swing.*;
import plugins.fmp.multicafe.dlg.levels.MeniscusRefinementPanel;
import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;
import plugins.fmp.multitools.service.MeniscusRefinement;

public class MeniscusNeighbourSmoke {
    static Object get(Object o,String name) throws Exception {
        Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);
    }
    static void waitReady(MeniscusRefinementPanel panel) throws Exception {
        long end=System.currentTimeMillis()+15000;
        while(System.currentTimeMillis()<end) {
            boolean[] ready={false};
            SwingUtilities.invokeAndWait(()-> {
                try {ready[0]=get(panel,"worker")==null && !((Timer)get(panel,"previewTimer")).isRunning()
                        && !((Map<?,?>)get(panel,"proposals")).isEmpty();}
                catch(Exception error){throw new RuntimeException(error);}
            });
            if(ready[0])return;
            Thread.sleep(20);
        }
        throw new AssertionError("Refinement worker did not finish");
    }
    public static void main(String[] args) throws Exception {
        System.setProperty("java.util.prefs.PreferencesFactory",CorrelationPanelSmoke.MemoryPreferencesFactory.class.getName());
        icy.preferences.IcyPreferences.init();
        Experiment exp=CorrelationIntakeBatchSmoke.fixture(new int[1]);
        Capillary cap=exp.getCapillaries().getList().get(0);
        double[] saved=cap.getTopRaw().polylineLevel.ypoints.clone();
        MeniscusRefinementPanel[] holder={null};
        SwingUtilities.invokeAndWait(()->holder[0]=new MeniscusRefinementPanel(exp,cap,null));
        MeniscusRefinementPanel panel=holder[0];
        try {
            waitReady(panel);
            SwingUtilities.invokeAndWait(()-> {
                try {
                    if(((JComboBox<?>)get(panel,"correlationMethod")).getSelectedItem()!=MeniscusRefinement.Method.ADJACENT)
                        throw new AssertionError("Previous method is not the default");
                } catch(Exception error){throw new RuntimeException(error);}
            });
            double[] chosen={0};
            SwingUtilities.invokeAndWait(()-> {
                try {
                    MeniscusRefinement.Proposal p=(MeniscusRefinement.Proposal)((Map<?,?>)get(panel,"proposals")).get(cap);
                    p.addOffset(20,20,1.25);chosen[0]=p.refined()[20];
                    Method reset=panel.getClass().getDeclaredMethod("invalidateCorrelation");reset.setAccessible(true);reset.invoke(panel);
                } catch(Exception error){throw new RuntimeException(error);}
            });
            for(int run=0;run<2;run++) {
                final MeniscusRefinement.Method method=MeniscusRefinement.Method.values()[run];
                SwingUtilities.invokeAndWait(()-> {
                    try {
                        ((JComboBox<?>)get(panel,"correlationMethod")).setSelectedItem(method);
                        Method series=panel.getClass().getDeclaredMethod("newSeriesWorker");series.setAccessible(true);
                        if(get(series.invoke(panel),"method")!=method)throw new AssertionError("Batch method differs from preview");
                        ((JButton)get(panel,"runCorrelation")).doClick();
                    }
                    catch(Exception error){throw new RuntimeException(error);}
                });
                waitReady(panel);
                SwingUtilities.invokeAndWait(()-> {
                    try {
                        MeniscusRefinement.Proposal computed=(MeniscusRefinement.Proposal)((Map<?,?>)get(panel,"correlations")).get(cap);
                        if(computed==null || computed.refined()[20]!=chosen[0])throw new AssertionError("Run moved manual anchor");
                        if(!((JLabel)get(panel,"status")).getText().contains(method+" ready"))throw new AssertionError("Selected method not reported");
                        ((JButton)get(panel,"applyCorrelation")).doClick();
                        MeniscusRefinement.Proposal p=(MeniscusRefinement.Proposal)((Map<?,?>)get(panel,"proposals")).get(cap);
                        if(p.refined()[20]!=chosen[0] || !p.placedColumns()[20])throw new AssertionError("Apply lost manual anchor");
                    } catch(Exception error){throw new RuntimeException(error);}
                });
            }
            if(!java.util.Arrays.equals(saved,cap.getTopRaw().polylineLevel.ypoints))throw new AssertionError("Preview changed saved topraw");
            SwingUtilities.invokeAndWait(()-> {
                try {
                    ((JButton)get(panel,"accept")).doClick();
                    MeniscusRefinement.Proposal p=(MeniscusRefinement.Proposal)((Map<?,?>)get(panel,"proposals")).get(cap);
                    if(p.refined()[20]!=chosen[0] || !p.placedColumns()[20])throw new AssertionError("Validate lost manual anchor");
                    ((JButton)get(panel,"runCorrelation")).doClick();
                } catch(Exception error){throw new RuntimeException(error);}
            });
            waitReady(panel);
            SwingUtilities.invokeAndWait(()-> {
                try {
                    MeniscusRefinement.Proposal p=(MeniscusRefinement.Proposal)((Map<?,?>)get(panel,"correlations")).get(cap);
                    if(p==null || p.refined()[20]!=chosen[0])throw new AssertionError("Run after Validate moved manual anchor");
                    ((JButton)get(panel,"undo")).doClick();
                } catch(Exception error){throw new RuntimeException(error);}
            });
            if(!java.util.Arrays.equals(saved,cap.getTopRaw().polylineLevel.ypoints))throw new AssertionError("Undo did not restore saved topraw");
        } finally {SwingUtilities.invokeAndWait(panel::close);}
        System.out.println("PASS: neighbour Run/Apply/Validate/Run retains manual anchors; preview leaves topraw unchanged and Undo restores validation.");
        System.exit(0);
    }
}
