package plugins.fmp.multiSPOTS96.tools.JComponents;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

public class JCheckComboBox extends JComboBox<JCheckComboBox.CheckableItem> {

    private static final long serialVersionUID = 1L;

    public JCheckComboBox() {
        setRenderer(new CheckBoxRenderer());
        addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                @SuppressWarnings("unchecked")
                JComboBox<JCheckComboBox.CheckableItem> combo = (JComboBox<JCheckComboBox.CheckableItem>) e.getSource();
                CheckableItem item = (CheckableItem) combo.getSelectedItem();
                if (item != null) {
                    item.setSelected(!item.isSelected());
                    combo.repaint();
                }
                setPopupVisible(true);
            }
        });
    }

    public void setItems(List<String> items) {
        DefaultComboBoxModel<CheckableItem> model = new DefaultComboBoxModel<CheckableItem>();
        if (items != null) {
            for (String s : items) {
                model.addElement(new CheckableItem(s, false));
            }
        }
        setModel(model);
    }

    public void setSelectedValues(Collection<String> values) {
        if (values == null)
            return;
        DefaultComboBoxModel<CheckableItem> model = (DefaultComboBoxModel<CheckableItem>) getModel();
        for (int i = 0; i < model.getSize(); i++) {
            CheckableItem item = model.getElementAt(i);
            item.setSelected(values.contains(item.getText()));
        }
        repaint();
    }

    public List<String> getSelectedValues() {
        List<String> list = new ArrayList<String>();
        DefaultComboBoxModel<CheckableItem> model = (DefaultComboBoxModel<CheckableItem>) getModel();
        for (int i = 0; i < model.getSize(); i++) {
            CheckableItem item = model.getElementAt(i);
            if (item.isSelected())
                list.add(item.getText());
        }
        return list;
    }

    public void clearSelection() {
        DefaultComboBoxModel<CheckableItem> model = (DefaultComboBoxModel<CheckableItem>) getModel();
        for (int i = 0; i < model.getSize(); i++) {
            model.getElementAt(i).setSelected(false);
        }
        repaint();
    }

    public static class CheckableItem {
        private final String text;
        private boolean selected;

        public CheckableItem(String text, boolean selected) {
            this.text = text;
            this.selected = selected;
        }

        public String getText() { return text; }
        public boolean isSelected() { return selected; }
        public void setSelected(boolean b) { selected = b; }

        @Override
        public String toString() {
            return text;
        }
    }

    private static class CheckBoxRenderer extends JCheckBox implements ListCellRenderer<CheckableItem> {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<? extends CheckableItem> list, CheckableItem value, int index,
                boolean isSelected, boolean cellHasFocus) {
            setText(value != null ? value.getText() : "");
            setSelected(value != null ? value.isSelected() : false);
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }
            return this;
        }
    }
}


