package com.yapcore.gui.panels;

import com.yapcore.gui.theme.GuiTheme;
import com.yapcore.server.YaPcoreServer;
import com.yapcore.setup.SetupActions;
import com.yapcore.setup.SetupChecklist;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** First-boot checklist — same actions as web Configure → Setup. */
public final class SetupPanel {

    private final YaPcoreServer server;
    private final JPanel root = new JPanel(new BorderLayout());
    private final JLabel summary = new JLabel("—");
    private final JLabel osHint = new JLabel("—");
    private final JPanel stepsHost = new JPanel();
    private final JTextArea log = new JTextArea(10, 40);
    private final JCheckBox withLink = new JCheckBox("Production profile: also enable Link forwarding");

    public SetupPanel(YaPcoreServer server) {
        this.server = server;
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(2, 2, 2, 2));
        root.add(GuiTheme.verticalScroll(buildForm()), BorderLayout.CENTER);
        refresh();
    }

    public JPanel component() {
        return root;
    }

    public void refresh() {
        Map<String, Object> snap = SetupChecklist.snapshot(server.getRootDir());
        summary.setText(String.valueOf(snap.getOrDefault("summary", "—")));
        osHint.setText(String.valueOf(snap.getOrDefault("hint", "")));
        stepsHost.removeAll();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) snap.getOrDefault("steps", List.of());
        for (Map<String, Object> step : steps) {
            stepsHost.add(stepRow(step));
            stepsHost.add(Box.createVerticalStrut(4));
        }
        stepsHost.revalidate();
        stepsHost.repaint();
    }

    private JPanel buildForm() {
        JPanel panel = GuiTheme.card();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = GuiTheme.sectionTitle("Setup checklist");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        summary.setFont(new Font("Segoe UI", Font.BOLD, 13));
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(summary);
        osHint.setForeground(GuiTheme.MUTED);
        osHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(osHint);
        panel.add(Box.createVerticalStrut(8));

        stepsHost.setLayout(new BoxLayout(stepsHost, BoxLayout.Y_AXIS));
        stepsHost.setOpaque(false);
        stepsHost.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(stepsHost);
        panel.add(Box.createVerticalStrut(8));

        withLink.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(withLink);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.add(btn("Refresh", this::refresh));
        actions.add(btn("Accept EULA", () -> run("accept-eula")));
        actions.add(btn("Seed defaults", () -> run("seed-defaults")));
        actions.add(btn("Link forwarding ON", () -> run("link-forwarding", Map.of("enable", "true"))));
        actions.add(btn("Fetch Tebex", () -> run("fetch-tebex")));
        actions.add(btn("Fetch Grim", () -> run("fetch-grim")));
        actions.add(btn("Enable Grim", () -> run("enable-grim")));
        actions.add(btn("Production profile", () -> run("production-profile",
                Map.of("withLink", withLink.isSelected() ? "true" : "false"))));
        actions.add(btn("nginx dry-run", () -> run("nginx-dry-run")));
        actions.add(btn("Build YaP-Folia", () -> run("build-folia")));
        panel.add(actions);

        JLabel tip = GuiTheme.tip(
                "Database + fleet bootstrap: <b>Fleet</b> home. "
                        + "Full nginx install: <b>nginx</b> tab (sudo). "
                        + "Windows: Git Bash/WSL for full scripts; PowerShell covers nginx/DB/start.");
        tip.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(tip);

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(log);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setPreferredSize(new java.awt.Dimension(600, 180));
        panel.add(Box.createVerticalStrut(8));
        panel.add(scroll);
        return panel;
    }

    private JPanel stepRow(Map<String, Object> step) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        boolean ready = Boolean.TRUE.equals(step.get("ready"));
        JLabel mark = new JLabel(ready ? "✓" : "○");
        mark.setForeground(ready ? GuiTheme.ACCENT : GuiTheme.MUTED);
        String text = String.valueOf(step.get("label")) + " — " + step.getOrDefault("detail", "");
        if (step.get("deeplinkHint") != null) {
            text += " (" + step.get("deeplinkHint") + ")";
        }
        JLabel label = new JLabel(text);
        label.setForeground(GuiTheme.TEXT);
        row.add(mark, BorderLayout.WEST);
        row.add(label, BorderLayout.CENTER);
        return row;
    }

    private JButton btn(String text, Runnable action) {
        JButton b = new JButton(text);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void run(String action) {
        run(action, Map.of());
    }

    private void run(String action, Map<String, String> body) {
        log.setText("Running " + action + "…\n");
        new SwingWorker<Map<String, Object>, Void>() {
            @Override
            protected Map<String, Object> doInBackground() throws Exception {
                return SetupActions.run(server.getRootDir(), action, new LinkedHashMap<>(body));
            }

            @Override
            protected void done() {
                try {
                    Map<String, Object> r = get();
                    StringBuilder sb = new StringBuilder();
                    sb.append(Boolean.TRUE.equals(r.get("ok")) ? "OK" : "FAILED");
                    if (r.get("result") != null) {
                        sb.append(" — ").append(r.get("result"));
                    }
                    if (r.get("error") != null) {
                        sb.append(" — ").append(r.get("error"));
                    }
                    sb.append('\n');
                    if (r.get("output") != null) {
                        sb.append(r.get("output"));
                    }
                    if (r.get("hint") != null) {
                        sb.append('\n').append(r.get("hint"));
                    }
                    log.setText(sb.toString());
                } catch (Exception e) {
                    log.setText("Error: " + e.getMessage());
                }
                refresh();
            }
        }.execute();
    }
}
