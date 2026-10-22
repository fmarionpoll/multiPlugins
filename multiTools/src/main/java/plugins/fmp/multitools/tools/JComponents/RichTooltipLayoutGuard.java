package plugins.fmp.multitools.tools.JComponents;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ContainerEvent;
import java.util.concurrent.atomic.AtomicBoolean;

import org.pushingpixels.flamingo.api.common.RichTooltip;
import org.pushingpixels.flamingo.internal.ui.common.JRichTooltipPanel;

/** Scoped workaround for Flamingo's LineBreakMeasurer failure during narrow popup
 * validation. Keep the installed look-and-feel layout, but defer its text wrapping
 * until there is usable width. No exceptions or application error handlers are hidden.
 */
public final class RichTooltipLayoutGuard implements AutoCloseable {
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AWTEventListener listener = event -> {
        if (event instanceof ContainerEvent && event.getID() == ContainerEvent.COMPONENT_ADDED) {
            Component child = ((ContainerEvent) event).getChild();
            if (child instanceof JRichTooltipPanel) protect((JRichTooltipPanel) child);
        }
    };

    public RichTooltipLayoutGuard() {
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.CONTAINER_EVENT_MASK);
    }

    @Override public void close() {
        if (closed.compareAndSet(false, true)) Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
    }

    static void protect(JRichTooltipPanel panel) {
        LayoutManager original = panel.getLayout();
        if (original == null || original instanceof GuardedLayout) return;
        panel.setLayout(new GuardedLayout(original, panel));
    }

    private static final class GuardedLayout implements LayoutManager {
        private final LayoutManager original;
        private final JRichTooltipPanel panel;
        GuardedLayout(LayoutManager original, JRichTooltipPanel panel) {
            this.original = original; this.panel = panel;
        }
        public void addLayoutComponent(String name, Component component) { original.addLayoutComponent(name, component); }
        public void removeLayoutComponent(Component component) { original.removeLayoutComponent(component); }
        public Dimension preferredLayoutSize(Container parent) { return original.preferredLayoutSize(parent); }
        public Dimension minimumLayoutSize(Container parent) { return original.minimumLayoutSize(parent); }
        public void layoutContainer(Container parent) {
            Insets insets = parent.getInsets();
            RichTooltip info = panel.getTooltipInfo();
            int imageWidth = info == null ? 0 : Math.max(width(info.getMainImage()), width(info.getFooterImage()));
            int textWidth = Math.max(64, parent.getFont() == null ? 64
                    : 3 * parent.getFontMetrics(parent.getFont()).getHeight());
            // Popup.reset / validation may first offer only a few pixels. Flamingo
            // then feeds too-small line widths to the JDK text measurer. The normal
            // preferred-size layout will follow once the popup is sized correctly.
            if (parent.getWidth() - insets.left - insets.right - imageWidth - 16 < textWidth) return;
            original.layoutContainer(parent);
        }
        private int width(Image image) { return image == null ? 0 : Math.max(0, image.getWidth(null)); }
    }
}
