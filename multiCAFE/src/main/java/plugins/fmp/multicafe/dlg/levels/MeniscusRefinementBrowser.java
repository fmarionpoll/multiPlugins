package plugins.fmp.multicafe.dlg.levels;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import plugins.fmp.multitools.experiment.Experiment;
import plugins.fmp.multitools.experiment.capillary.Capillary;

/** Browser-following window; each experiment owns its draft and undo history. */
final class MeniscusRefinementBrowser {
    private final Map<Experiment, MeniscusRefinementPanel> sessions = new IdentityHashMap<>();
    private final JPanel content = new JPanel(new BorderLayout());
    private final JComboBox<?> browser;
    private final JDialog dialog;
    private final Consumer<Experiment> changed;
    private MeniscusRefinementPanel active;
    private Experiment selected;
    private boolean closed;

    static void open(Component owner, JComboBox<?> browser, Capillary initial, Consumer<Experiment> changed) {
        new MeniscusRefinementBrowser(owner, browser, initial, changed);
    }

    private MeniscusRefinementBrowser(Component owner, JComboBox<?> browser, Capillary initial,
            Consumer<Experiment> changed) {
        this.browser = browser;
        this.changed = changed;
        dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setContentPane(content);
        dialog.setSize(1100, 700);
        dialog.setLocationRelativeTo(owner);
        // Defer until the browser's own selection/load listeners have run. Reading
        // the selection then also coalesces deselect/select event pairs.
        ItemListener listener = e -> SwingUtilities.invokeLater(() -> showSelected(null));
        browser.addItemListener(listener);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                closed = true;
                browser.removeItemListener(listener);
                for (MeniscusRefinementPanel panel : sessions.values()) panel.close();
                sessions.clear();
            }
        });
        showSelected(initial);
        dialog.setVisible(true);
    }

    private void showSelected(Capillary initial) {
        if (closed) return;
        Object item = browser.getSelectedItem();
        Experiment next = item instanceof Experiment ? (Experiment) item : null;
        if (next == selected && active != null) return;
        if (active != null) active.suspendSession();
        selected = next;
        active = null;
        content.removeAll();
        if (next == null) {
            content.add(new JLabel("Select an experiment in the browser."), BorderLayout.CENTER);
        } else {
            active = sessions.get(next);
            if (active == null) {
                Capillary cap = next.getCapillaries().getList().contains(initial) ? initial : null;
                active = new MeniscusRefinementPanel(next, cap,
                        () -> { if (changed != null) changed.accept(next); });
                sessions.put(next, active);
            }
            content.add(active, BorderLayout.CENTER);
            active.resumeSession();
        }
        dialog.setTitle("Phase 1 - refine meniscus from topraw - "
                + (next == null ? "No experiment selected" : next.toString()));
        content.revalidate();
        content.repaint();
    }
}
