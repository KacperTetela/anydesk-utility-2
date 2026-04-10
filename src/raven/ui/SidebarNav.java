package raven.ui;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Dimension;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.miginfocom.swing.MigLayout;

public class SidebarNav extends JPanel {

    public interface NavHandler {
        void onSelect(String card);
    }

    private static final int WIDTH_EXPANDED = 200;

    private final NavHandler handler;

    private final ButtonGroup navGroup = new ButtonGroup();

    private SidebarButton btnConnect;
    private SidebarButton btnAddressBook;
    private SidebarButton btnSettings;

    public SidebarNav(NavHandler handler) {
        this.handler = handler;
        init();
    }

    public SidebarButton getBtnConnect() {
        return btnConnect;
    }

    public SidebarButton getBtnAddressBook() {
        return btnAddressBook;
    }

    public SidebarButton getBtnSettings() {
        return btnSettings;
    }

    public void setActive(String cardConnect, String cardAddressBook, String cardSettings, String activeCard) {
        btnConnect.setActive(cardConnect.equals(activeCard));
        btnAddressBook.setActive(cardAddressBook.equals(activeCard));
        btnSettings.setActive(cardSettings.equals(activeCard));
    }

    private void init() {
        putClientProperty(FlatClientProperties.STYLE, ""
                + "background:darken($Panel.background,6%);"
                + "border:0,0,0,1,$Component.borderColor");

        // Detach from frame edge a bit (requested EmptyBorder)
        setBorder(new EmptyBorder(0, 5, 0, 10));
        // No hamburger toggle: keep a fixed sidebar and start items at top with a comfortable margin.
        setLayout(new MigLayout("wrap,fillx,insets 20 0 12 0", "[fill]", "[]12[]12[]push"));
        setPreferredSize(new Dimension(WIDTH_EXPANDED, 10));
    }

    public void setItems(SidebarButton connect, SidebarButton addressBook, SidebarButton settings) {
        this.btnConnect = connect;
        this.btnAddressBook = addressBook;
        this.btnSettings = settings;

        // Grouping: only one selected at a time
        navGroup.add(btnConnect);
        navGroup.add(btnAddressBook);
        navGroup.add(btnSettings);

        btnConnect.addActionListener(e -> handler.onSelect("connect"));
        btnAddressBook.addActionListener(e -> handler.onSelect("addressBook"));
        btnSettings.addActionListener(e -> handler.onSelect("settings"));

        add(btnConnect, "h 42!");
        add(btnAddressBook, "h 42!");
        add(btnSettings, "h 42!");
    }
}

