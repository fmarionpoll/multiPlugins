package plugins.fmp.multiSPOTS96.tools.JComponents;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import java.awt.Component;
import javax.swing.JCheckBox;

public class MultiSelectDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final DefaultListModel<CheckItem> fullModel = new DefaultListModel<CheckItem>();
    private final DefaultListModel<CheckItem> filteredModel = new DefaultListModel<CheckItem>();
    private final JList<CheckItem> list = new JList<CheckItem>(filteredModel);
    private final JTextField searchField = new JTextField();
    private boolean confirmed = false;

    public static List<String> showDialog(Component parent, String title, List<String> allValues, Collection<String> preSelected) {
        Frame owner = parent != null ? (Frame) SwingUtilities.getWindowAncestor(parent) : null;
        MultiSelectDialog dlg = new MultiSelectDialog(owner, title, allValues, preSelected);
        dlg.setVisible(true);
        if (!dlg.confirmed)
            return null;
        return dlg.getSelectedValues();
    }

    private MultiSelectDialog(Frame owner, String title, List<String> allValues, Collection<String> preSelected) {
        super(owner, title, true);
        setLayout(new BorderLayout(5, 5));

        for (String v : allValues) {
            boolean sel = preSelected != null && preSelected.contains(v);
            fullModel.addElement(new CheckItem(v, sel));
        }
        copyFilter("");

        list.setCellRenderer(new CheckRenderer());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int idx = list.locationToIndex(e.getPoint());
                if (idx >= 0) {
                    CheckItem item = filteredModel.get(idx);
                    item.selected = !item.selected;
                    list.repaint(list.getCellBounds(idx, idx));
                }
            }
        });

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { copyFilter(searchField.getText()); }
            @Override public void removeUpdate(DocumentEvent e) { copyFilter(searchField.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { copyFilter(searchField.getText()); }
        });

        JPanel top = new JPanel(new BorderLayout(5, 5));
        top.add(new JLabel("Filter:"), BorderLayout.WEST);
        top.add(searchField, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton selectAllBtn = new JButton("Select all");
        JButton clearBtn = new JButton("Clear");
        JButton invertBtn = new JButton("Invert");
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");
        buttons.add(selectAllBtn);
        buttons.add(clearBtn);
        buttons.add(invertBtn);
        buttons.add(okBtn);
        buttons.add(cancelBtn);

        selectAllBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { setAllFiltered(true); }
        });
        clearBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { setAllFiltered(false); }
        });
        invertBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { invertFiltered(); }
        });
        okBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { confirmed = true; dispose(); }
        });
        cancelBtn.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) { dispose(); }
        });

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(360, 420));
        pack();
        setLocationRelativeTo(owner);
    }

    private void copyFilter(String term) {
        filteredModel.clear();
        String t = term != null ? term.trim().toLowerCase() : "";
        for (int i = 0; i < fullModel.getSize(); i++) {
            CheckItem it = fullModel.getElementAt(i);
            if (t.isEmpty() || it.text.toLowerCase().contains(t))
                filteredModel.addElement(it);
        }
    }

    private void setAllFiltered(boolean sel) {
        for (int i = 0; i < filteredModel.getSize(); i++) {
            filteredModel.getElementAt(i).selected = sel;
        }
        list.repaint();
    }

    private void invertFiltered() {
        for (int i = 0; i < filteredModel.getSize(); i++) {
            CheckItem it = filteredModel.getElementAt(i);
            it.selected = !it.selected;
        }
        list.repaint();
    }

    private List<String> getSelectedValues() {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < fullModel.getSize(); i++) {
            CheckItem it = fullModel.getElementAt(i);
            if (it.selected)
                out.add(it.text);
        }
        return out;
    }

    private static class CheckItem {
        final String text; boolean selected;
        CheckItem(String t, boolean s) { text = t; selected = s; }
    }

    private static class CheckRenderer extends JCheckBox implements ListCellRenderer<CheckItem> {
        private static final long serialVersionUID = 1L;
        @Override
        public Component getListCellRendererComponent(JList<? extends CheckItem> list, CheckItem value, int index,
                boolean isSelected, boolean cellHasFocus) {
            setText(value != null ? value.text : "");
            setSelected(value != null && value.selected);
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


