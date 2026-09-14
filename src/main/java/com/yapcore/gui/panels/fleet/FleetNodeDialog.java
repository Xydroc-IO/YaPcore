package com.yapcore.gui.panels.fleet;

import com.yapcore.fleet.model.FleetNode;
import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/** Register remote fleet agent node. */
public final class FleetNodeDialog extends JDialog {

    private final Path rootDir;
    private final JTextField id = new JTextField("node-2");
    private final JTextField baseUrl = new JTextField("http://192.168.1.20:9095");
    private final JTextField display = new JTextField("Remote");
    private final JTextField token = new JTextField();
    private boolean accepted;
    private FleetNode node;

    public FleetNodeDialog(Frame owner, Path rootDir) {
        super(owner, "Add fleet node", true);
        this.rootDir = rootDir;
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Node id"));
        form.add(id);
        form.add(new JLabel("Base URL"));
        form.add(baseUrl);
        form.add(new JLabel("Display name"));
        form.add(display);
        form.add(new JLabel("Bearer token"));
        form.add(token);
        JButton ok = new JButton("Save");
        ok.addActionListener(e -> save());
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

    private void save() {
        try {
            String nid = id.getText().trim();
            Path tokenFile = rootDir.resolve("fleet/agents/" + nid + ".token");
            Files.createDirectories(tokenFile.getParent());
            Files.writeString(tokenFile, token.getText().trim() + "\n");
            node = new FleetNode(nid, baseUrl.getText().trim(),
                    "fleet/agents/" + nid + ".token", display.getText().trim());
            accepted = true;
            setVisible(false);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Node", JOptionPane.ERROR_MESSAGE);
        }
    }

    public boolean accepted() {
        return accepted;
    }

    public FleetNode node() {
        return node;
    }
}
