package raven.ui;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.prefs.Preferences;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.miginfocom.swing.MigLayout;
import raven.addressbook.AddressBookStore;
import raven.anydesk.AnyDeskCleanupService;
import raven.anydesk.AnyDeskId;
import raven.anydesk.AnyDeskLauncher;
import raven.anydesk.AnyDeskSettings;
import raven.history.ConnectionHistoryStore;
import raven.history.ConnectionRecord;
import raven.ui.SuggestionTextField.SuggestionItem;

public class AnyDeskConnectView extends JPanel {

    private static final String PREF_KEY_RECENTS = "anydesk.recents";
    private static final int MAX_RECENTS = 10;

    private final ExecutorService launcherExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "anydesk-launcher");
        t.setDaemon(true);
        return t;
    });

    private final Preferences prefs = Preferences.userNodeForPackage(AnyDeskConnectView.class);

    private SuggestionTextField txtId;
    private JButton cmdConnect;
    private final BiConsumer<String, Boolean> statusSink;

    /**
     * Password to use for the next connection, supplied by the Address Book when
     * launching a contact that has a saved credential. Owned by this class;
     * zeroed immediately after it is passed to the launcher.
     */
    private volatile char[] pendingPassword;

    private final ConnectionHistoryStore historyStore = new ConnectionHistoryStore();
    private final AddressBookStore addressBookStore = new AddressBookStore();

    private JTable historyTable;
    private HistoryTableModel historyModel;

    public AnyDeskConnectView(BiConsumer<String, Boolean> statusSink) {
        this.statusSink = statusSink != null ? statusSink : (m, e) -> {
        };
        init();
        loadHistory();
        updateActions();
    }

    private void init() {
        putClientProperty(FlatClientProperties.STYLE, "background:null");
        setLayout(new MigLayout("fill,insets 0", "[grow,fill]", "[grow]"));

        JPanel card = new JPanel(new MigLayout("wrap,fill,insets 26", "[grow,fill]", "[]14[]14[]10[]10[grow]"));
        card.putClientProperty(FlatClientProperties.STYLE, ""
                + "arc:24;"
                + "border:1,1,1,1,$Component.borderColor,,24;"
                + "background:$Panel.background");

        JLabel title = new JLabel("AnyDesk Connect");
        title.putClientProperty(FlatClientProperties.STYLE, "font:+7");

        JLabel subtitle = new JLabel("Enter an AnyDesk ID and launch AnyDesk.");
        subtitle.putClientProperty(FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");

        txtId = new SuggestionTextField();
        txtId.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "ID or contact name…");
        txtId.putClientProperty(FlatClientProperties.STYLE, ""
                + "arc:14;"
                + "margin:10,12,10,12;");
        // No DocumentFilter — the field accepts names for suggestion search.
        // Validation (digits only) is enforced at connection time in isValidId().
        txtId.addActionListener(e -> executeConnection()); // Enter with no popup open
        txtId.setOnAccepted(this::executeConnection);      // Enter / click on highlighted suggestion
        txtId.setSuggestionProvider(this::suggest);
        txtId.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateActions();
                clearErrorOutlineIfValid();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateActions();
                clearErrorOutlineIfValid();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateActions();
                clearErrorOutlineIfValid();
            }
        });

        cmdConnect = new JButton("Connect");
        cmdConnect.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cmdConnect.putClientProperty(FlatClientProperties.STYLE, ""
                + "arc:14;"
                + "margin:10,22,10,22;"
                + "borderWidth:0;"
                + "focusWidth:0;"
                + "innerFocusWidth:0;"
                + "background:#D92B34;"
                + "foreground:#ffffff;"
                + "font:bold;"
                + "hoverBackground:lighten(#D92B34,6%);"
                + "pressedBackground:darken(#D92B34,6%);");
        cmdConnect.addActionListener(e -> executeConnection());
        cmdConnect.putClientProperty("JButton.buttonType", "borderless");

        historyModel = new HistoryTableModel();
        historyTable = new JTable(historyModel);
        historyTable.setRowHeight(34);
        historyTable.setShowGrid(false);
        historyTable.setIntercellSpacing(new Dimension(0, 0));
        historyTable.getTableHeader().putClientProperty(FlatClientProperties.STYLE, "height:32");
        historyTable.putClientProperty(FlatClientProperties.STYLE, ""
                + "selectionArc:10;");
        historyTable.setDefaultRenderer(Object.class, new AlternatingRowRenderer());

        JScrollPane historyScroll = new JScrollPane(historyTable);
        historyScroll.putClientProperty(FlatClientProperties.STYLE, ""
                + "arc:14;"
                + "border:1,1,1,1,$Component.borderColor,,14;");
        historyScroll.setPreferredSize(new Dimension(10, 150));

        JLabel recentLabel = new JLabel("Recent Connections");
        recentLabel.putClientProperty(FlatClientProperties.STYLE, "font:+1");

        card.add(title);
        card.add(subtitle);
        card.add(new JLabel("AnyDesk ID"), "gapy 6");
        card.add(txtId, "h 44!");
        // Single primary action, aligned to the right under the input
        card.add(cmdConnect, "align right, w 140!, h 42!");
        card.add(recentLabel, "gapy 6");
        // Expandable table: fill remaining vertical space
        card.add(historyScroll, "grow, pushy");

        add(card, "grow, push");
    }

    private void executeConnection() {
        String raw = txtId.getText();
        if (!isValidId(raw)) {
            txtId.putClientProperty("JComponent.outline", "error");
            setStatus("Invalid ID. Use numbers and spaces only.", true);
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        txtId.putClientProperty("JComponent.outline", null);

        String id = raw.trim();
        setBusy(true);
        setStatus("Launching AnyDesk for ID " + id + "...", false);

        // Take ownership of the pending password (may be null = no password).
        final char[] password = this.pendingPassword;
        this.pendingPassword = null;

        launcherExecutor.execute(() -> {
            Process process = null;
            try {
                LocalDateTime start = LocalDateTime.now();
                if (password != null && password.length > 0) {
                    process = AnyDeskLauncher.launchWithPassword(id, password); // zeroes password
                } else {
                    if (password != null) {
                        Arrays.fill(password, '\0');
                    }
                    process = AnyDeskLauncher.launch(id);
                }
                addRecent(id); // keep legacy simple recents (used by suggestions fallback)
                SwingUtilities.invokeLater(() -> setStatus("Launched AnyDesk.", false));

                int exit = process.waitFor();   // IMPORTANT: never on EDT
                LocalDateTime end = LocalDateTime.now();
                System.out.println("AnyDesk exited with code: " + exit);

                long durationSeconds = Math.max(0, Duration.between(start, end).getSeconds());
                ConnectionRecord record = new ConnectionRecord(id, end, durationSeconds);
                historyStore.append(record);
                SwingUtilities.invokeLater(this::loadHistory);

                if (AnyDeskSettings.isCleanupEnabled()) {
                    SwingUtilities.invokeLater(() -> setStatus("Connection ended. Cleaning traces...", false));
                    AnyDeskCleanupService svc = new AnyDeskCleanupService();

                    AnyDeskCleanupService.CleanupResult result = tryCleanupWithRetry(svc, 3, 600);
                    if (result.error != null) {
                        System.err.println(result.message);
                        result.error.printStackTrace(System.err);
                    }
                    SwingUtilities.invokeLater(() -> setStatus(
                            result.cleaned ? "Connection ended. Traces cleaned." : "Connection ended. " + result.message,
                            result.error != null
                    ));
                } else {
                    SwingUtilities.invokeLater(() -> setStatus("Connection ended.", false));
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> setStatus("Failed to launch: " + ex.getMessage(), true));
                ex.printStackTrace(System.err);
            } finally {
                if (process != null) {
                    process.destroy();
                }
                SwingUtilities.invokeLater(() -> setBusy(false));
            }
        });
    }

    private boolean isValidId(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        // Remove whitespace and require 9-10 digits (common AnyDesk ID length)
        String cleanId = id.replaceAll("\\s+", "");
        return cleanId.matches("\\d{9,10}");
    }

    private void clearErrorOutlineIfValid() {
        if ("error".equals(txtId.getClientProperty("JComponent.outline")) && isValidId(txtId.getText())) {
            txtId.putClientProperty("JComponent.outline", null);
        }
    }

    private AnyDeskCleanupService.CleanupResult tryCleanupWithRetry(AnyDeskCleanupService svc, int maxAttempts, long delayMs) {
        AnyDeskCleanupService.CleanupResult last = null;
        for (int attempt = 1; attempt <= Math.max(1, maxAttempts); attempt++) {
            last = svc.cleanAnyDeskFolder();
            if (last.cleaned || last.skipped) {
                return last;
            }
            // If still failing (often due to locks), wait a little and retry.
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return AnyDeskCleanupService.CleanupResult.error("Cleanup interrupted.", ie);
            }
        }
        return last != null ? last : AnyDeskCleanupService.CleanupResult.error("Cleanup failed.", new RuntimeException("Unknown cleanup error"));
    }

    private void setBusy(boolean busy) {
        txtId.setEnabled(!busy);
        cmdConnect.setEnabled(!busy);
        if (!busy) {
            updateActions();
        }
    }

    private void updateActions() {
        boolean hasText = txtId != null && !txtId.getText().trim().isEmpty();
        if (txtId != null && txtId.isEnabled()) {
            cmdConnect.setEnabled(hasText);
        }
    }

    private void setStatus(String message, boolean error) {
        statusSink.accept(message, error);
    }

    private void addRecent(String id) {
        SwingUtilities.invokeLater(() -> {
            String normalized = id.trim();
            if (normalized.isEmpty()) {
                return;
            }
            saveRecents();
        });
    }

    private void loadHistory() {
        historyModel.setData(historyStore.load());
    }

    private void saveRecents() {
        // Keep a simple legacy list for suggestion fallback (no duration). Stored as pipe-separated IDs.
        // We only keep the newest N by prefixing and de-duping against existing.
        String raw = prefs.get(PREF_KEY_RECENTS, "");
        List<String> existing = new ArrayList<>();
        if (raw != null && !raw.isBlank()) {
            for (String part : raw.split("\\|")) {
                String v = part.trim();
                if (!v.isEmpty() && isValidId(v) && !existing.contains(v)) {
                    existing.add(v);
                }
            }
        }
        String current = txtId.getText() != null ? txtId.getText().trim() : "";
        if (isValidId(current)) {
            existing.remove(current);
            existing.add(0, current);
        }
        while (existing.size() > MAX_RECENTS) {
            existing.remove(existing.size() - 1);
        }
        prefs.put(PREF_KEY_RECENTS, String.join("|", existing));
    }

    /**
     * Initiates a connection to the given ID without a saved password.
     * Equivalent to the user typing the ID and pressing Connect.
     */
    public void startConnection(String anyDeskId) {
        startConnection(anyDeskId, null);
    }

    /**
     * Initiates a connection to the given ID using a pre-decrypted password.
     * The {@code password} array is owned by this method and will be zeroed
     * after the launcher has consumed it. Pass {@code null} for no password.
     */
    public void startConnection(String anyDeskId, char[] password) {
        if (anyDeskId == null) {
            return;
        }
        // Zero any previously pending password that was never consumed
        if (this.pendingPassword != null) {
            Arrays.fill(this.pendingPassword, '\0');
        }
        this.pendingPassword = password;
        txtId.setText(anyDeskId);
        executeConnection();
    }

    private List<SuggestionItem> suggest(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        String qLower = q.toLowerCase(Locale.ROOT);
        String qDigits = q.replaceAll("\\s+", ""); // for ID matching

        List<SuggestionItem> out = new ArrayList<>();

        // 1) Address book — match by ID, full name, room number, or hostname
        for (var d : addressBookStore.load()) {
            String id = d.anyDeskId();
            if (matchesId(id, qDigits)
                    || matchesName(d.fullName(),   qLower)
                    || matchesName(d.roomNumber(),  qLower)
                    || matchesName(d.hostname(),    qLower)) {
                // Show the human-readable label (name / room / hostname) first, ID after
                out.add(new SuggestionItem(id, d.displayLabel() + "  —  " + id));
            }
        }

        // 2) History — match by ID only (no name stored)
        for (var r : historyStore.load()) {
            String id = r.anydeskId();
            if (matchesId(id, qDigits) && out.stream().noneMatch(x -> x.value().equals(id))) {
                out.add(new SuggestionItem(id, id + "  —  recent"));
            }
        }

        // 3) Mock LAN devices
        for (String mock : List.of("111 222 333", "999 888 777")) {
            if (matchesId(mock, qDigits) && out.stream().noneMatch(x -> x.value().equals(mock))) {
                out.add(new SuggestionItem(mock, mock + "  —  LAN Device (mock)"));
            }
        }
        return out;
    }

    /** ID match: the raw ID or its digit-only form contains the digit query string. */
    private boolean matchesId(String id, String qDigits) {
        if (id == null || id.isBlank() || qDigits.isEmpty()) {
            return false;
        }
        String s = id.trim();
        return s.contains(qDigits) || s.replaceAll("\\s+", "").contains(qDigits);
    }

    /** Name match: case-insensitive substring. */
    private boolean matchesName(String name, String qLower) {
        return name != null && !name.isBlank()
                && name.toLowerCase(Locale.ROOT).contains(qLower);
    }

    private static final class HistoryTableModel extends javax.swing.table.AbstractTableModel {
        private java.util.List<ConnectionRecord> data = java.util.List.of();
        private static final String[] COLS = {"AnyDesk ID", "Last Connected", "Duration"};
        private static final java.time.format.DateTimeFormatter DF = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        void setData(java.util.List<ConnectionRecord> list) {
            data = list != null ? list : java.util.List.of();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public int getColumnCount() {
            return COLS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ConnectionRecord r = data.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> r.anydeskId();
                case 1 -> r.connectionDate().format(DF);
                case 2 -> formatDuration(r.durationSeconds());
                default -> "";
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        private static String formatDuration(long seconds) {
            long s = Math.max(0, seconds);
            long mm = s / 60;
            long ss = s % 60;
            return String.format("%02d:%02d", mm, ss);
        }
    }

    private static final class AlternatingRowRenderer extends javax.swing.table.DefaultTableCellRenderer {
        @Override
        public java.awt.Component getTableCellRendererComponent(javax.swing.JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            java.awt.Component c = super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
            if (!isSelected) {
                java.awt.Color base = javax.swing.UIManager.getColor("Table.background");
                if (base == null) {
                    base = javax.swing.UIManager.getColor("List.background");
                }
                java.awt.Color alt = base != null ? new java.awt.Color(base.getRed(), base.getGreen(), base.getBlue(), base.getAlpha()) : java.awt.Color.DARK_GRAY;
                // subtle alternating effect by mixing with selection/background
                java.awt.Color mixed = (row % 2 == 0) ? base : blend(base, javax.swing.UIManager.getColor("Panel.background"), 0.08f);
                c.setBackground(mixed != null ? mixed : alt);
            }
            setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 10, 0, 10));
            return c;
        }

        private static java.awt.Color blend(java.awt.Color a, java.awt.Color b, float t) {
            if (a == null) return b;
            if (b == null) return a;
            t = Math.max(0f, Math.min(1f, t));
            int r = (int) Math.round(a.getRed() * (1 - t) + b.getRed() * t);
            int g = (int) Math.round(a.getGreen() * (1 - t) + b.getGreen() * t);
            int bl = (int) Math.round(a.getBlue() * (1 - t) + b.getBlue() * t);
            return new java.awt.Color(r, g, bl);
        }
    }
}

