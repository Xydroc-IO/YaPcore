package com.yapcore.gui.panels.fleet;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Network bootstrap wizard dialog. */
public final class FleetBootstrapDialog extends JDialog {

    private static final String MYSQL_URL =
            "jdbc:mysql://127.0.0.1:3306/yap_playerdata?useSSL=false&allowPublicKeyRetrieval=true";
    private static final String MARIA_URL = "jdbc:mariadb://127.0.0.1:3306/yap_playerdata";
    private static final String PG_URL = "jdbc:postgresql://127.0.0.1:5432/yap_playerdata";
    private static final String SQLITE_URL = "jdbc:sqlite:data/yap.db";

    private final JComboBox<String> engine = new JComboBox<>(new String[]{
            "MariaDB / MySQL", "PostgreSQL", "SQLite", "Skip DB setup"
    });
    private final JTextField jdbc = new JTextField(MYSQL_URL);
    private final JCheckBox survival = new JCheckBox("Create survival", true);
    private final JCheckBox velocity = new JCheckBox("Enable velocity/Link forwarding", true);
    private final JCheckBox start = new JCheckBox("Start auto instances now", false);
    private boolean accepted;

    public FleetBootstrapDialog(Frame owner) {
        super(owner, "Network bootstrap", true);
        JPanel form = new JPanel(new GridLayout(0, 1, 8, 8));
        form.add(new JLabel("Database (YaPDB — MariaDB/MySQL · PostgreSQL · SQLite)"));
        form.add(engine);
        form.add(new JLabel("JDBC URL (blank skips ensure-db) — see docs/data/YAPDB.md"));
        form.add(jdbc);
        engine.addActionListener(e -> applyEnginePreset());
        form.add(survival);
        form.add(velocity);
        form.add(start);
        JButton ok = new JButton("Run bootstrap");
        ok.addActionListener(e -> {
            accepted = true;
            setVisible(false);
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> setVisible(false));
        JPanel actions = new JPanel();
        actions.add(ok);
        actions.add(cancel);
        getContentPane().setLayout(new BorderLayout(8, 8));
        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(actions, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    private void applyEnginePreset() {
        String sel = String.valueOf(engine.getSelectedItem());
        if (sel.startsWith("PostgreSQL")) {
            jdbc.setText(PG_URL);
        } else if (sel.startsWith("SQLite")) {
            jdbc.setText(SQLITE_URL);
        } else if (sel.startsWith("Skip")) {
            jdbc.setText("");
        } else if (sel.startsWith("MariaDB")) {
            jdbc.setText(MYSQL_URL);
        } else {
            jdbc.setText(MARIA_URL);
        }
    }

    public boolean accepted() {
        return accepted;
    }

    public String jdbcUrl() {
        return jdbc.getText().trim();
    }

    public boolean createSurvival() {
        return survival.isSelected();
    }

    public boolean enableVelocity() {
        return velocity.isSelected();
    }

    public boolean startInstances() {
        return start.isSelected();
    }
}
