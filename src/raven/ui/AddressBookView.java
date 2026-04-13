package raven.ui;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import net.miginfocom.swing.MigLayout;
import raven.anydesk.AnyDeskId;
import raven.anydesk.AnyDeskLauncher;
import raven.anydesk.CryptoUtils;
import raven.addressbook.AddressBookStore;
import raven.addressbook.DeviceEntry;
import raven.config.ConfigManager;

public class AddressBookView extends JPanel {

    private static final String CONTENT_TABLE   = "table";
    private static final String CONTENT_NO_PATH = "noPath";

    // Entry-row column indices — must stay in sync with GroupedTableModel.COLS
    static final int COL_NAME     = 0;
    static final int COL_HOST     = 1;
    static final int COL_ID       = 2;
    static final int COL_PASSWORD = 3;
    static final int COL_LAST     = 4;

    // Visual constants
    private static final int ROW_HEIGHT_GROUP   = 36;
    private static final int ROW_HEIGHT_ENTRY   = 32;
    private static final int CELL_PAD_LEFT      = 14;
    private static final int CELL_PAD_RIGHT     = 10;
    private static final int ADD_BTN_MARGIN_R   = 14;
    private static final int ADD_BTN_PAD_H      = 6;   // horizontal padding inside pill
    private static final int ADD_BTN_PAD_V      = 3;   // vertical padding inside pill
    private static final String ADD_BTN_LABEL   = "+ Dodaj";

    private final AddressBookStore store = new AddressBookStore();
    private final BiConsumer<String, char[]> connectAction;

    private JTable          table;
    private GroupedTableModel model;
    private JButton         cmdConnectNow;
    private JButton         cmdAdd;
    private JButton         cmdEdit;
    private JButton         cmdRemove;
    private JButton         cmdTogglePasswords;

    private CardLayout contentCard;
    private JPanel     contentArea;

    private final Map<String, char[]> decryptedCache = new HashMap<>();
    private boolean passwordsVisible = false;

    public AddressBookView(BiConsumer<String, char[]> connectAction) {
        this.connectAction = connectAction;
        init();
        reload();
    }

    // ── UI construction ──────────────────────────────────────────────────────

    private void init() {
        putClientProperty(FlatClientProperties.STYLE, "background:null");
        setLayout(new MigLayout("fill,insets 0", "[grow,fill]", "[grow]"));

        JPanel card = new JPanel(new MigLayout("wrap,fill,insets 26", "[grow,fill]", "[]14[grow]14[]"));
        card.putClientProperty(FlatClientProperties.STYLE,
                "arc:20;border:1,1,1,1,$Component.borderColor,,20;background:$Panel.background");

        JLabel title = new JLabel("Address Book");
        title.putClientProperty(FlatClientProperties.STYLE, "font:+8");

        model = new GroupedTableModel();
        table = buildTable();

        JScrollPane scroll = new JScrollPane(table);
        scroll.putClientProperty(FlatClientProperties.STYLE,
                "arc:14;border:1,1,1,1,$Component.borderColor,,14;");
        scroll.getViewport().setBackground(tableBg());

        JLabel noPathLabel = new JLabel(
                "<html><center>Please set the storage path in Settings.</center></html>",
                JLabel.CENTER);
        noPathLabel.putClientProperty(FlatClientProperties.STYLE,
                "foreground:$Label.disabledForeground");

        contentCard = new CardLayout();
        contentArea = new JPanel(contentCard);
        contentArea.putClientProperty(FlatClientProperties.STYLE, "background:null");
        contentArea.add(scroll,      CONTENT_TABLE);
        contentArea.add(noPathLabel, CONTENT_NO_PATH);

        cmdAdd             = btn("Add",            e -> onAdd());
        cmdEdit            = btn("Edit",           e -> onEditSelected());
        cmdRemove          = btn("Remove",         e -> onRemoveSelected());
        cmdTogglePasswords = btn("Show Passwords", e -> onTogglePasswords());

        cmdConnectNow = new JButton("Connect Now");
        cmdConnectNow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cmdConnectNow.putClientProperty(FlatClientProperties.STYLE,
                "arc:14;margin:10,14,10,14;"
                + "borderWidth:0;focusWidth:0;innerFocusWidth:0;"
                + "background:$App.accentColor;foreground:#ffffff;");
        cmdConnectNow.addActionListener(e -> onConnectSelected());

        JPanel actions = new JPanel(new MigLayout(
                "insets 0,fillx", "[fill]8[fill]8[fill]8[fill]8[fill,push]", "[]"));
        actions.putClientProperty(FlatClientProperties.STYLE, "background:null");
        actions.add(cmdAdd);
        actions.add(cmdEdit);
        actions.add(cmdRemove);
        actions.add(cmdTogglePasswords);
        actions.add(cmdConnectNow, "align right");

        card.add(title);
        card.add(contentArea, "grow");
        card.add(actions, "dock south");
        add(card, "dock center");

        table.getSelectionModel().addListSelectionListener(e -> updateActions());
        updateActions();
    }

