package com.yapcore.packs;

import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extra resource packs beyond Paper's single login-prompt slot.
 * <p>
 * The primary pack is offered by Paper {@code server.properties} (Yes/No dialog).
 * Play-phase {@link Player#addResourcePack} is <b>off by default</b> because Via's
 * remapper was closing the connection ("Connection reset by peer") when packs were
 * pushed on join. Use {@code /yappacks push} or set {@code push-extras-on-join: true}
 * only after verifying your client path.
 */
public final class PacksPlugin extends JavaPlugin implements Listener {

    private static final Pattern PACK_OBJ = Pattern.compile(
            "\\{\\s*\"file\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"url\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"sha1\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"uuid\"\\s*:\\s*\"([^\"]+)\"\\s*}",
            Pattern.DOTALL);

    private volatile Manifest manifest = Manifest.empty();
    private boolean pushExtrasOnJoin;
    private int pushDelayTicks;
    /** Skip the first manifest pack (already offered via server.properties). */
    private boolean skipPrimary;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadLocalConfig();
        reloadManifest();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("YaPPacks online — " + manifest.packs().size()
                + " pack(s) in manifest, forced=" + manifest.forced()
                + ", push-extras-on-join=" + pushExtrasOnJoin
                + " (primary pack uses Paper login prompt)");
    }

    private void reloadLocalConfig() {
        reloadConfig();
        pushExtrasOnJoin = getConfig().getBoolean("push-extras-on-join", false);
        pushDelayTicks = Math.max(20, getConfig().getInt("push-delay-ticks", 60));
        skipPrimary = getConfig().getBoolean("skip-primary", true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!pushExtrasOnJoin) {
            return;
        }
        Player player = event.getPlayer();
        YapSched.entityLater(this, player, () -> pushPacks(player, true), pushDelayTicks);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        getLogger().info("Pack status " + event.getPlayer().getName() + ": " + event.getStatus());
        if (!manifest.forced()) {
            return;
        }
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        if (status == PlayerResourcePackStatusEvent.Status.DECLINED
                || status == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD
                || status == PlayerResourcePackStatusEvent.Status.INVALID_URL) {
            event.getPlayer().kick(Component.text("This server requires its resource packs."));
        }
    }

    private void pushPacks(Player player, boolean extrasOnly) {
        reloadManifestQuiet();
        Manifest m = this.manifest;
        if (!m.enabled() || m.packs().isEmpty()) {
            return;
        }
        List<PackEntry> toSend = m.packs();
        if (extrasOnly && skipPrimary && toSend.size() > 1) {
            toSend = toSend.subList(1, toSend.size());
        } else if (extrasOnly && skipPrimary) {
            // Only primary in manifest — Paper already offered it
            return;
        }
        String prompt = resolvePrompt(m.prompt());
        // Never force from play-phase unless manifest says so (login prompt handles required)
        boolean forced = m.forced();
        for (PackEntry pack : toSend) {
            try {
                byte[] hash = HexFormat.of().parseHex(pack.sha1());
                player.addResourcePack(pack.uuid(), pack.url(), hash, prompt, forced);
                getLogger().info("Sent pack " + pack.file() + " → " + player.getName());
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to send pack " + pack.file() + " to "
                        + player.getName() + ": " + e.getMessage());
            }
        }
    }

    private static String resolvePrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "This server offers additional resource packs. Click Yes to download.";
        }
        if (prompt.trim().startsWith("{")) {
            return "This server offers resource packs. Click Yes to download, or No to skip.";
        }
        return prompt;
    }

    public void reloadManifest() {
        Path file = getDataFolder().toPath().resolve("active.json");
        try {
            Files.createDirectories(file.getParent());
            if (!Files.isRegularFile(file)) {
                Files.writeString(file, """
                        {
                          "enabled": true,
                          "forced": false,
                          "prompt": "",
                          "packs": []
                        }
                        """, StandardCharsets.UTF_8);
                this.manifest = Manifest.empty();
                getLogger().info("No active.json yet — YaPcore writes it when packs are set active");
                return;
            }
            this.manifest = Manifest.parse(Files.readString(file, StandardCharsets.UTF_8));
            getLogger().info("Loaded " + manifest.packs().size() + " pack(s) from " + file);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not read " + file, e);
            this.manifest = Manifest.empty();
        }
    }

    private void reloadManifestQuiet() {
        try {
            Path file = getDataFolder().toPath().resolve("active.json");
            if (Files.isRegularFile(file)) {
                this.manifest = Manifest.parse(Files.readString(file, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("yappacks")) {
            return false;
        }
        if (args.length == 0 || "list".equalsIgnoreCase(args[0])) {
            sender.sendMessage("YaPPacks enabled=" + manifest.enabled()
                    + " forced=" + manifest.forced()
                    + " count=" + manifest.packs().size()
                    + " push-extras-on-join=" + pushExtrasOnJoin);
            for (PackEntry p : manifest.packs()) {
                sender.sendMessage(" - " + p.file() + " → " + p.url());
            }
            sender.sendMessage("Primary pack is offered at login by Paper (Yes/No).");
            return true;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            reloadLocalConfig();
            reloadManifest();
            sender.sendMessage("Reloaded (" + manifest.packs().size() + " packs).");
            return true;
        }
        if ("push".equalsIgnoreCase(args[0]) && sender instanceof Player player) {
            // Manual: send everything including primary (for testing)
            pushPacks(player, false);
            sender.sendMessage("Pushed packs to you (may disconnect on some Via clients).");
            return true;
        }
        sender.sendMessage("Usage: /yappacks [list|reload|push]");
        return true;
    }

    private record PackEntry(String file, String url, String sha1, UUID uuid) {
    }

    private record Manifest(boolean enabled, boolean forced, String prompt, List<PackEntry> packs) {
        static Manifest empty() {
            return new Manifest(true, false, "", List.of());
        }

        static Manifest parse(String raw) {
            boolean enabled = !raw.contains("\"enabled\": false") && !raw.contains("\"enabled\":false");
            boolean forced = raw.contains("\"forced\": true") || raw.contains("\"forced\":true");
            String prompt = "";
            Matcher pm = Pattern.compile("\"prompt\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(raw);
            if (pm.find()) {
                prompt = pm.group(1).replace("\\\"", "\"").replace("\\n", "\n");
            }
            List<PackEntry> packs = new ArrayList<>();
            Matcher m = PACK_OBJ.matcher(raw);
            while (m.find()) {
                packs.add(new PackEntry(
                        m.group(1),
                        m.group(2),
                        m.group(3).toLowerCase(Locale.ROOT),
                        UUID.fromString(m.group(4))));
            }
            return new Manifest(enabled, forced, prompt, List.copyOf(packs));
        }
    }
}
