package com.yapcore.gui;

import com.yapcore.gui.theme.GuiTheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Standalone Test Lab — buttons for every suite + live console.
 * Launch: {@code scripts/test-gui.sh} or Control Panel → Test Lab.
 */
public final class TestLab extends JFrame {

    private final Path root;
    private final JTextArea console = new JTextArea();
    private final JLabel status = new JLabel("Idle");
    private final JButton stopBtn = new JButton("Stop");
    private final List<JButton> runButtons = new ArrayList<>();
    private final AtomicReference<Process> running = new AtomicReference<>();

    public TestLab(Path root) {
        super("YaPcore Test Lab");
        this.root = root.toAbsolutePath().normalize();
        GuiTheme.install();
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(960, 640));
        setPreferredSize(new Dimension(1100, 720));
        getContentPane().setBackground(GuiTheme.BG);

        JPanel rootPanel = new JPanel(new BorderLayout(12, 12));
        rootPanel.setBorder(new EmptyBorder(14, 14, 14, 14));
        rootPanel.setBackground(GuiTheme.BG);
        rootPanel.add(buildHeader(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildButtonPanel(), buildConsolePanel());
        split.setResizeWeight(0.32);
        split.setBorder(null);
        rootPanel.add(split, BorderLayout.CENTER);
        setContentPane(rootPanel);
        pack();
        setLocationRelativeTo(null);

        stopBtn.setEnabled(false);
        stopBtn.addActionListener(e -> stopRunning());
        append("YaPcore Test Lab\n");
        append("Project: " + this.root + "\n");
        append("Drop scripts into Konsole, or click a button here.\n\n");
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel brand = new JLabel("Test Lab");
        brand.setFont(new Font("Segoe UI", Font.BOLD, 24));
        brand.setForeground(GuiTheme.ACCENT);
        JLabel sub = new JLabel("Unit · Fray · JCStress · SpotBugs · Soak · Console");
        sub.setForeground(GuiTheme.MUTED);
        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.setOpaque(false);
        titles.add(brand);
        titles.add(sub);
        header.add(titles, BorderLayout.WEST);
        status.setForeground(GuiTheme.TEXT);
        status.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.add(status, BorderLayout.EAST);
        return header;
    }

    private JPanel buildButtonPanel() {
        JPanel card = GuiTheme.card();
        card.setLayout(new BorderLayout(8, 8));
        card.add(GuiTheme.sectionTitle("Suites"), BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 1, 8, 8));
        grid.setOpaque(false);
        grid.add(suite("Unit tests", "Fast JUnit (excludes Fray/soak)",
                List.of("test")));
        grid.add(suite("Fray", "Forced thread interleavings",
                List.of("frayTest")));
        grid.add(suite("JCStress", "AtomicLeaseManager stress",
                List.of("jcstress")));
        grid.add(suite("SpotBugs", "Static analysis (sync packages)",
                List.of("spotbugsMain")));
        grid.add(suite("All CI verify", "SpotBugs + unit + Fray",
                List.of("verifyConcurrency")));
        grid.add(suite("Boundary stress", "32 bots × 30s handoffs",
                List.of("boundaryStress",
                        "-Dyap.stress.bots=32",
                        "-Dyap.stress.seconds=30",
                        "-Dyap.stress.handoffs=999999")));
        grid.add(suite("Soak + JFR", "60s soak with flight recording",
                List.of("boundaryStress",
                        "-Dyap.stress.bots=32",
                        "-Dyap.stress.seconds=60",
                        "-Dyap.stress.handoffs=999999999",
                        "-Dyap.stress.jfr=" + root.resolve("logs/jfr/yapcore_soak.jfr"))));
        grid.add(suite("Endurance (2 min)", "LIVE/lease/heap report → logs/endurance/",
                List.of("endurance",
                        "-Dyap.endurance.bots=32",
                        "-Dyap.endurance.seconds=120",
                        "-Dyap.endurance.idleSeconds=15")));
        grid.add(suite("Endurance (day)", "24h readiness soak (long run)",
                List.of("endurance",
                        "-Dyap.endurance.bots=64",
                        "-Dyap.endurance.seconds=86400",
                        "-Dyap.endurance.idleSeconds=120",
                        "-Dyap.endurance.sampleMs=60000")));
        card.add(grid, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        south.setOpaque(false);
        GuiTheme.styleDanger(stopBtn);
        south.add(stopBtn);
        JButton clear = new JButton("Clear console");
        clear.addActionListener(e -> console.setText(""));
        south.add(clear);
        card.add(south, BorderLayout.SOUTH);
        return card;
    }

