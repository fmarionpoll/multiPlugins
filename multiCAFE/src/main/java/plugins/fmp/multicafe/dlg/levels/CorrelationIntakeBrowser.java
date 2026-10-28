package plugins.fmp.multicafe.dlg.levels;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
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

/** One persistent dialog following the experiment browser, with separate intake sessions. */
final class CorrelationIntakeBrowser extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JComboBox<?> browser;
    private final Consumer<Capillary> capillaryChosen;
    private final Map<Experiment, CorrelationIntakePanel> sessions = new IdentityHashMap<>();
    private final ItemListener browserListener;
    private CorrelationIntakePanel active;
    private Experiment selected;
    private boolean closed;

    static CorrelationIntakeBrowser open(Component owner, JComboBox<?> browser, Capillary initial,
            Consumer<Capillary> capillaryChosen) {
        CorrelationIntakeBrowser content = new CorrelationIntakeBrowser(browser, initial, capillaryChosen);
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(owner), Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setContentPane(content);
        dialog.setSize(1180, 880);
        dialog.setMinimumSize(new Dimension(920, 700));
        dialog.setLocationRelativeTo(owner);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) { content.disposeSessions(); }
        });
        content.updateTitle();
        dialog.setVisible(true);
        return content;
    }

    CorrelationIntakeBrowser(JComboBox<?> browser, Capillary initial, Consumer<Capillary> capillaryChosen) {
        super(new BorderLayout());
        this.browser = browser;
        this.capillaryChosen = capillaryChosen;
        // Let the application's selection/load handlers finish, and coalesce
        // deselect/select event pairs by reading the eventual selected item.
        browserListener = event -> followSelection();
        browser.addItemListener(browserListener);
        showSelected(initial);
    }

    void followSelection() {
        SwingUtilities.invokeLater(() -> showSelected(null));
    }

    private void showSelected(Capillary initial) {
        if (closed || (active != null && active.isSeriesRunning())) return;
        Object item = browser.getSelectedItem();
        Experiment next = item instanceof Experiment ? (Experiment) item : null;
        if (next == selected && active != null) {
            active.resumeSession();
            if (initial != null) active.showCapillary(initial);
            return;
        }
        if (active != null) active.suspendSession();
        selected = next;
        active = null;
        removeAll();
        if (next == null) {
            add(new JLabel("Select an experiment in the browser."), BorderLayout.CENTER);
        } else {
            active = sessions.get(next);
            if (active == null) {
                Capillary cap = next.getCapillaries().getList().contains(initial) ? initial : null;
                active = new CorrelationIntakePanel(next, cap);
                active.followExperimentBrowser(browser);
                active.onCapillaryChosen(chosen -> {
                    if (!closed && selected == next && browser.getSelectedItem() == next && capillaryChosen != null)
                        capillaryChosen.accept(chosen);
                });
                active.addPropertyChangeListener(event -> {
                    if ("intake_series_finished".equals(event.getPropertyName())) followSelection();
                });
                sessions.put(next, active);
            }
            add(active, BorderLayout.CENTER);
            active.resumeSession();
            if (initial != null) active.showCapillary(initial);
        }
        updateTitle();
        revalidate();
        repaint();
    }

    Experiment getExperiment() { return selected; }

    void showCapillary(Capillary cap) {
        if (!closed) showSelected(cap);
    }

    void bringToFront() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null) window.toFront();
    }

    void close() {
        Window window = SwingUtilities.getWindowAncestor(this);
        disposeSessions();
        if (window != null) window.dispose();
    }

    private void updateTitle() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window instanceof JDialog) ((JDialog) window).setTitle("Intake as measured from topraw - "
                + (selected == null ? "No experiment selected" : selected.toString()));
    }

    private void disposeSessions() {
        if (closed) return;
        closed = true;
        browser.removeItemListener(browserListener);
        for (CorrelationIntakePanel panel : sessions.values()) panel.disposeSession();
        sessions.clear();
        active = null;
    }
}