    // ── Table factory ─────────────────────────────────────────────────────────

    private JTable buildTable() {
        JTable t = new JTable(model) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                    RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                try {
                    for (int row = 0; row < model.getRowCount(); row++) {
                        if (model.isGroupRow(row)) {
                            paintGroupHeader(g2, row);
                        }
                    }
                } finally {
                    g2.dispose();
                }
            }

            private void paintGroupHeader(Graphics2D g2, int row) {
                Rectangle r = getCellRect(row, 0, true);
                r.x = 0; r.width = getWidth();

                // ── Subtle divider line above (skip first row) ────────────────
                if (r.y > 0) {
                    g2.setColor(dividerColor());
                    g2.drawLine(r.x, r.y, r.x + r.width, r.y);
                }

                // ── Clean flat background ──────────────────────────────────────
                g2.setColor(groupHeaderBg());
                g2.fillRect(r.x, r.y + 1, r.width, r.height - 1);

                // ── Bottom border ─────────────────────────────────────────────
                g2.setColor(dividerColor());
                g2.drawLine(r.x, r.y + r.height - 1, r.x + r.width, r.y + r.height - 1);

                GroupedTableModel.GroupRow gr = (GroupedTableModel.GroupRow) model.rowAt(row);

                // ── Chevron ───────────────────────────────────────────────────
                String chevron = gr.collapsed() ? "\u25B8" : "\u25BE";  // ▸ or ▾
                Font chevronFont = getFont().deriveFont(Font.PLAIN, getFont().getSize() + 1f);
                g2.setFont(chevronFont);
                g2.setColor(groupHeaderFg());
                FontMetrics cfm = g2.getFontMetrics(chevronFont);
                int cx = r.x + CELL_PAD_LEFT;
                int cy = r.y + (r.height - cfm.getHeight()) / 2 + cfm.getAscent();
                g2.drawString(chevron, cx, cy);

                // ── Room label ─────────────────────────────────────────────────
                String roomLabel = GroupedTableModel.NO_ROOM.equals(gr.roomKey())
                        ? "Bez pokoju"
                        : "Pok\u00f3j " + gr.roomKey();
                Font labelFont = getFont().deriveFont(Font.BOLD, getFont().getSize() + 0.5f);
                g2.setFont(labelFont);
                g2.setColor(groupHeaderFg());
                FontMetrics lfm = g2.getFontMetrics(labelFont);
                int lx = cx + cfm.stringWidth(chevron) + 8;
                int ly = r.y + (r.height - lfm.getHeight()) / 2 + lfm.getAscent();
                g2.drawString(roomLabel, lx, ly);

                // ── "+ Dodaj" pill button (right-aligned) ─────────────────────
                Color accent = accentColor();
                Font btnFont = getFont().deriveFont(Font.PLAIN, getFont().getSize() - 0.5f);
                g2.setFont(btnFont);
                FontMetrics bfm = g2.getFontMetrics(btnFont);
                int pillW = bfm.stringWidth(ADD_BTN_LABEL) + ADD_BTN_PAD_H * 2;
                int pillH = bfm.getHeight() + ADD_BTN_PAD_V * 2;
                int pillX = r.x + r.width - pillW - ADD_BTN_MARGIN_R;
                int pillY = r.y + (r.height - pillH) / 2;
                int arc   = pillH;  // fully rounded ends
                // pill background
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 25));
                g2.fillRoundRect(pillX, pillY, pillW, pillH, arc, arc);
                // pill border
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 90));
                g2.drawRoundRect(pillX, pillY, pillW, pillH, arc, arc);
                // pill text
                g2.setColor(accent);
                int tx = pillX + ADD_BTN_PAD_H;
                int ty = pillY + ADD_BTN_PAD_V + bfm.getAscent();
                g2.drawString(ADD_BTN_LABEL, tx, ty);
            }
        };

        t.setRowHeight(ROW_HEIGHT_ENTRY);
        t.putClientProperty(FlatClientProperties.STYLE,
                "showHorizontalLines:false;showVerticalLines:false;");
        t.getTableHeader().putClientProperty(FlatClientProperties.STYLE, "height:32");
        t.setBackground(tableBg());
        t.setIntercellSpacing(new java.awt.Dimension(0, 0));

        // Column renderers
        t.setDefaultRenderer(Object.class, new EntryRowRenderer());
        t.getColumnModel().getColumn(COL_PASSWORD).setCellRenderer(new PasswordCellRenderer());

        // Preferred widths
        t.getColumnModel().getColumn(COL_NAME).setPreferredWidth(220);
        t.getColumnModel().getColumn(COL_HOST).setPreferredWidth(160);
        t.getColumnModel().getColumn(COL_ID).setPreferredWidth(120);
        t.getColumnModel().getColumn(COL_PASSWORD).setPreferredWidth(90);
        t.getColumnModel().getColumn(COL_LAST).setPreferredWidth(140);

        // Custom selection model: skip group rows
        t.setSelectionModel(new DefaultListSelectionModel() {
            @Override public void setSelectionInterval(int a, int b) {
                if (a == b && model.isGroupRow(a)) return;
                super.setSelectionInterval(a, b);
            }
            @Override public void addSelectionInterval(int a, int b) {
                if (a == b && model.isGroupRow(a)) return;
                super.addSelectionInterval(a, b);
            }
        });

        // Mouse: toggle group on click; "+" to add device; double-click entry to connect
        t.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int row = t.rowAtPoint(e.getPoint());
                if (row < 0) return;
                if (model.isGroupRow(row)) {
                    // Compute pill hit area (same formula as paintGroupHeader)
                    Rectangle cellRect = t.getCellRect(row, 0, true);
                    cellRect.x = 0; cellRect.width = t.getWidth();
                    FontMetrics bfm = t.getFontMetrics(
                            t.getFont().deriveFont(Font.PLAIN, t.getFont().getSize() - 0.5f));
                    int pillW = bfm.stringWidth(ADD_BTN_LABEL) + ADD_BTN_PAD_H * 2;
                    int pillH = bfm.getHeight() + ADD_BTN_PAD_V * 2;
                    int pillX = cellRect.x + cellRect.width - pillW - ADD_BTN_MARGIN_R;
                    int pillY = cellRect.y + (cellRect.height - pillH) / 2;
                    if (e.getX() >= pillX && e.getX() <= pillX + pillW
                            && e.getY() >= pillY && e.getY() <= pillY + pillH) {
                        GroupedTableModel.GroupRow gr = (GroupedTableModel.GroupRow) model.rowAt(row);
                        onAddForRoom(gr.roomKey());
                    } else {
                        model.toggleGroup(row);
                        syncRowHeights(t);
                        t.clearSelection();
                    }
                } else if (e.getClickCount() == 2) {
                    onConnectSelected();
                }
            }
        });

        // Cursor pointer over group rows
        t.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int row = t.rowAtPoint(e.getPoint());
                boolean onGroup = row >= 0 && model.isGroupRow(row);
                t.setCursor(Cursor.getPredefinedCursor(
                        onGroup ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        });

        // Row heights: update whenever model changes
        model.addTableModelListener(e -> SwingUtilities.invokeLater(() -> syncRowHeights(t)));

        // Sortable column headers
        installSortableHeader(t);

        return t;
    }

    // ── Sortable header ───────────────────────────────────────────────────────

    private void installSortableHeader(JTable t) {
        JTableHeader header = t.getTableHeader();

        // Custom renderer: appends ↑ / ↓ to the sorted column
        TableCellRenderer delegate = header.getDefaultRenderer();
        header.setDefaultRenderer((tbl, value, sel, focus, row, col) -> {
            String label = value != null ? value.toString() : "";
            if (col == model.getSortColumn()) {
                label += model.isSortAscending() ? "  \u2191" : "  \u2193"; // ↑ ↓
            }
            Component c = delegate.getTableCellRendererComponent(tbl, label, sel, focus, row, col);
            if (c instanceof JLabel lbl) {
                lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                if (col == model.getSortColumn()) {
                    lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
                }
            }
            return c;
        });

        // Click to sort
        header.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int col = t.columnAtPoint(e.getPoint());
                if (col < 0) return;
                model.setSort(col);
                header.repaint();
            }
        });
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        header.setReorderingAllowed(false);
    }

    // ── Row height sync ───────────────────────────────────────────────────────

    private static void syncRowHeights(JTable t) {
        GroupedTableModel m = (GroupedTableModel) t.getModel();
        for (int row = 0; row < m.getRowCount(); row++) {
            t.setRowHeight(row, m.isGroupRow(row) ? ROW_HEIGHT_GROUP : ROW_HEIGHT_ENTRY);
        }
    }

    // ── Reload / state ────────────────────────────────────────────────────────

    public void reload() {
        clearDecryptedCache();
        String path = ConfigManager.getAddressBookPath();
        boolean hasPath = (path != null && !path.isBlank());
        contentCard.show(contentArea, hasPath ? CONTENT_TABLE : CONTENT_NO_PATH);
        model.setData(hasPath ? store.load() : List.of());
        updateActions();
    }

    private void updateActions() {
        String path = ConfigManager.getAddressBookPath();
        boolean hasPath = (path != null && !path.isBlank());
        int     sel     = table.getSelectedRow();
        boolean entry   = hasPath && sel >= 0 && !model.isGroupRow(sel);
        cmdAdd.setEnabled(hasPath);
        cmdEdit.setEnabled(entry);
        cmdRemove.setEnabled(entry);
        cmdConnectNow.setEnabled(entry);
        cmdTogglePasswords.setEnabled(hasPath && !model.isEmpty());
    }

    // ── User actions ──────────────────────────────────────────────────────────

    private void onAdd() {
        DeviceDialog.show(this, store, null, () -> reload());
    }

    private void onAddForRoom(String roomKey) {
        String room = GroupedTableModel.NO_ROOM.equals(roomKey) ? "" : roomKey;
        DeviceDialog.showForRoom(this, store, room, () -> reload());
    }

    private void onEditSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || model.isGroupRow(row)) return;
        DeviceDialog.show(this, store, model.entryAt(row), () -> reload());
    }

    private void onRemoveSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || model.isGroupRow(row)) return;
        DeviceEntry e = model.entryAt(row);
        String label = e.fullName().isBlank() ? e.anyDeskId() : e.fullName();
        if (JOptionPane.showConfirmDialog(this,
                "Usunąć \"" + label + "\"?", "Potwierdź",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            store.remove(e.anyDeskId());
            reload();
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            JOptionPane.showMessageDialog(this, "Błąd: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onConnectSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || model.isGroupRow(row)) return;
        DeviceEntry e = model.entryAt(row);
        store.markLastConnected(e.anyDeskId(), System.currentTimeMillis());
        reload();
        char[] plain = null;
        if (e.hasPassword()) {
            try { plain = CryptoUtils.decrypt(e.encryptedPassword()); }
            catch (Exception ex) {
                ex.printStackTrace(System.err);
                JOptionPane.showMessageDialog(this,
                        "Nie udało się odszyfrować hasła — łączę bez hasła.",
                        "Błąd szyfrowania", JOptionPane.WARNING_MESSAGE);
            }
        }
        final char[] pwd = plain;
        SwingUtilities.invokeLater(() -> connectAction.accept(e.anyDeskId(), pwd));
    }

    private void onTogglePasswords() {
        if (passwordsVisible) {
            clearDecryptedCache();
            cmdTogglePasswords.setText("Show Passwords");
        } else {
            for (DeviceEntry e : model.allEntries()) {
                if (e.hasPassword() && !decryptedCache.containsKey(e.anyDeskId())) {
                    try { decryptedCache.put(e.anyDeskId(), CryptoUtils.decrypt(e.encryptedPassword())); }
                    catch (Exception ex) { decryptedCache.put(e.anyDeskId(), "?error?".toCharArray()); }
                }
            }
            passwordsVisible = true;
            cmdTogglePasswords.setText("Hide Passwords");
        }
        table.repaint();
    }

    private void clearDecryptedCache() {
        decryptedCache.values().forEach(a -> { if (a != null) Arrays.fill(a, '\0'); });
        decryptedCache.clear();
        passwordsVisible = false;
        if (cmdTogglePasswords != null) cmdTogglePasswords.setText("Show Passwords");
    }

    // ── Cell renderers ────────────────────────────────────────────────────────

    /**
     * Entry-row renderer: transparent for group rows (the overpaint handles them),
     * proper indent for data rows, subtle alternating background within each group.
     */
    private final class EntryRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable t, Object value, boolean sel, boolean focus, int row, int col) {

            Component c = super.getTableCellRendererComponent(t, value, sel, false, row, col);
            if (model.isGroupRow(row)) {
                setBackground(groupHeaderBg());
                setText("");
                setBorder(BorderFactory.createEmptyBorder());
                return c;
            }
            if (!sel) {
                int localIdx = model.localIndexInGroup(row);
                Color base = tableBg();
                Color alt  = blendWith(base, groupHeaderBg(), 0.06f);
                setBackground(localIdx % 2 == 0 ? base : alt);
            }
            setBorder(BorderFactory.createEmptyBorder(0, CELL_PAD_LEFT, 0, CELL_PAD_RIGHT));
            return c;
        }
    }

    private final class PasswordCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable t, Object value, boolean sel, boolean focus, int row, int col) {
            if (model.isGroupRow(row)) {
                setBackground(groupHeaderBg());
                setText("");
                setBorder(BorderFactory.createEmptyBorder());
                return this;
            }
            DeviceEntry e = model.entryAt(row);
            String display;
            if (!e.hasPassword()) {
                display = "—";
            } else if (passwordsVisible) {
                char[] pwd = decryptedCache.get(e.anyDeskId());
                display = pwd != null ? new String(pwd) : "••••••••";
            } else {
                display = "••••••••";
            }
            if (!sel) {
                int localIdx = model.localIndexInGroup(row);
                Color base = tableBg();
                Color alt  = blendWith(base, groupHeaderBg(), 0.09f);
                setBackground(localIdx % 2 == 0 ? base : alt);
            }
            super.getTableCellRendererComponent(t, display, sel, false, row, col);
            setBorder(BorderFactory.createEmptyBorder(0, CELL_PAD_LEFT, 0, CELL_PAD_RIGHT));
            return this;
        }
    }

    // ── Color / blend helpers ─────────────────────────────────────────────────

    private static Color tableBg() {
        Color c = UIManager.getColor("Table.background");
        return c != null ? c : Color.DARK_GRAY;
    }

    private static Color groupHeaderBg() {
        Color c = UIManager.getColor("TableHeader.background");
        return c != null ? c : UIManager.getColor("Panel.background");
    }

    private static Color groupHeaderFg() {
        Color c = UIManager.getColor("TableHeader.foreground");
        return c != null ? c : UIManager.getColor("Label.foreground");
    }

    private static Color dividerColor() {
        Color c = UIManager.getColor("Component.borderColor");
        return c != null ? c : Color.GRAY;
    }

    private static Color accentColor() {
        Color c = UIManager.getColor("App.accentColor");
        return c != null ? c : new Color(0xD92B34);
    }

    private static Color blendWith(Color base, Color overlay, float t) {
        if (base == null) return overlay;
        if (overlay == null) return base;
        t = Math.max(0f, Math.min(1f, t));
        return new Color(
                Math.round(base.getRed()   * (1 - t) + overlay.getRed()   * t),
                Math.round(base.getGreen() * (1 - t) + overlay.getGreen() * t),
                Math.round(base.getBlue()  * (1 - t) + overlay.getBlue()  * t));
    }

    // ── Button factory ────────────────────────────────────────────────────────

    private static JButton btn(String text, java.awt.event.ActionListener al) {
        JButton b = new JButton(text);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.putClientProperty(FlatClientProperties.STYLE, "arc:14;margin:10,14,10,14;");
        b.addActionListener(al);
        return b;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Grouped table model
    // ══════════════════════════════════════════════════════════════════════════

    static final class GroupedTableModel extends AbstractTableModel {

        /** Sentinel key for entries that have no room number — sorted last. */
        static final String NO_ROOM = "\uFFFF";

        sealed interface TableRow permits GroupRow, EntryRow {}
        record GroupRow(String roomKey, int count, boolean collapsed) implements TableRow {}
        record EntryRow(DeviceEntry entry)                            implements TableRow {}

        private static final String[] COLS = {
            "Imię i nazwisko", "Hostname", "AnyDesk ID", "Hasło", "Ostatnie połączenie"
        };
        private static final DateTimeFormatter DF =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

        /** Numeric-first comparator for room keys; NO_ROOM sorts last. */
        private static final Comparator<String> ROOM_ORDER = (a, b) -> {
            if (NO_ROOM.equals(a) && NO_ROOM.equals(b)) return 0;
            if (NO_ROOM.equals(a)) return  1;
            if (NO_ROOM.equals(b)) return -1;
            try { return Integer.compare(Integer.parseInt(a), Integer.parseInt(b)); }
            catch (NumberFormatException e) { return a.compareToIgnoreCase(b); }
        };

        // ── State ─────────────────────────────────────────────────────────────

        private List<TableRow>   visible   = new ArrayList<>();
        private List<DeviceEntry> entries  = List.of();
        private final Set<String> collapsed = new HashSet<>();

        // Sorting: default = last connected, descending
        private int     sortColumn    = COL_LAST;
        private boolean sortAscending = false;

        // ── Public API ────────────────────────────────────────────────────────

        void setData(List<DeviceEntry> data) {
            this.entries = data != null ? List.copyOf(data) : List.of();
            rebuild();
        }

        void toggleGroup(int row) {
            if (!(visible.get(row) instanceof GroupRow gr)) return;
            if (gr.collapsed()) collapsed.remove(gr.roomKey());
            else                collapsed.add(gr.roomKey());
            rebuild();
        }

        /** Called by header click. Toggles direction when same column, resets to asc for new one. */
        void setSort(int col) {
            if (this.sortColumn == col) sortAscending = !sortAscending;
            else { sortColumn = col; sortAscending = true; }
            rebuild();
        }

        int     getSortColumn()    { return sortColumn; }
        boolean isSortAscending()  { return sortAscending; }
        boolean isGroupRow(int row){ return row >= 0 && row < visible.size()
                                        && visible.get(row) instanceof GroupRow; }
        TableRow rowAt(int row)    { return visible.get(row); }
        boolean isEmpty()          { return entries.isEmpty(); }
        List<DeviceEntry> allEntries() { return entries; }

        DeviceEntry entryAt(int row) {
            if (visible.get(row) instanceof EntryRow er) return er.entry();
            throw new IllegalArgumentException("Row " + row + " is a group header.");
        }

        /** True when {@code row} is the last entry row in its group (used for the connector elbow). */
        boolean isLastInGroup(int row) {
            if (row < 0 || row >= visible.size() || visible.get(row) instanceof GroupRow) return false;
            return row == visible.size() - 1 || visible.get(row + 1) instanceof GroupRow;
        }

        /** 0-based index of an entry row within its group (used for zebra striping). */
        int localIndexInGroup(int row) {
            int idx = 0;
            for (int i = row - 1; i >= 0; i--) {
                if (visible.get(i) instanceof GroupRow) break;
                idx++;
            }
            return idx;
        }

        // ── Rebuild ───────────────────────────────────────────────────────────

        private void rebuild() {
            // Group by room key
            Map<String, List<DeviceEntry>> groups = new LinkedHashMap<>();
            for (DeviceEntry e : entries) {
                String key = (e.roomNumber() == null || e.roomNumber().isBlank())
                             ? NO_ROOM : e.roomNumber().trim();
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
            }

            // Sort group keys numerically
            List<String> keys = new ArrayList<>(groups.keySet());
            keys.sort(ROOM_ORDER);

            // Build entry comparator for the current sort column
            Comparator<DeviceEntry> cmp = entryComparator();

            // Assemble visible rows
            List<TableRow> rows = new ArrayList<>();
            for (String key : keys) {
                List<DeviceEntry> group = groups.get(key);
                boolean isCollapsed = collapsed.contains(key);
                rows.add(new GroupRow(key, group.size(), isCollapsed));
                if (!isCollapsed) {
                    group.sort(cmp);
                    group.forEach(e -> rows.add(new EntryRow(e)));
                }
            }
            this.visible = rows;
            fireTableDataChanged();
        }

        private Comparator<DeviceEntry> entryComparator() {
            Comparator<DeviceEntry> cmp = switch (sortColumn) {
                case COL_NAME ->
                    Comparator.comparing(DeviceEntry::fullName,
                            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case COL_HOST ->
                    Comparator.comparing(DeviceEntry::hostname,
                            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case COL_ID ->
                    Comparator.comparing(DeviceEntry::anyDeskId,
                            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                case COL_PASSWORD ->
                    // Entries with a password float to the top
                    Comparator.comparing(e -> e.hasPassword() ? 0 : 1);
                case COL_LAST ->
                    Comparator.comparingLong(DeviceEntry::lastConnectedEpochMs);
                default ->
                    Comparator.comparing(DeviceEntry::fullName,
                            Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            };
            return sortAscending ? cmp : cmp.reversed();
        }

        // ── AbstractTableModel ────────────────────────────────────────────────

        @Override public int    getRowCount()              { return visible.size(); }
        @Override public int    getColumnCount()           { return COLS.length; }
        @Override public String getColumnName(int col)     { return COLS[col]; }
        @Override public boolean isCellEditable(int r, int c) { return false; }

        @Override
        public Object getValueAt(int row, int col) {
            if (visible.get(row) instanceof GroupRow) return ""; // overpainted
            DeviceEntry e = ((EntryRow) visible.get(row)).entry();
            return switch (col) {
                case COL_NAME     -> e.fullName();
                case COL_HOST     -> e.hostname();
                case COL_ID       -> e.anyDeskId();
                case COL_PASSWORD -> e.hasPassword() ? "••••••••" : "—";
                case COL_LAST     -> e.lastConnectedEpochMs() <= 0 ? "—"
                        : DF.format(Instant.ofEpochMilli(e.lastConnectedEpochMs()));
                default           -> "";
            };
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Add / Edit dialog
    // ══════════════════════════════════════════════════════════════════════════

    static final class DeviceDialog extends JDialog {

        interface Callback { void onSaved(); }

        private final AddressBookStore store;
        private final DeviceEntry      existing;
        private final Callback         callback;

        private JComboBox<String> cmbRoom;
        private JTextField     txtName, txtHost, txtId;
        private JPasswordField txtPassword;
        private JLabel         lblStatus;
        private JButton        cmdTest;

        private String prefillRoom;
        private String prefillId;

        private DeviceDialog(Window owner, AddressBookStore store,
                             DeviceEntry existing, Callback callback) {
            this(owner, store, existing, null, callback);
        }

        private DeviceDialog(Window owner, AddressBookStore store,
                             DeviceEntry existing, String prefillRoom, Callback callback) {
            super(owner, existing == null ? "Add Device" : "Edit Device",
                  Dialog.ModalityType.APPLICATION_MODAL);
            this.store = store; this.existing = existing;
            this.prefillRoom = prefillRoom; this.prefillId = null;
            this.callback = callback;
            initUi();
            pack();
            setResizable(false);
            setLocationRelativeTo(owner);
        }

        static void show(JPanel parent, AddressBookStore store,
                         DeviceEntry existing, Callback callback) {
            new DeviceDialog(SwingUtilities.getWindowAncestor(parent),
                             store, existing, callback).setVisible(true);
        }

        static void showForRoom(JPanel parent, AddressBookStore store,
                                String roomNumber, Callback callback) {
            new DeviceDialog(SwingUtilities.getWindowAncestor(parent),
                             store, null, roomNumber, callback).setVisible(true);
        }

        /**
         * Opens the Add Device dialog with a pre-filled AnyDesk ID.
         * Called from Recent Connections to quickly save a device to the address book.
         */
        static void showForId(JPanel parent, AddressBookStore store,
                              String anyDeskId, Callback callback) {
            DeviceDialog dlg = new DeviceDialog(SwingUtilities.getWindowAncestor(parent),
                    store, null, null, callback);
            dlg.prefillId = anyDeskId;
            dlg.txtId.setText(anyDeskId != null ? anyDeskId : "");
            dlg.pack();
            dlg.setLocationRelativeTo(SwingUtilities.getWindowAncestor(parent));
            dlg.setVisible(true);
        }

        private void initUi() {
            boolean edit = existing != null;
            JPanel root = new JPanel(new MigLayout("wrap,fillx,insets 20", "[fill,380!]", "[]8[]"));
            root.putClientProperty(FlatClientProperties.STYLE, "background:$Panel.background");

            JLabel heading = new JLabel(edit ? "Edit Device" : "Add Device");
            heading.putClientProperty(FlatClientProperties.STYLE, "font:+4");

            // Room combo: editable, pre-populated with existing rooms
            cmbRoom = new JComboBox<>();
            cmbRoom.setEditable(true);
            cmbRoom.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Wybierz lub wpisz nowy");
            java.util.TreeSet<String> rooms = new java.util.TreeSet<>();
            for (DeviceEntry d : store.load()) {
                String r = d.roomNumber();
                if (r != null && !r.isBlank()) rooms.add(r.trim());
            }
            for (String r : rooms) cmbRoom.addItem(r);
            cmbRoom.setSelectedItem("");  // start empty

            txtName = field("np. Jan Kowalski");
            txtHost = field("np. PC-ROOM101");
            txtId   = field("cyfry i spacje");
            ((javax.swing.text.AbstractDocument) txtId.getDocument())
                    .setDocumentFilter(AnyDeskId.digitsAndSpacesFilter());
            txtPassword = new JPasswordField();
            txtPassword.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT,
                    edit && existing.hasPassword()
                    ? "Pozostaw puste, aby zachować aktualne hasło"
                    : "Opcjonalne — pozostaw puste jeśli brak hasła");

            if (edit) {
                cmbRoom.setSelectedItem(existing.roomNumber());
                txtName.setText(existing.fullName());
                txtHost.setText(existing.hostname());
                txtId.setText(existing.anyDeskId());
            } else if (prefillRoom != null && !prefillRoom.isEmpty()) {
                cmbRoom.setSelectedItem(prefillRoom);
            }

            lblStatus = new JLabel(" ");
            lblStatus.putClientProperty(FlatClientProperties.STYLE,
                    "foreground:$Label.disabledForeground");

            cmdTest = new JButton("Test połączenia");
            cmdTest.putClientProperty(FlatClientProperties.STYLE, "arc:10;margin:8,14,8,14;");
            cmdTest.addActionListener(e -> onTest());

            JButton cmdSave = new JButton(edit ? "Zapisz zmiany" : "Dodaj urządzenie");
            cmdSave.putClientProperty(FlatClientProperties.STYLE,
                    "arc:10;margin:8,14,8,14;borderWidth:0;focusWidth:0;innerFocusWidth:0;"
                    + "background:$App.accentColor;foreground:#ffffff;");
            cmdSave.addActionListener(e -> onSave());

            JButton cmdCancel = new JButton("Anuluj");
            cmdCancel.putClientProperty(FlatClientProperties.STYLE, "arc:10;margin:8,14,8,14;");
            cmdCancel.addActionListener(e -> dispose());

            JPanel btnRow = new JPanel(new MigLayout("insets 0", "[]8[]push[]", "[]"));
            btnRow.putClientProperty(FlatClientProperties.STYLE, "background:null");
            btnRow.add(cmdSave); btnRow.add(cmdCancel); btnRow.add(cmdTest, "align right");

            root.add(heading,                       "gapy 0 8");
            root.add(new JLabel("Numer pokoju"));   root.add(cmbRoom);
            root.add(new JLabel("Imię i nazwisko"),"gapy 6"); root.add(txtName);
            root.add(new JLabel("Hostname"),        "gapy 6"); root.add(txtHost);
            root.add(new JLabel("Numer AnyDesk"),   "gapy 6"); root.add(txtId);
            root.add(new JLabel("Hasło"),           "gapy 6"); root.add(txtPassword);
            root.add(lblStatus,                     "gapy 4");
            root.add(btnRow,                        "gapy 12,growx");

            setContentPane(root);
            getRootPane().setDefaultButton(cmdSave);
        }

        private static JTextField field(String ph) {
            JTextField f = new JTextField();
            f.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, ph);
            return f;
        }

        private void onSave() {
            Object sel = cmbRoom.getSelectedItem();
            String room = sel != null ? sel.toString().trim() : "";
            String name = txtName.getText().trim();
            String host = txtHost.getText().trim();
            String id   = txtId.getText().trim();

            if (name.isEmpty() && room.isEmpty()) {
                setStatus("Podaj przynajmniej imię lub numer pokoju.", true); return;
            }
            if (!AnyDeskId.isValid(id)) {
                setStatus("Nieprawidłowy numer AnyDesk (tylko cyfry i spacje).", true); return;
            }

            char[] rawPwd = txtPassword.getPassword();
            String encPwd;
            if (rawPwd.length > 0) {
                try { encPwd = CryptoUtils.encrypt(rawPwd); }
                catch (Exception ex) {
                    Arrays.fill(rawPwd, '\0');
                    setStatus("Błąd szyfrowania: " + ex.getMessage(), true); return;
                }
            } else {
                Arrays.fill(rawPwd, '\0');
                encPwd = (existing != null && existing.hasPassword()) ? existing.encryptedPassword() : "";
            }

            long ts = existing != null ? existing.lastConnectedEpochMs() : System.currentTimeMillis();
            try {
                store.upsert(new DeviceEntry(room, name, host, id, encPwd, ts));
                if (callback != null) callback.onSaved();
                dispose();
            } catch (Exception ex) {
                ex.printStackTrace(System.err);
                setStatus("Błąd zapisu: " + ex.getMessage(), true);
            }
        }

        private void onTest() {
            String id = txtId.getText().trim();
            if (!AnyDeskId.isValid(id)) {
                setStatus("Wprowadź prawidłowy numer AnyDesk.", true); return;
            }
            char[] rawPwd = txtPassword.getPassword();
            cmdTest.setEnabled(false);
            setStatus("Uruchamianie AnyDesk…", false);
            new Thread(() -> {
                try {
                    Process proc;
                    if (rawPwd.length > 0) {
                        char[] copy = rawPwd.clone(); Arrays.fill(rawPwd, '\0');
                        proc = AnyDeskLauncher.launchWithPassword(id, copy);
                    } else {
                        Arrays.fill(rawPwd, '\0');
                        proc = AnyDeskLauncher.launch(id);
                    }
                    Thread.sleep(2_000);
                    if (!proc.isAlive() && proc.exitValue() != 0) {
                        int code = proc.exitValue();
                        SwingUtilities.invokeLater(() ->
                                setStatus("AnyDesk zakończył się kodem " + code + ".", true));
                    } else {
                        SwingUtilities.invokeLater(() ->
                                setStatus("AnyDesk uruchomiony — sprawdź okno AnyDesk.", false));
                    }
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> setStatus("Błąd: " + ex.getMessage(), true));
                } finally {
                    Arrays.fill(rawPwd, '\0');
                    SwingUtilities.invokeLater(() -> cmdTest.setEnabled(true));
                }
            }, "anydesk-test").start();
        }

        private void setStatus(String text, boolean error) {
            lblStatus.setText(text);
            Color c = error ? Color.decode("#ef4444")
                            : UIManager.getColor("Label.disabledForeground");
            lblStatus.setForeground(c != null ? c : Color.GRAY);
        }
    }
}
