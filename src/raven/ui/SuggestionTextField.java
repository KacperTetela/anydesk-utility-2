package raven.ui;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import net.miginfocom.swing.MigLayout;

public class SuggestionTextField extends javax.swing.JTextField {

    public record SuggestionItem(String value, String display) {
        public SuggestionItem {
            Objects.requireNonNull(value);
            Objects.requireNonNull(display);
        }
    }

    @FunctionalInterface
    public interface SuggestionProvider {
        List<SuggestionItem> suggest(String query);
    }

    private SuggestionProvider provider = q -> List.of();

    private final JPopupMenu popup = new JPopupMenu();
    private final DefaultListModel<SuggestionItem> model = new DefaultListModel<>();
    private final JList<SuggestionItem> list = new JList<>(model);

    public SuggestionTextField() {
        initPopup();
        installListeners();
    }

    public void setSuggestionProvider(SuggestionProvider provider) {
        this.provider = provider != null ? provider : (q -> List.of());
    }

    private void initPopup() {
        popup.setFocusable(false);
        popup.putClientProperty(FlatClientProperties.STYLE, "background:$PopupMenu.background");

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setVisibleRowCount(6);
        list.putClientProperty(FlatClientProperties.STYLE, ""
                + "cellFocusColor:null;"
                + "selectionArc:10;");
        list.setCellRenderer((jList, value, index, isSelected, cellHasFocus) -> {
            var lbl = new javax.swing.JLabel(value.display());
            lbl.setOpaque(true);
            java.awt.Color bg = javax.swing.UIManager.getColor(isSelected ? "List.selectionBackground" : "List.background");
            java.awt.Color fg = javax.swing.UIManager.getColor(isSelected ? "List.selectionForeground" : "List.foreground");
            lbl.setBackground(bg);
            lbl.setForeground(fg);
            lbl.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 10, 8, 10));
            return lbl;
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.putClientProperty(FlatClientProperties.STYLE, ""
                + "arc:12;"
                + "border:1,1,1,1,$Component.borderColor,,12;");
        scroll.setBorder(null);
        popup.setLayout(new MigLayout("insets 0,fill", "[grow,fill]", "[grow,fill]"));
        popup.add(scroll);

        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 1) {
                    acceptSelected();
                }
            }
        });
    }

    private void installListeners() {
        Document doc = getDocument();
        doc.addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateSuggestions();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateSuggestions();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateSuggestions();
            }
        });

        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                popup.setVisible(false);
            }
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!popup.isVisible()) {
                    return;
                }
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    int next = Math.min(model.size() - 1, list.getSelectedIndex() + 1);
                    list.setSelectedIndex(next);
                    list.ensureIndexIsVisible(next);
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    int next = Math.max(0, list.getSelectedIndex() - 1);
                    list.setSelectedIndex(next);
                    list.ensureIndexIsVisible(next);
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    acceptSelected();
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    popup.setVisible(false);
                    e.consume();
                }
            }
        });
    }

    private void updateSuggestions() {
        SwingUtilities.invokeLater(() -> {
            String q = getText() != null ? getText().trim() : "";
            List<SuggestionItem> suggestions = provider.suggest(q);
            List<SuggestionItem> limited = new ArrayList<>();
            for (SuggestionItem s : suggestions) {
                if (s != null) {
                    limited.add(s);
                }
                if (limited.size() >= 10) {
                    break;
                }
            }
            model.clear();
            for (SuggestionItem s : limited) {
                model.addElement(s);
            }
            if (model.isEmpty() || !isShowing() || q.isEmpty()) {
                popup.setVisible(false);
                return;
            }
            list.setSelectedIndex(0);

            Point p = new Point(0, getHeight());
            SwingUtilities.convertPointToScreen(p, this);
            int w = getWidth();
            int h = UIScale.scale(240);

            popup.setPopupSize(w, h);
            popup.show(this, 0, getHeight());
        });
    }

    private void acceptSelected() {
        SuggestionItem item = list.getSelectedValue();
        if (item == null) {
            popup.setVisible(false);
            return;
        }
        setText(item.value());
        popup.setVisible(false);
        setCaretPosition(getText().length());
        requestFocusInWindow();
    }
}

