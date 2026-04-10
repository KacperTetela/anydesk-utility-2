package raven.ui;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Cursor;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import net.miginfocom.swing.MigLayout;
import raven.anydesk.AnyDeskId;
import raven.addressbook.AddressBookStore;
import raven.addressbook.DeviceEntry;

public class AddressBookView extends JPanel {

    private final AddressBookStore store = new AddressBookStore();
    private final Consumer<String> connectAction;

    private JTable table;
    private DeviceTableModel model;
    private JButton cmdConnectNow;

    public AddressBookView(Consumer<String> connectAction) {
        this.connectAction = connectAction;
        init();
        reload();
    }

    private void init() {
        putClientProperty(FlatClientProperties.STYLE, "background:null");
        setLayout(new MigLayout("fill,insets 0", "[grow,fill]", "[grow]"));

        JPanel card = new JPanel(new MigLayout("wrap,fill,insets 22", "[grow,fill]", "[]12[grow]12[]"));
        card.putClientProperty(FlatClientProperties.STYLE, ""
                + "arc:20;"
                + "border:1,1,1,1,$Component.borderColor,,20;"
                + "background:$Panel.background");

        JLabel title = new JLabel("Address Book");
        title.putClientProperty(FlatClientProperties.STYLE, "font:+6");

        model = new DeviceTableModel();
        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(34);
        table.putClientProperty(FlatClientProperties.STYLE, ""
                + "showHorizontalLines:true;"
                + "showVerticalLines:false;");
        table.getTableHeader().putClientProperty(FlatClientProperties.STYLE, "height:32");

        JScrollPane scroll = new JScrollPane(table);
        scroll.putClientProperty(FlatClientProperties.STYLE, ""
                + "arc:14;"
                + "border:1,1,1,1,$Component.borderColor,,14;");

        JButton cmdAdd = new JButton("Add Device");
        cmdAdd.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cmdAdd.putClientProperty(FlatClientProperties.STYLE, "arc:14;margin:10,14,10,14;");
        cmdAdd.addActionListener(e -> onAdd());

        cmdConnectNow = new JButton("Connect Now");
        cmdConnectNow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cmdConnectNow.putClientProperty(FlatClientProperties.STYLE, ""
                + "arc:14;"
                + "margin:10,14,10,14;"
                + "borderWidth:0;"
                + "focusWidth:0;"
                + "innerFocusWidth:0;"
                + "background:$App.accentColor;"
                + "foreground:#ffffff;");
        cmdConnectNow.addActionListener(e -> onConnectSelected());

        JButton cmdRemove = new JButton("Remove");
        cmdRemove.putClientProperty(FlatClientProperties.STYLE, "arc:14;margin:10,14,10,14;");
        cmdRemove.addActionListener(e -> onRemoveSelected());

        JPanel actions = new JPanel(new MigLayout("insets 0,fillx", "[fill]10[fill]10[fill]", "[]"));
        actions.putClientProperty(FlatClientProperties.STYLE, "background:null");
        actions.add(cmdAdd);
        actions.add(cmdRemove);
        actions.add(cmdConnectNow);

        card.add(title);
        card.add(scroll, "grow");
        card.add(actions, "dock south");

        add(card, "dock center");

        table.getSelectionModel().addListSelectionListener(e -> updateActions());
        updateActions();
    }

    private void reload() {
        model.setData(store.load());
        updateActions();
    }

    private void updateActions() {
        boolean selected = table.getSelectedRow() >= 0;
        cmdConnectNow.setEnabled(selected);
    }

    private void onAdd() {
        AddDeviceDialog.Result r = AddDeviceDialog.show(this);
        if (r == null) {
            return;
        }
        try {
            store.upsert(new DeviceEntry(r.name(), r.anyDeskId(), System.currentTimeMillis()));
            reload();
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            JOptionPane.showMessageDialog(this, "Failed to save device: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onRemoveSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        DeviceEntry entry = model.getAt(row);
        int ok = JOptionPane.showConfirmDialog(this, "Remove \"" + entry.name() + "\"?", "Confirm", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            store.remove(entry.anyDeskId());
            reload();
        } catch (Exception ex) {
            ex.printStackTrace(System.err);
            JOptionPane.showMessageDialog(this, "Failed to remove device: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onConnectSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        DeviceEntry entry = model.getAt(row);
        store.markLastConnected(entry.anyDeskId(), System.currentTimeMillis());
        reload();
        SwingUtilities.invokeLater(() -> connectAction.accept(entry.anyDeskId()));
    }

    private static final class DeviceTableModel extends AbstractTableModel {
        private java.util.List<DeviceEntry> data = java.util.List.of();

        private static final String[] COLS = {"Name", "AnyDesk ID", "Last Connected"};
        private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault());

        void setData(java.util.List<DeviceEntry> list) {
            data = list != null ? list : java.util.List.of();
            fireTableDataChanged();
        }

        DeviceEntry getAt(int row) {
            return data.get(row);
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
            DeviceEntry e = data.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> e.name();
                case 1 -> e.anyDeskId();
                case 2 -> formatLastConnected(e.lastConnectedEpochMs());
                default -> "";
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        private static String formatLastConnected(long epochMs) {
            if (epochMs <= 0) {
                return "—";
            }
            return DF.format(Instant.ofEpochMilli(epochMs));
        }
    }

    private record AddDeviceDialog() {
        record Result(String name, String anyDeskId) {
        }

        static Result show(JPanel parent) {
            javax.swing.JTextField txtName = new javax.swing.JTextField();
            javax.swing.JTextField txtId = new javax.swing.JTextField();
            txtId.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "numbers and spaces");
            ((javax.swing.text.AbstractDocument) txtId.getDocument()).setDocumentFilter(AnyDeskId.digitsAndSpacesFilter());

            JPanel panel = new JPanel(new MigLayout("wrap,fillx,insets 12", "[fill,320!]", "[]8[]"));
            panel.add(new JLabel("Device Name"));
            panel.add(txtName);
            panel.add(new JLabel("AnyDesk ID"), "gapy 8");
            panel.add(txtId);

            int ok = JOptionPane.showConfirmDialog(parent, panel, "Add Device", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) {
                return null;
            }
            String name = Optional.ofNullable(txtName.getText()).orElse("").trim();
            String id = Optional.ofNullable(txtId.getText()).orElse("").trim();
            if (name.isEmpty() || !AnyDeskId.isValid(id)) {
                JOptionPane.showMessageDialog(parent, "Please enter a valid name and AnyDesk ID (numbers/spaces).", "Invalid input", JOptionPane.WARNING_MESSAGE);
                return null;
            }
            return new Result(name, id);
        }
    }
}

