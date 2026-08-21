package com.yapcore.gui.panels;

import com.yapcore.config.ServerConfig;
import com.yapcore.gui.theme.GuiTheme;
import com.yapcore.network.publicity.PublicEndpoint;
import com.yapcore.server.YaPcoreServer;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Consumer;

/**
 * External access setup — domain, public host, NAT ports, internet expose.
 */
public final class NetworkPanel {

    private final YaPcoreServer server;
    private final ConnectInfoPanel connectInfo;
    private final JPanel root = new JPanel(new BorderLayout(8, 8));
    private final JCheckBox exposeBox = new JCheckBox("Allow internet connections (bind 0.0.0.0)");
    private final JTextField domainField = new JTextField();
    private final JTextField publicHostField = new JTextField();
    private final JSpinner publicPortSpinner;
    private final JSpinner publicBedrockSpinner;
    private final JSpinner publicPackSpinner;
    private final JCheckBox srvBox = new JCheckBox("Include Minecraft DNS SRV record hint");
    private final JTextArea dnsHint = new JTextArea(3, 28);
    private Consumer<Void> onSaved = v -> {
    };

    public NetworkPanel(YaPcoreServer server) {
        this.server = server;
        this.connectInfo = new ConnectInfoPanel(server);
        ServerConfig cfg = server.getConfig();
        publicPortSpinner = new JSpinner(new SpinnerNumberModel(
                Math.max(0, cfg.getPublicPort()), 0, 65_535, 1));
        publicBedrockSpinner = new JSpinner(new SpinnerNumberModel(
                Math.max(0, cfg.getPublicBedrockPort()), 0, 65_535, 1));
        publicPackSpinner = new JSpinner(new SpinnerNumberModel(
                Math.max(0, cfg.getPublicPackPort()), 0, 65_535, 1));

        root.setOpaque(false);
        root.setBorder(new EmptyBorder(2, 2, 2, 2));

        JPanel stack = new JPanel(new BorderLayout(8, 8));
        stack.setOpaque(false);
        stack.add(connectInfo.component(), BorderLayout.NORTH);
        stack.add(buildForm(), BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(stack);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        root.add(scroll, BorderLayout.CENTER);

        loadFromConfig();
        refresh();
    }

    public JPanel component() {
        return root;
    }

    public void setOnSaved(Consumer<Void> onSaved) {
        this.onSaved = onSaved != null ? onSaved : v -> {
        };
    }

    public void refresh() {
        connectInfo.refresh();
        PublicEndpoint ep = new PublicEndpoint(server.getConfig());
        dnsHint.setText(ep.srvRecordExample());
    }

    private JPanel buildForm() {
        JPanel panel = GuiTheme.card();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(4, 4, 4, 4);
        panel.add(GuiTheme.sectionTitle("External access"), c);
        c.gridy++;
        JLabel blurb = new JLabel("<html><body style='width:240px'>Point a domain at this machine, "
                + "forward ports on your router, then save. Join addresses update above.</body></html>");
        blurb.setForeground(GuiTheme.MUTED);
        blurb.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        panel.add(blurb, c);
        c.gridy++;
        exposeBox.setOpaque(false);
        exposeBox.setForeground(GuiTheme.TEXT);
        panel.add(exposeBox, c);
        c.gridy++;
        panel.add(labeled("Domain (e.g. play.myserver.com)", domainField), c);
        c.gridy++;
        panel.add(labeled("Public host / IP (optional override)", publicHostField), c);
        c.gridy++;
        panel.add(labeled("Public Java TCP port (0 = listen port)", publicPortSpinner), c);
        c.gridy++;
        panel.add(labeled("Public Bedrock UDP port (0 = listen)", publicBedrockSpinner), c);
        c.gridy++;
        panel.add(labeled("Public pack HTTP port (0 = listen)", publicPackSpinner), c);
        c.gridy++;
        srvBox.setOpaque(false);
        srvBox.setForeground(GuiTheme.TEXT);
        panel.add(srvBox, c);
        c.gridy++;
        panel.add(labeled("DNS SRV example (paste into your DNS panel)", dnsHintArea()), c);
        c.gridy++;
        JButton save = new JButton("Save external access");
        GuiTheme.stylePrimary(save);
        save.addActionListener(e -> save());
        panel.add(save, c);
        return panel;
    }

    private JPanel dnsHintArea() {
        dnsHint.setEditable(false);
        dnsHint.setLineWrap(true);
        dnsHint.setWrapStyleWord(true);
        dnsHint.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        dnsHint.setForeground(GuiTheme.TEXT);
        dnsHint.setBackground(GuiTheme.BG);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(dnsHint, BorderLayout.CENTER);
        return wrap;
    }

    private void loadFromConfig() {
        ServerConfig cfg = server.getConfig();
        exposeBox.setSelected(cfg.isInternetExposed());
        domainField.setText(cfg.getServerDomain());
        publicHostField.setText(cfg.getPublicHost());
        publicPortSpinner.setValue(cfg.getPublicPort());
        publicBedrockSpinner.setValue(cfg.getPublicBedrockPort());
        publicPackSpinner.setValue(cfg.getPublicPackPort());
        srvBox.setSelected(cfg.isSrvEnabled());
    }

    private void save() {
        try {
            ServerConfig cfg = server.getConfig();
            cfg.setInternetExposed(exposeBox.isSelected());
            cfg.setServerDomain(domainField.getText());
            cfg.setPublicHost(publicHostField.getText());
            cfg.setPublicPort((Integer) publicPortSpinner.getValue());
            cfg.setPublicBedrockPort((Integer) publicBedrockSpinner.getValue());
            cfg.setPublicPackPort((Integer) publicPackSpinner.getValue());
            cfg.setSrvEnabled(srvBox.isSelected());
            PublicEndpoint ep = new PublicEndpoint(cfg);
            if (exposeBox.isSelected()) {
                ep.applyInternetBind();
            }
            String advertise = firstNonBlank(cfg.getPublicHost(), cfg.getServerDomain());
            if (advertise != null && cfg.getResourcePackPublicHost().isBlank()) {
                cfg.setResourcePackPublicHost(advertise);
            }
            cfg.save();
            server.getResourcePacks().setPublicHost(ep.publicHost());
            refresh();
            onSaved.accept(null);
            JOptionPane.showMessageDialog(root,
                    "External access saved.\n\nJava players join:\n" + ep.javaJoinAddress()
                            + "\n\nBedrock:\n" + ep.bedrockJoinAddress(),
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(root, e.getMessage(), "Save Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return null;
    }

    private static JPanel labeled(String label, java.awt.Component field) {
        JPanel col = new JPanel(new BorderLayout(0, 4));
        col.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setForeground(GuiTheme.MUTED);
        col.add(l, BorderLayout.NORTH);
        col.add(field, BorderLayout.CENTER);
        return col;
    }
}