    private JPanel suite(String title, String hint, List<String> gradleArgs) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x2A, 0x33, 0x3C)),
                new EmptyBorder(8, 10, 8, 10)));
        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        JLabel t = new JLabel(title);
        t.setForeground(GuiTheme.TEXT);
        t.setFont(new Font("Segoe UI", Font.BOLD, 14));
        JLabel h = new JLabel(hint);
        h.setForeground(GuiTheme.MUTED);
        h.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        text.add(t);
        text.add(h);
        JButton run = new JButton("Run");
        GuiTheme.stylePrimary(run);
        run.addActionListener(e -> startGradle(title, gradleArgs));
        runButtons.add(run);
        row.add(text, BorderLayout.CENTER);
        row.add(run, BorderLayout.EAST);
        return row;
    }

    private JPanel buildConsolePanel() {
        JPanel panel = GuiTheme.card();
        panel.setLayout(new BorderLayout(8, 8));
        panel.add(GuiTheme.sectionTitle("Console"), BorderLayout.NORTH);
        console.setEditable(false);
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        console.setBackground(new Color(0x0D, 0x11, 0x17));
        console.setForeground(GuiTheme.TEXT);
        console.setCaretColor(GuiTheme.ACCENT);
        panel.add(new JScrollPane(console), BorderLayout.CENTER);

        JPanel cmdRow = new JPanel(new BorderLayout(8, 0));
        cmdRow.setOpaque(false);
        JTextField custom = new JTextField();
        custom.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        custom.setToolTipText("Extra gradle tasks/args, e.g. test --tests '*Lease*'");
        JButton go = new JButton("gradle");
        GuiTheme.stylePrimary(go);
        Runnable launch = () -> {
            String raw = custom.getText();
            if (raw == null || raw.isBlank()) {
                return;
            }
            List<String> args = new ArrayList<>();
            for (String part : raw.trim().split("\\s+")) {
                if (!part.isBlank()) {
                    args.add(part);
                }
            }
            startGradle("custom: " + raw, args);
        };
        go.addActionListener(e -> launch.run());
        custom.addActionListener(e -> launch.run());
        cmdRow.add(custom, BorderLayout.CENTER);
        cmdRow.add(go, BorderLayout.EAST);
        panel.add(cmdRow, BorderLayout.SOUTH);
        return panel;
    }

    private void startGradle(String label, List<String> gradleArgs) {
        if (running.get() != null) {
            JOptionPane.showMessageDialog(this, "A suite is already running. Stop it first.",
                    "Busy", JOptionPane.WARNING_MESSAGE);
            return;
        }
        File gradle = resolveGradle();
        if (gradle == null) {
            JOptionPane.showMessageDialog(this,
                    "Neither ./gradlew nor 'gradle' found on PATH.",
                    "Gradle missing", JOptionPane.ERROR_MESSAGE);
            return;
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(gradle.getAbsolutePath());
        cmd.addAll(gradleArgs);

        append("\n────────────────────────────────────────\n");
        append("▶ " + label + "\n");
        append("+ " + String.join(" ", cmd) + "\n\n");
        status.setText("Running: " + label);
        status.setForeground(GuiTheme.ACCENT);
        setBusy(true);

        if (label.contains("JFR")) {
            // ensure jfr dir exists
            root.resolve("logs/jfr").toFile().mkdirs();
        }

        new SwingWorker<Integer, String>() {
            @Override
            protected Integer doInBackground() throws Exception {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(root.toFile());
                pb.redirectErrorStream(true);
                pb.environment().put("YAPCORE_HOME", root.toString());
                Process proc = pb.start();
                running.set(proc);
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        publish(line + "\n");
                    }
                }
                return proc.waitFor();
            }

            @Override
            protected void process(List<String> chunks) {
                for (String c : chunks) {
                    append(c);
                }
            }

            @Override
            protected void done() {
                running.set(null);
                setBusy(false);
                try {
                    int code = get();
                    if (code == 0) {
                        append("\n✓ Finished OK\n");
                        status.setText("OK — " + label);
                        status.setForeground(GuiTheme.ACCENT);
                    } else {
                        append("\n✗ Failed (exit " + code + ")\n");
                        status.setText("Failed (" + code + ") — " + label);
                        status.setForeground(GuiTheme.DANGER);
                    }
                } catch (Exception e) {
                    append("\n✗ " + e.getMessage() + "\n");
                    status.setText("Error");
                    status.setForeground(GuiTheme.DANGER);
                }
            }
        }.execute();
    }

    private void stopRunning() {
        Process p = running.getAndSet(null);
        if (p != null) {
            p.destroyForcibly();
            append("\n■ Stopped by user\n");
            status.setText("Stopped");
            status.setForeground(GuiTheme.DANGER);
            setBusy(false);
        }
    }

    private void setBusy(boolean busy) {
        for (JButton b : runButtons) {
            b.setEnabled(!busy);
        }
        stopBtn.setEnabled(busy);
    }

    private File resolveGradle() {
        File wrapper = root.resolve("gradlew").toFile();
        if (wrapper.canExecute()) {
            return wrapper;
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                File g = new File(dir, "gradle");
                if (g.canExecute()) {
                    return g;
                }
            }
        }
        return null;
    }

    private void append(String text) {
        console.append(text);
        console.setCaretPosition(console.getDocument().getLength());
    }

    public static void open(Path root) {
        SwingUtilities.invokeLater(() -> {
            TestLab lab = new TestLab(root);
            lab.setVisible(true);
        });
    }

    /** Standalone entry: {@code java … com.yapcore.gui.TestLab} */
    public static void main(String[] args) {
        Path home = Path.of(System.getProperty("yapcore.home", ".")).toAbsolutePath().normalize();
        open(home);
    }
}
