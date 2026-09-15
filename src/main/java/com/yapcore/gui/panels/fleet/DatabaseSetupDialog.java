package com.yapcore.gui.panels.fleet;

import com.yapcore.fleet.ops.DatabaseSetup;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

/**
 * Standalone database wizard: pick MariaDB / Postgres / SQLite, start Docker if needed,
 * write JDBC, sync fleet catalog.
 */
public final class DatabaseSetupDialog extends JDialog {

    private final Path rootDir;
    private final JComboBox<String> engine = new JComboBox<>(new String[]{
            "MariaDB / MySQL (Docker)", "PostgreSQL (Docker)", "SQLite (single-node, no Docker)"
    });
    private final JTextField serverId = new JTextField("lobby");
    private final JCheckBox syncFleet = new JCheckBox("Sync JDBC/kits/items to fleet instances", true);
    private final JTextArea log = new JTextArea(12, 56);
    private final JLabel statusLine = new JLabel(" ");
    private boolean accepted;

    public DatabaseSetupDialog(Frame owner, Path rootDir) {
        super(owner, "Database setup (YaPDB)", true);
        this.rootDir = rootDir;
        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);

        JPanel form = new JPanel(new GridLayout(0, 1, 6, 6));
        form.add(new JLabel("Engine — MariaDB/Postgres start packaged Docker; SQLite uses data/yap.db"));
        form.add(engine);
        form.add(new JLabel("server-id hint (playerdata)"));
        form.add(serverId);
        form.add(syncFleet);

        JPanel actions = new JPanel();
        JButton refresh = new JButton("Refresh status");
        refresh.addActionListener(e -> refreshStatus());
        JButton startDocker = new JButton("Start Docker only");
        startDocker.addActionListener(e -> runDocker(true));
        JButton stopDocker = new JButton("Stop Docker");
        stopDocker.addActionListener(e -> runDocker(false));
        JButton ensure = new JButton("Set up database");
        ensure.addActionListener(e -> runEnsure());
        JButton close = new JButton("Close");
        close.addActionListener(e -> {
            accepted = true;
            setVisible(false);
        });
        actions.add(refresh);
        actions.add(startDocker);
        actions.add(stopDocker);
        actions.add(ensure);
        actions.add(close);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.add(form, BorderLayout.NORTH);
        center.add(statusLine, BorderLayout.CENTER);
        center.add(new JScrollPane(log), BorderLayout.SOUTH);

        getContentPane().setLayout(new BorderLayout(8, 8));
        getContentPane().add(center, BorderLayout.CENTER);
        getContentPane().add(actions, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(640, 480));
        pack();
        setLocationRelativeTo(owner);
        refreshStatus();
    }

    public boolean accepted() {
        return accepted;
    }

    private DatabaseSetup.Engine selectedEngine() {
        String sel = String.valueOf(engine.getSelectedItem());
        if (sel.startsWith("PostgreSQL")) {
            return DatabaseSetup.Engine.POSTGRES;
        }
        if (sel.startsWith("SQLite")) {
            return DatabaseSetup.Engine.SQLITE;
        }
        return DatabaseSetup.Engine.MYSQL;
    }

    private void refreshStatus() {
        new SwingWorker<Map<String, Object>, Void>() {
            @Override
            protected Map<String, Object> doInBackground() {
                return DatabaseSetup.status(rootDir);
            }

            @Override
            protected void done() {
                try {
                    Map<String, Object> s = get();
                    Object yapdb = s.get("yapdb");
                    String jdbc = "";
                    if (yapdb instanceof Map<?, ?> y && y.get("jdbcUrl") != null) {
                        jdbc = String.valueOf(y.get("jdbcUrl"));
                    }
                    statusLine.setText("Docker=" + s.get("dockerRunning")
                            + "  bash=" + s.get("bashAvailable")
                            + "  current=" + (jdbc.isBlank() ? "(none)" : jdbc));
                    appendLog("Status refreshed.\n" + summarize(s));
                } catch (Exception e) {
                    appendLog("Status failed: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void runEnsure() {
        DatabaseSetup.Engine eng = selectedEngine();
        String sid = serverId.getText().trim();
        boolean sync = syncFleet.isSelected();
        appendLog("Ensuring " + eng + " …");
        new SwingWorker<Map<String, Object>, Void>() {
            @Override
            protected Map<String, Object> doInBackground() throws Exception {
                return DatabaseSetup.ensure(rootDir, eng, sid, null, sync);
            }

            @Override
            protected void done() {
                try {
                    Map<String, Object> r = get();
                    appendLog(String.valueOf(r.getOrDefault("ensure", r)));
                    Object yapdb = r.get("yapdb");
                    if (yapdb instanceof Map<?, ?> y && y.get("jdbcUrl") != null) {
                        statusLine.setText("Ready: " + y.get("jdbcUrl"));
                    }
                    if (!Boolean.TRUE.equals(r.get("ok"))) {
                        appendLog("ERROR: " + r.getOrDefault("error", "ensure failed"));
                    }
                } catch (Exception e) {
                    appendLog("Ensure failed: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void runDocker(boolean start) {
        DatabaseSetup.Engine eng = selectedEngine();
        appendLog((start ? "Starting" : "Stopping") + " Docker for " + eng + " …");
        new SwingWorker<Map<String, Object>, Void>() {
            @Override
            protected Map<String, Object> doInBackground() throws Exception {
                return start
                        ? DatabaseSetup.startDocker(rootDir, eng)
                        : DatabaseSetup.stopDocker(rootDir, eng);
            }

            @Override
            protected void done() {
                try {
                    appendLog(String.valueOf(get()));
                    refreshStatus();
                } catch (Exception e) {
                    appendLog("Docker action failed: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void appendLog(String text) {
        log.append(text);
        if (!text.endsWith("\n")) {
            log.append("\n");
        }
        log.setCaretPosition(log.getDocument().getLength());
    }

    private static String summarize(Map<String, Object> s) {
        StringBuilder b = new StringBuilder();
        b.append("dockerInstalled=").append(s.get("dockerInstalled"))
                .append(" dockerRunning=").append(s.get("dockerRunning")).append('\n');
        Object maria = s.get("mariadb");
        if (maria instanceof Map<?, ?> m) {
            b.append("mariadb present=").append(m.get("present"))
                    .append(" health=").append(m.get("health")).append('\n');
        }
        Object pg = s.get("postgres");
        if (pg instanceof Map<?, ?> m) {
            b.append("postgres present=").append(m.get("present"))
                    .append(" health=").append(m.get("health")).append('\n');
        }
        return b.toString();
    }
}
