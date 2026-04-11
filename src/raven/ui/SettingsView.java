package raven.ui;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Cursor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.miginfocom.swing.MigLayout;
import raven.anydesk.AnyDeskSettings;
import raven.config.ConfigManager;

public class SettingsView extends JPanel {

    private final Runnable onAddressBookPathChanged;

    private JTextField txtAbPath;

    public SettingsView(Runnable onAddressBookPathChanged) {
        this.onAddressBookPathChanged = onAddressBookPathChanged;
        init();
    }

    private void init() {
        putClientProperty(FlatClientProperties.STYLE, "background:null");
        setLayout(new MigLayout("fill,insets 22", "[grow,fill]", "[grow]"));

        JPanel card = new JPanel(new MigLayout("wrap,fillx,insets 22", "[fill,520::]", "[]10[]16[]30[]10[]16[]10[]"));
        card.putClientProperty(FlatClientProperties.STYLE, ""
                + "arc:20;"
                + "border:1,1,1,1,$Component.borderColor,,20;"
                + "background:$Panel.background");

        // ── General settings ────────────────────────────────────────────────
        JLabel title = new JLabel("Application Settings");
        title.putClientProperty(FlatClientProperties.STYLE, "font:+6");

        JLabel subtitle = new JLabel("Configure general behaviour and address book storage.");
        subtitle.putClientProperty(FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");

        JCheckBox chkCleanup = new JCheckBox("Clean AnyDesk traces after connection (Ad/Limit reset)");
        chkCleanup.setSelected(AnyDeskSettings.isCleanupEnabled());
        chkCleanup.putClientProperty(FlatClientProperties.STYLE, "margin:6,6,6,6;");
        chkCleanup.addActionListener(e -> AnyDeskSettings.setCleanupEnabled(chkCleanup.isSelected()));

        // ── Address Book Storage ─────────────────────────────────────────────
        JLabel abTitle = new JLabel("Address Book Storage");
        abTitle.putClientProperty(FlatClientProperties.STYLE, "font:+2;fontStyle:bold");

        JLabel abSubtitle = new JLabel("Choose where your contacts file is stored on disk.");
        abSubtitle.putClientProperty(FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");

        String currentPath = ConfigManager.getAddressBookPath();
        txtAbPath = new JTextField(currentPath != null ? currentPath : "");
        txtAbPath.setEditable(false);
        txtAbPath.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "(not set — use Browse or Create Default)");
        txtAbPath.putClientProperty(FlatClientProperties.STYLE, "arc:10;");

        JButton cmdBrowse = new JButton("Browse…");
        cmdBrowse.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cmdBrowse.putClientProperty(FlatClientProperties.STYLE, "arc:10;margin:6,14,6,14;");
        cmdBrowse.addActionListener(e -> onBrowse());

        JButton cmdCreateDefault = new JButton("Create Default");
        cmdCreateDefault.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cmdCreateDefault.putClientProperty(FlatClientProperties.STYLE, "arc:10;margin:6,14,6,14;");
        cmdCreateDefault.addActionListener(e -> onCreateDefault());

        // Row: path field + Browse side by side
        JPanel pathRow = new JPanel(new MigLayout("insets 0,fillx", "[grow,fill]8[]", "[]"));
        pathRow.putClientProperty(FlatClientProperties.STYLE, "background:null");
        pathRow.add(txtAbPath, "growx");
        pathRow.add(cmdBrowse);

        // ── Assembly ─────────────────────────────────────────────────────────
        card.add(title);
        card.add(subtitle);
        card.add(chkCleanup, "gapy 8");

        card.add(abTitle, "gapy 8");
        card.add(abSubtitle);
        card.add(pathRow, "growx");
        card.add(cmdCreateDefault);

        add(card, "dock center");
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    private void onBrowse() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select or create address book file");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(true);
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("JSON files (*.json)", "json"));

        // Pre-select current file if one is configured
        String current = ConfigManager.getAddressBookPath();
        if (current != null && !current.isBlank()) {
            chooser.setSelectedFile(new java.io.File(current));
        } else {
            chooser.setCurrentDirectory(ConfigManager.getDefaultAddressBookPath().getParent().toFile());
        }

        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        java.io.File selected = chooser.getSelectedFile();
        // Ensure .json extension when user omits it
        if (!selected.getName().contains(".")) {
            selected = new java.io.File(selected.getAbsolutePath() + ".json");
        }

        applyNewPath(selected.getAbsolutePath());
    }

    private void onCreateDefault() {
        Path defaultPath = ConfigManager.getDefaultAddressBookPath();
        try {
            Files.createDirectories(defaultPath.getParent());
            if (!Files.exists(defaultPath)) {
                Files.writeString(defaultPath, "[]\n",
                        java.nio.charset.StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE);
            }
            applyNewPath(defaultPath.toString());
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
            JOptionPane.showMessageDialog(this,
                    "Could not create default file:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Saves the new path, refreshes the displayed path, and notifies the
     * Address Book view so it reloads immediately.
     */
    private void applyNewPath(String path) {
        ConfigManager.setAddressBookPath(path);
        txtAbPath.setText(path);
        if (onAddressBookPathChanged != null) {
            onAddressBookPathChanged.run();
        }
    }
}
