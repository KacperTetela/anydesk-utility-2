package raven.ui;

import com.formdev.flatlaf.FlatClientProperties;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import raven.anydesk.AnyDeskSettings;

public class SettingsView extends JPanel {

    public SettingsView() {
        init();
    }

    private void init() {
        putClientProperty(FlatClientProperties.STYLE, "background:null");
        setLayout(new MigLayout("fill,insets 22", "[grow,fill]", "[grow]"));

        JPanel card = new JPanel(new MigLayout("wrap,fillx,insets 22", "[fill,520::]", "[]10[]16[]"));
        card.putClientProperty(FlatClientProperties.STYLE, ""
                + "arc:20;"
                + "border:1,1,1,1,$Component.borderColor,,20;"
                + "background:$Panel.background");

        JLabel title = new JLabel("Application Settings");
        title.putClientProperty(FlatClientProperties.STYLE, "font:+6");

        JLabel subtitle = new JLabel("Settings will be added here (e.g. AnyDesk executable path).");
        subtitle.putClientProperty(FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");

        JCheckBox chkCleanup = new JCheckBox("Clean AnyDesk traces after connection (Ad/Limit reset)");
        chkCleanup.setSelected(AnyDeskSettings.isCleanupEnabled());
        chkCleanup.putClientProperty(FlatClientProperties.STYLE, ""
                + "margin:6,6,6,6;");
        chkCleanup.addActionListener(e -> AnyDeskSettings.setCleanupEnabled(chkCleanup.isSelected()));

        card.add(title);
        card.add(subtitle);
        card.add(chkCleanup, "gapy 8");

        add(card, "dock center");
    }
}

