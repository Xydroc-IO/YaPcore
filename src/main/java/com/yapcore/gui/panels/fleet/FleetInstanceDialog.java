package com.yapcore.gui.panels.fleet;

import com.yapcore.gui.theme.GuiTheme;
import com.yapcore.server.YaPcoreServer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;

/** Create fleet instance — identity, port, settings, and catalog plugin picks. */
public final class FleetInstanceDialog extends JDialog {

    private final JTextField id = new JTextField("survival");
    private final JTextField display = new JTextField("Survival");
    private final JTextField port = new JTextField("");
    private final JSpinner maxPlayers = new JSpinner(new SpinnerNumberModel(20, 1, 10_000, 1));
    private final JSpinner ramMb = new JSpinner(new SpinnerNumberModel(2048, 512, 131_072, 512));
    private final JTextField motd = new JTextField("A YaPcore world");
    private final JCheckBox autoStart = new JCheckBox("Auto-start with chassis", false);
    private final FleetPluginPicker pluginPicker;
    private boolean accepted;

    public FleetInstanceDialog(Frame owner, YaPcoreServer server) {
        super(owner, "Add game server", true);
        this.pluginPicker = new FleetPluginPicker(server, true);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.setBorder(new EmptyBorder(8, 8, 8, 8));
        form.add(new JLabel("Id"));
        form.add(id);
        form.add(new JLabel("Display name"));
        form.add(display);
        form.add(new JLabel("Port (blank=auto)"));
        form.add(port);
        form.add(new JLabel("Max players"));
        form.add(maxPlayers);
        form.add(new JLabel("RAM max (MB)"));
        form.add(ramMb);
        form.add(new JLabel("MOTD"));
        form.add(motd);
        form.add(new JLabel(""));
        form.add(autoStart);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.setBorder(new EmptyBorder(0, 8, 8, 8));
        center.add(form, BorderLayout.NORTH);
        center.add(pluginPicker, BorderLayout.CENTER);

        JButton ok = new JButton("Create & install plugins");
        GuiTheme.stylePrimary(ok);
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
        getContentPane().add(center, BorderLayout.CENTER);
        getContentPane().add(actions, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(520, 560));
        pack();
        setLocationRelativeTo(owner);
    }

    public boolean accepted() {
        return accepted;
    }

    public String id() {
        return id.getText().trim();
    }

    public String displayName() {
        return display.getText().trim();
    }

    public Integer port() {
        String raw = port.getText().trim();
        if (raw.isEmpty()) {
            return null;
        }
        return Integer.parseInt(raw);
    }

    public boolean autoStart() {
        return autoStart.isSelected();
    }

    public int maxPlayers() {
        return ((Number) maxPlayers.getValue()).intValue();
    }

    public int ramMb() {
        return ((Number) ramMb.getValue()).intValue();
    }

    public String motd() {
        return motd.getText().trim();
    }

    public List<String> selectedPlugins() {
        return Collections.unmodifiableList(pluginPicker.selectedJars());
    }
}
