package raven;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.function.BiConsumer;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.BorderFactory;
import javax.swing.UIManager;
import net.miginfocom.swing.MigLayout;
import raven.ui.AddressBookView;
import raven.ui.AnyDeskConnectView;
import raven.ui.SettingsView;
import raven.ui.SidebarButton;
import raven.ui.SidebarNav;

public class AnyDeskManagerFrame extends JFrame {

    private static final String CARD_CONNECT = "connect";
    private static final String CARD_ADDRESS_BOOK = "addressBook";
    private static final String CARD_SETTINGS = "settings";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private SidebarNav sidebar;
    private AnyDeskConnectView connectView;
    private JLabel statusLabel;
    private BiConsumer<String, Boolean> statusSink;

    public AnyDeskManagerFrame() {
        initFrame();
        initUi();
        showCard(CARD_CONNECT);
    }

    private void initFrame() {
        setTitle("AnyDesk Connection Manager");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // Taller default window for history table visibility
        setSize(new Dimension(860, 700));
        setMinimumSize(new Dimension(780, 600));
        setLocationRelativeTo(null);
        getRootPane().putClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT, true);
    }

    private void initUi() {
        // CRITICAL: strict BorderLayout root: WEST sidebar, CENTER cards, SOUTH status bar
        JPanel root = new JPanel(new BorderLayout());
        root.putClientProperty(FlatClientProperties.STYLE, "background:$Panel.background");

        statusSink = (msg, err) -> {
            if (statusLabel == null) {
                return;
            }
            javax.swing.SwingUtilities.invokeLater(() -> {
                statusLabel.setText(msg != null ? msg : "");
                if (Boolean.TRUE.equals(err)) {
                    statusLabel.setForeground(Color.decode("#ef4444"));
                } else {
                    statusLabel.setForeground(UIManager.getColor("Label.foreground"));
                }
            });
        };

        root.add(createSidebar(), BorderLayout.WEST);
        root.add(createContent(), BorderLayout.CENTER);
        root.add(createStatusBar(), BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel createSidebar() {
        sidebar = new SidebarNav(this::showCard);

        SidebarButton btnConnect = new SidebarButton("Connect", iconOrFallback("flatlaf.icons.OutlineConnect", "raven/resources/nav/connect.svg", 0.78f));
        SidebarButton btnAddressBook = new SidebarButton("Address Book", iconOrFallback("flatlaf.icons.OutlineContact", "raven/resources/nav/contact.svg", 0.78f));
        SidebarButton btnSettings = new SidebarButton("Settings", iconOrFallback("flatlaf.icons.OutlineSettings", "raven/resources/nav/settings.svg", 0.78f));

        sidebar.setItems(btnConnect, btnAddressBook, btnSettings);
        return sidebar;
    }

    private JPanel createContent() {
        // Extra top inset so window controls never overlap content when FULL_WINDOW_CONTENT is enabled.
        JPanel content = new JPanel(new MigLayout("fill,insets 40 28 28 28", "[grow,fill]", "[grow,fill]"));
        content.putClientProperty(FlatClientProperties.STYLE, "background:$Panel.background");
        content.add(createCards());
        return content;
    }

    private JPanel createCards() {
        cards.putClientProperty(FlatClientProperties.STYLE, "background:$Panel.background");
        connectView = new AnyDeskConnectView(statusSink);
        AddressBookView addressBookView = new AddressBookView(id -> {
            showCard(CARD_CONNECT);
            connectView.startConnection(id);
        });

        cards.add(connectView, CARD_CONNECT);
        cards.add(addressBookView, CARD_ADDRESS_BOOK);
        cards.add(new SettingsView(), CARD_SETTINGS);
        return cards;
    }

    private JPanel createStatusBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        bar.putClientProperty(FlatClientProperties.STYLE, "background:$Panel.background");
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Component.borderColor")));
        statusLabel = new JLabel("Ready.");
        statusLabel.putClientProperty(FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");
        bar.add(statusLabel);
        return bar;
    }

    private void showCard(String name) {
        FlatAnimatedLafChange.showSnapshot();
        cardLayout.show(cards, name);
        if (sidebar != null) {
            sidebar.setActive(CARD_CONNECT, CARD_ADDRESS_BOOK, CARD_SETTINGS, name);
        }
        FlatAnimatedLafChange.hideSnapshotWithAnimation();
    }

    private static javax.swing.Icon iconOrFallback(String uiKey, String fallbackSvgPath, float fallbackScale) {
        javax.swing.Icon icon = UIManager.getIcon(uiKey);
        if (icon != null) {
            return icon;
        }
        return new FlatSVGIcon(fallbackSvgPath, fallbackScale);
    }

    public static void main(String[] args) {
        // Global LAF init (pick one; easy to swap later)
        FlatDarkLaf.setup();
        UIManager.put("defaultFont", new Font("Dialog", Font.PLAIN, 13));

        // Make focus + selection feel more modern
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.arc", 12);
        UIManager.put("Button.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.trackArc", 999);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ProgressBar.arc", 999);
        Color anyDeskRed = Color.decode(FlatLaf.isLafDark() ? "#D92B34" : "#EF443B");
        UIManager.put("App.accentColor", anyDeskRed);
        UIManager.put("Component.focusColor", anyDeskRed);
        UIManager.put("TextComponent.focusColor", anyDeskRed);
        UIManager.put("Component.innerFocusWidth", 1);

        EventQueue.invokeLater(() -> new AnyDeskManagerFrame().setVisible(true));
    }
}

