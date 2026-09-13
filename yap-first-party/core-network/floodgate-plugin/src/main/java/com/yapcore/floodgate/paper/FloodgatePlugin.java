package com.yapcore.floodgate.paper;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import com.yapcore.messages.YapMessages;
import org.geysermc.floodgate.api.InstanceHolder;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Native Floodgate-class identity for Paper backends behind Velocity+Geyser / Link-native.
 * No Floodgate jar required — copy {@code key.pem} from the proxy Floodgate plugin.
 *
 * <p>Registers a minimal {@link FloodgateApi} so GrimAC {@code GeyserUtil.isBedrockPlayer}
 * exempts Bedrock from Reach/Hitboxes cancels (BE-COMBAT-01).
 */
public final class FloodgatePlugin extends JavaPlugin implements Listener {

    private FloodgateRuntime runtime;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Path key = getDataFolder().toPath().resolve(getConfig().getString("key-file", "key.pem"));
        runtime = new FloodgateRuntime(getLogger(), key);
        // Grim looks up org.geysermc.floodgate.api.FloodgateApi — register before any join.
        InstanceHolder.setApi(new YapFloodgateApi(uuid -> runtime != null && runtime.isBedrock(uuid)));
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("floodgate (YaP) online — FloodgateApi registered for Grim Bedrock exempt"
                + " (plugin name=floodgate for Grim softdepend)");
    }

    @Override
    public void onDisable() {
        InstanceHolder.setApi(null);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        String host = event.getHostname();
        FloodgateRuntime.PlayerInfo info = runtime.remember(uuid, name, host);
        if (info != null) {
            getLogger().info("Recognized Bedrock via "
                    + (host != null && host.contains(FloodgateRuntime.IDENTIFIER) ? "hostname+key" : "UUID heuristic")
                    + ": " + info.name() + " xuid=" + Long.toUnsignedString(info.xuid()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        runtime.forget(event.getPlayer().getUniqueId());
    }

    /** API for other YaP plugins. */
    public boolean isBedrock(Player player) {
        return player != null && runtime.isBedrock(player.getUniqueId());
    }

    public boolean isBedrock(UUID uuid) {
        return runtime.isBedrock(uuid);
    }

    public String xuid(Player player) {
        return runtime.get(player.getUniqueId())
                .map(i -> Long.toUnsignedString(i.xuid()))
                .orElse(null);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("yapfloodgate")) {
            return false;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("yapfloodgate.admin")) {
                YapMessages.noPermission(sender, "yapfloodgate.admin");
                return true;
            }
            reloadConfig();
            Path key = getDataFolder().toPath().resolve(getConfig().getString("key-file", "key.pem"));
            runtime = new FloodgateRuntime(getLogger(), key);
            YapMessages.reloaded(sender, "YaPFloodgate");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("grim")) {
            if (!sender.hasPermission("yapfloodgate.admin")) {
                YapMessages.noPermission(sender, "yapfloodgate.admin");
                return true;
            }
            sender.sendMessage(grimFloodgateDiagnostic());
            return true;
        }
        Player target;
        if (args.length >= 1) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("Player not found.");
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("Usage: /yapfloodgate [player]|reload|grim");
            return true;
        }
        var info = runtime.get(target.getUniqueId());
        if (info.isEmpty() && !runtime.isBedrock(target.getUniqueId())) {
            sender.sendMessage(target.getName() + " is Java (not Floodgate Bedrock).");
            return true;
        }
        if (info.isPresent()) {
            var i = info.get();
            sender.sendMessage("Bedrock " + i.name()
                    + " xuid=" + Long.toUnsignedString(i.xuid())
                    + " linked=" + i.linked()
                    + " bedrockName=" + i.bedrockUsername());
        } else {
            sender.sendMessage(target.getName() + " looks Floodgate (UUID MSB=0) xuid="
                    + Long.toUnsignedString(target.getUniqueId().getLeastSignificantBits()));
        }
        return true;
    }

    /**
     * Prove Grim can resolve our FloodgateApi (softdepend name=floodgate + InstanceHolder).
     * Without this, Reach treats Bedrock as Java and cancels hits (BE-COMBAT-01).
     */
    private String grimFloodgateDiagnostic() {
        StringBuilder out = new StringBuilder();
        var api = org.geysermc.floodgate.api.FloodgateApi.getInstance();
        out.append("FloodgateApi.getInstance()=").append(api != null ? api.getClass().getName() : "null");
        UUID sampleBe = new UUID(0L, 0xBEEFL);
        UUID sampleJe = UUID.randomUUID();
        if (api != null) {
            out.append(" isFloodgatePlayer(MSB0)=").append(api.isFloodgatePlayer(sampleBe));
            out.append(" isFloodgatePlayer(random)=").append(api.isFloodgatePlayer(sampleJe));
        }
        try {
            Class<?> geyserUtil = Class.forName("ac.grim.grimac.utils.reflection.GeyserUtil");
            var floodgateField = geyserUtil.getDeclaredField("floodgate");
            floodgateField.setAccessible(true);
            boolean grimSeesFloodgate = floodgateField.getBoolean(null);
            out.append(" | Grim GeyserUtil.floodgate=").append(grimSeesFloodgate);
            if (grimSeesFloodgate) {
                boolean be = (Boolean) geyserUtil.getMethod("isBedrockPlayer", UUID.class)
                        .invoke(null, sampleBe);
                boolean je = (Boolean) geyserUtil.getMethod("isBedrockPlayer", UUID.class)
                        .invoke(null, sampleJe);
                out.append(" isBedrockPlayer(MSB0)=").append(be);
                out.append(" isBedrockPlayer(random)=").append(je);
                out.append(be && !je ? " OK" : " FAIL");
            } else {
                out.append(" FAIL — Grim did not see FloodgateApi at class-init (softdepend name?)");
            }
        } catch (ClassNotFoundException e) {
            out.append(" | Grim GeyserUtil not loaded yet");
        } catch (ReflectiveOperationException e) {
            out.append(" | Grim reflect error: ").append(e.getMessage());
        }
        return out.toString();
    }
}
