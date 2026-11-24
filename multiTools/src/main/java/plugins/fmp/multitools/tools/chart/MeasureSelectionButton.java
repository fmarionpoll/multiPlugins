package plugins.fmp.multitools.tools.chart;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPopupMenu;

import plugins.fmp.multitools.tools.results.EnumResults;

/** Checklist stays open while measures are ticked; Apply commits a nonempty selection. */
public class MeasureSelectionButton extends JButton {
    private static final long serialVersionUID = 1L;
    private final EnumResults[] choices;
    private List<EnumResults> selected = new ArrayList<>();
    private final Consumer<List<EnumResults>> listener;

    public MeasureSelectionButton(EnumResults[] choices, Consumer<List<EnumResults>> listener) {
        this.choices = choices.clone();
        this.listener = listener;
        if (choices.length > 0) selected.add(choices[0]);
        setSelection(selected);
        addActionListener(e -> createPopup().show(this, 0, getHeight()));
    }

    public void setSelection(List<EnumResults> values) {
        selected = new ArrayList<>();
        for (EnumResults value : choices) if (values.contains(value)) selected.add(value);
        if (selected.isEmpty() && choices.length > 0) selected.add(choices[0]);
        setText("Measures… (" + selected.size() + ")");
        setToolTipText("Overlay: " + selected.toString() + ". The Summary dropdown selects one measure.");
    }

    public List<EnumResults> getSelection() { return new ArrayList<>(selected); }

    public JPopupMenu createPopup() {
        JPopupMenu popup = new JPopupMenu();
        List<JCheckBox> boxes = new ArrayList<>();
        JButton apply = new JButton("Apply");
        for (EnumResults choice : choices) {
            JCheckBox box = new JCheckBox(choice.name(), selected.contains(choice));
            box.setToolTipText(choice.toTitle());
            box.addActionListener(e -> apply.setEnabled(boxes.stream().anyMatch(JCheckBox::isSelected)));
            boxes.add(box);
            popup.add(box);
        }
        popup.addSeparator();
        popup.add(apply);
        apply.addActionListener(e -> {
            List<EnumResults> values = new ArrayList<>();
            for (int i = 0; i < choices.length; i++) if (boxes.get(i).isSelected()) values.add(choices[i]);
            if (values.isEmpty()) return;
            setSelection(values);
            popup.setVisible(false);
            listener.accept(getSelection());
        });
        return popup;
    }
}
