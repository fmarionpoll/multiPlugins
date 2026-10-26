package plugins.fmp.multitools.service;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import plugins.fmp.multitools.experiment.capillary.Capillary;

/** Session-only comparison data. Never replaces measurements or enters saved results. */
public final class MeniscusRefinementPreview {
    public static final String ROI_PROPERTY = "fmp.meniscusRefinementPreview";
    private static final Map<Capillary, Entry> previews = new WeakHashMap<>();
    private MeniscusRefinementPreview() { }
    private static final class Entry {
        final WeakReference<Object> owner;
        final MeniscusRefinement.Proposal proposal;
        Entry(Object owner, MeniscusRefinement.Proposal proposal) {
            this.owner = new WeakReference<>(owner); this.proposal = proposal;
        }
    }
    public static synchronized void publish(Object owner, Map<Capillary, MeniscusRefinement.Proposal> proposals) {
        clear(owner);
        proposals.forEach((cap, proposal) -> previews.put(cap, new Entry(owner, proposal)));
    }
    public static synchronized void clear(Object owner) {
        previews.entrySet().removeIf(entry -> entry.getValue().owner.get() == null || entry.getValue().owner.get() == owner);
    }
    public static synchronized MeniscusRefinement.Proposal get(Capillary cap) {
        Entry entry = previews.get(cap);
        return entry != null && entry.owner.get() != null && MeniscusRefinement.isCurrent(cap, entry.proposal)
                ? entry.proposal : null;
    }
}
