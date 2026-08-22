package com.yapcore.gui.panels;

import com.yapcore.config.ServerConfig;
import com.yapcore.gui.theme.GuiTheme;
import com.yapcore.server.YaPcoreServer;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.GridLayout;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Central tuning hub — opens YaP + Paper + gameplay-knobs configs from one place.
 */
public final class TunePanel {

    private final YaPcoreServer server;
    private final JPanel root = new JPanel(new BorderLayout(8, 8));
    private final JTextArea hint = new JTextArea();

    public TunePanel(YaPcoreServer server) {
        this.server = server;
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(4, 4, 4, 4));

        hint.setEditable(false);
        hint.setOpaque(false);
        hint.setLineWrap(true);
        hint.setWrapStyleWord(true);
        hint.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        hint.setText(buildHint());
        hint.setRows(10);

        JPanel buttons = new JPanel(new GridLayout(0, 1, 4, 4));
        buttons.setOpaque(false);
        buttons.add(openBtn("YaP server.properties", hub().resolve("server.properties")));
        buttons.add(openBtn("Paper config folder", hub().resolve("paper")));
        buttons.add(openBtn("paper-global.yml", hub().resolve("paper").resolve("paper-global.yml")));
        buttons.add(openBtn("paper-world-defaults.yml",
                hub().resolve("paper").resolve("paper-world-defaults.yml")));
        buttons.add(openBtn("spigot.yml", hub().resolve("spigot.yml")));
        buttons.add(openBtn("Gameplay knobs (encyclopedia)", knobsYml()));
        buttons.add(folderBtn("Open config/ hub", hub()));
        buttons.add(folderBtn("Open plugins/", server.getRootDir().resolve("plugins")));

        JPanel body = new JPanel(new BorderLayout(6, 8));
        body.setOpaque(false);
        body.add(GuiTheme.sectionTitle("Tune — configs & knobs"), BorderLayout.NORTH);
        body.add(hint, BorderLayout.CENTER);
        body.add(buttons, BorderLayout.SOUTH);

        root.add(body, BorderLayout.NORTH);
    }

    public JPanel component() {
        return root;
    }

    public void refresh() {
        hint.setText(buildHint());
    }

    private Path hub() {
        return server.getRootDir().resolve("config");
    }

    private Path knobsYml() {
        return server.getRootDir().resolve("plugins")
                .resolve("YaPGameplayKnobs").resolve("knobs.yml");
    }

    private String buildHint() {
        ServerConfig cfg = server.getConfig();
        Path knobs = knobsYml();
        boolean knobsJar = Files.isRegularFile(
                server.getRootDir().resolve("plugins").resolve("yap-gameplay-knobs.jar"));
        return """
                Edit configs under config/ (Paper files are symlinks into paper-dir).

                YaP product:     config/server.properties
                Paper globals:  config/paper/paper-global.yml
                Paper worlds:   config/paper/paper-world-defaults.yml
                Spigot/Bukkit:  config/spigot.yml · config/bukkit.yml

                Gameplay encyclopedia:
                  jar:     plugins/yap-gameplay-knobs.jar  %s
                  config:  plugins/YaPGameplayKnobs/knobs.yml  %s

                game-authority=%s  paper-dir=%s

                Docs: docs/TUNE.md
                """.formatted(
                knobsJar ? "[installed]" : "[build knobs plugin]",
                Files.isRegularFile(knobs) ? "[present]" : "[created on first enable]",
                cfg.getGameAuthority(),
                cfg.getPaperDir());
    }

    private JButton openBtn(String label, Path path) {
        JButton b = new JButton(label);
        b.addActionListener(e -> openPath(path));
        return b;
    }

    private JButton folderBtn(String label, Path path) {
        JButton b = new JButton(label);
        b.addActionListener(e -> openPath(path));
        return b;
    }

    private void openPath(Path path) {
        try {
            Path p = path.toAbsolutePath().normalize();
            if (!Files.exists(p)) {
                JOptionPane.showMessageDialog(root,
                        "Missing: " + p + "\nStart the server once or build the knobs plugin.",
                        "Tune", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(p.toFile());
            } else {
                JOptionPane.showMessageDialog(root, p.toString(), "Path", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(root, ex.getMessage(), "Tune", JOptionPane.ERROR_MESSAGE);
        }
    }
}
