package com.yapcore.gui.panels;

import com.yapcore.config.ServerConfig;
import com.yapcore.gui.theme.GuiTheme;
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
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * nginx reverse-proxy setup + same-PC localhost assist.
 */
public final class NginxPanel {

    private final YaPcoreServer server;
    private final JPanel root = new JPanel(new BorderLayout());
    private final JCheckBox allowLocalBox = new JCheckBox("Allow same-PC clients (127.0.0.1 / localhost)");
    private final JSpinner publicPortSpinner;
    private final JSpinner packPortSpinner;
    private final JTextField domainField = new JTextField();
    private final JTextArea log = new JTextArea(10, 28);
    private Consumer<Void> onSaved = v -> {
    };

    public NginxPanel(YaPcoreServer server) {
        this.server = server;
        ServerConfig cfg = server.getConfig();
        publicPortSpinner = new JSpinner(new SpinnerNumberModel(
                Math.max(1, cfg.getNginxPublicPort()), 1, 65_535, 1));
        packPortSpinner = new JSpinner(new SpinnerNumberModel(
                Math.max(1, cfg.getNginxPackPort()), 1, 65_535, 1));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(2, 2, 2, 2));
        JScrollPane scroll = new JScrollPane(buildForm());
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        root.add(scroll, BorderLayout.CENTER);
        load();
    }

    public JPanel component() {
        return root;
    }

    public void setOnSaved(Consumer<Void> onSaved) {
        this.onSaved = onSaved != null ? onSaved : v -> {
        };
    }

    private JPanel buildForm() {
        JPanel panel = GuiTheme.card();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(4, 4, 4, 4);

        panel.add(GuiTheme.sectionTitle("Local + nginx"), c);
        c.gridy++;
        JLabel blurb = new JLabel("<html><body style='width:250px'>"
                + "<b>Same PC:</b> use <code>127.0.0.1:port</code> in Minecraft "
                + "(Connect tab). Do not use your public IP from this machine.<br><br>"
                + "<b>Domain:</b> <code>yapcoremc.yaplabs.us</code> — nginx stream for "
                + "game TCP/UDP; Cloudflare may orange-cloud packs only (DNS-only for Minecraft)."
                + "</body></html>");
        blurb.setForeground(GuiTheme.MUTED);
        blurb.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        panel.add(blurb, c);

        c.gridy++;
        allowLocalBox.setOpaque(false);
        allowLocalBox.setForeground(GuiTheme.TEXT);
        panel.add(allowLocalBox, c);
        c.gridy++;
        panel.add(labeled("nginx public game port (stream)", publicPortSpinner), c);
        c.gridy++;
        panel.add(labeled("nginx HTTP pack port", packPortSpinner), c);
        c.gridy++;
        panel.add(labeled("nginx server_name / domain", domainField), c);

        c.gridy++;
        JButton save = new JButton("Save");
        GuiTheme.stylePrimary(save);
        save.addActionListener(e -> save());
        panel.add(save, c);

        c.gridy++;
        JButton dry = new JButton("Generate configs (dry-run)");
        dry.addActionListener(e -> runScript("--dry-run"));
        panel.add(dry, c);

        c.gridy++;
        JButton install = new JButton("Install nginx configs (needs sudo)");
        GuiTheme.stylePrimary(install);
        install.addActionListener(e -> {
            int ok = JOptionPane.showConfirmDialog(root,
                    "This runs scripts/nginx-setup.sh with sudo.\nContinue?",
                    "nginx install", JOptionPane.OK_CANCEL_OPTION);
            if (ok == JOptionPane.OK_OPTION) {
                runScript("");
            }
        });
        panel.add(install, c);

        c.gridy++;
        JButton pkg = new JButton("Install nginx package + configs");
        pkg.addActionListener(e -> runScript("--install-pkg"));
        panel.add(pkg, c);

        c.gridy++;
        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        log.setForeground(GuiTheme.TEXT);
        log.setBackground(GuiTheme.BG);
        log.setLineWrap(true);
        panel.add(labeled("Script output", log), c);
        return panel;
    }

    private void load() {
        ServerConfig cfg = server.getConfig();
        allowLocalBox.setSelected(cfg.isAllowLocalhost());
        publicPortSpinner.setValue(cfg.getNginxPublicPort());
        packPortSpinner.setValue(cfg.getNginxPackPort());
        domainField.setText(cfg.getNginxDomain());
    }

    private void save() {
        try {
            ServerConfig cfg = server.getConfig();
            cfg.setAllowLocalhost(allowLocalBox.isSelected());
            cfg.setNginxPublicPort((Integer) publicPortSpinner.getValue());
            cfg.setNginxPackPort((Integer) packPortSpinner.getValue());
            cfg.setNginxDomain(domainField.getText());
            if (allowLocalBox.isSelected()) {
                cfg.setBindHost("0.0.0.0");
            }
            cfg.save();
            onSaved.accept(null);
            JOptionPane.showMessageDialog(root,
                    "Saved.\nSame-PC join: 127.0.0.1:" + cfg.getPort(),
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(root, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void runScript(String extraArgs) {
        saveQuiet();
        Path script = server.getRootDir().resolve("scripts").resolve("nginx-setup.sh");
        log.setText("Running " + script.getFileName() + " " + extraArgs + "…\n");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                ProcessBuilder pb;
                if (extraArgs.contains("install-pkg") || extraArgs.isEmpty()) {
                    // elevate when installing
                    pb = new ProcessBuilder("pkexec", "bash", script.toString());
                    if (!extraArgs.isBlank()) {
                        pb.command().add(extraArgs.trim());
                    }
                } else {
                    pb = new ProcessBuilder("bash", script.toString());
                    for (String a : extraArgs.trim().split("\\s+")) {
                        if (!a.isBlank()) {
                            pb.command().add(a);
                        }
                    }
                }
                pb.directory(server.getRootDir().toFile());
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String out = new String(p.getInputStream().readAllBytes());
                p.waitFor(120, TimeUnit.SECONDS);
                return out + "\nexit=" + p.exitValue();
            }

            @Override
            protected void done() {
                try {
                    log.setText(get());
                } catch (Exception e) {
                    // Fallback without pkexec
                    try {
                        ProcessBuilder pb = new ProcessBuilder("bash", script.toString(), "--dry-run");
                        pb.directory(server.getRootDir().toFile());
                        pb.redirectErrorStream(true);
                        Process p = pb.start();
                        String out = new String(p.getInputStream().readAllBytes());
                        log.setText("pkexec unavailable — dry-run instead:\n" + out
                                + "\n\nInstall manually:\n  sudo ./scripts/nginx-setup.sh");
                    } catch (Exception e2) {
                        log.setText("Failed: " + e.getMessage() + " / " + e2.getMessage());
                    }
                }
            }
        }.execute();
    }

    private void saveQuiet() {
        try {
            ServerConfig cfg = server.getConfig();
            cfg.setAllowLocalhost(allowLocalBox.isSelected());
            cfg.setNginxPublicPort((Integer) publicPortSpinner.getValue());
            cfg.setNginxPackPort((Integer) packPortSpinner.getValue());
            cfg.setNginxDomain(domainField.getText());
            cfg.save();
        } catch (Exception ignored) {
        }
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
