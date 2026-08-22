package com.yapcore.server.console;

import com.yapcore.client.ClientEdition;
import com.yapcore.crash.CrashLogger;
import com.yapcore.network.publicity.PublicityCommands;
import com.yapcore.ranks.YapRanks;
import com.yapcore.server.YaPcoreServer;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Console command dispatch for {@link YaPcoreServer}.
 */
public final class ServerConsoleCommands {

    private static final Logger LOG = Logger.getLogger("YaPcore.Server");

    private ServerConsoleCommands() {
    }

    public static String execute(YaPcoreServer server, String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String line = raw.trim();
        String[] parts = line.split("\\s+");
        String cmd = parts[0].toLowerCase(Locale.ROOT);

        return switch (cmd) {
            case "help", "?" -> """
                    Commands:
                      help                 Show this help
                      status               Engine / player / memory status
                      say <msg>            Broadcast a message
                      list                 List online player count
                      maxplayers [n]       Get/set max players (saved)
                      plugins              List installed plugins
                      packs                List resource / texture packs
                      setpack <file>       Set active pack (seamless client DL)
                      clearpack            Clear active pack
                      joinjava <name> [proto]   Simulate Java client join
                      joinbedrock <name> [proto] Simulate Bedrock client join
                      crashdump [reason]   Write full diagnostic crash report
                      ranks [status|apply] YaPPerms group pack (default/vip/mod/admin)
                      stop / end           Graceful shutdown
                      demo                 Run store-purchase lifecycle demo

                      Minecraft / Paper / plugin commands (when Paper is running):
                      Type them here — e.g. give @p diamond 1, tp Steve 0 64 0,
                      gamemode creative @a, op YourName, difficulty hard
                      Leading / is optional. In-game vanilla commands need OP:
                      set ops=YourName in config, or run: op YourName
                    """ + PublicityCommands.helpLines();
            case "status" -> server.statusReport();
            case "ranks", "yapranks" -> ranksCommand(server, parts);
            case "say" -> {
                String msg = line.length() > 4 ? line.substring(4).trim() : "";
                server.getBukkitServer().broadcastMessage(msg);
                yield "Broadcast: " + msg;
            }
            case "list" -> "Players: " + server.getOnlinePlayers() + " / " + server.getConfig().getMaxPlayers();
            case "maxplayers" -> {
                if (parts.length == 1) {
                    yield "max-players=" + server.getConfig().getMaxPlayers();
                }
                try {
                    int n = Integer.parseInt(parts[1]);
                    server.getConfig().setMaxPlayers(n);
                    server.getConfig().save();
                    server.getEngine().setMaxPlayers(server.getConfig().getMaxPlayers());
                    yield "max-players set to " + server.getConfig().getMaxPlayers() + " (saved)";
                } catch (Exception e) {
                    yield "Usage: maxplayers <number>";
                }
            }
            case "plugins" -> {
                var list = server.getPluginManager().listPlugins();
                int legacy = server.getBukkitServer().getPluginManager().getPlugins().length;
                int yap = server.getPluginRuntime().getYaPPlugins().size();
                if (list.isEmpty()) {
                    yield "No plugins installed in " + server.getPluginManager().getPluginsDir();
                }
                StringBuilder sb = new StringBuilder("Plugins on disk (" + list.size() + "), loaded legacy="
                        + legacy + " yap=" + yap + ":\n");
                for (var p : list) {
                    sb.append("  - ").append(p.fileName()).append(" (").append(p.sizeLabel()).append(")\n");
                }
                yield sb.toString().trim();
            }
            case "packs" -> {
                var packs = server.getResourcePacks().listPacks();
                String active = server.getResourcePacks().getActivePack().map(p -> p.getFileName()).orElse("none");
                if (packs.isEmpty()) {
                    yield "No packs in " + server.getResourcePacks().getPacksDir() + " (active=" + active + ")";
                }
                StringBuilder sb = new StringBuilder("Resource packs (active=" + active + "):\n");
                for (var p : packs) {
                    sb.append("  - ").append(p.getFileName())
                            .append(" sha1=").append(p.getSha1Hex().substring(0, 8))
                            .append("… (").append(p.sizeLabel()).append(")\n");
                }
                yield sb.toString().trim();
            }
            case "setpack" -> {
                if (parts.length < 2) {
                    yield "Usage: setpack <filename.zip|filename.mcpack>";
                }
                try {
                    server.getResourcePacks().setActivePack(parts[1]);
                    yield "Active pack set to " + parts[1]
                            + " → " + server.getResourcePacks().buildPublicUrl(parts[1]);
                } catch (Exception e) {
                    yield "setpack failed: " + e.getMessage();
                }
            }
            case "clearpack" -> {
                try {
                    server.getResourcePacks().setActivePack("");
                    yield "Active pack cleared";
                } catch (Exception e) {
                    yield "clearpack failed: " + e.getMessage();
                }
            }
            case "joinjava" -> {
                String name = parts.length > 1 ? parts[1] : "Steve";
                int proto = parts.length > 2 ? Integer.parseInt(parts[2])
                        : server.getGateway().getProtocols().recommended(ClientEdition.JAVA).protocolId();
                boolean ok = server.tryJoinEdition(name, ClientEdition.JAVA, proto);
                yield ok ? "Java join OK: " + name : "Java join denied: " + name;
            }
            case "joinbedrock" -> {
                String name = parts.length > 1 ? parts[1] : "Alex";
                int proto = parts.length > 2 ? Integer.parseInt(parts[2])
                        : server.getGateway().getProtocols().recommended(ClientEdition.BEDROCK).protocolId();
                boolean ok = server.tryJoinEdition(name, ClientEdition.BEDROCK, proto);
                yield ok ? "Bedrock join OK: " + name : "Bedrock join denied: " + name;
            }
            case "crashdump", "crash" -> {
                String reason = parts.length > 1 ? line.substring(cmd.length()).trim() : "manual";
                Path file = CrashLogger.get().dump("manual-" + reason, null, Map.of("reason", reason));
                yield file == null ? "Failed to write crash dump" : "Crash dump written: " + file;
            }
            case "demo" -> {
                try {
                    server.getEngine().runLifecycleDemo();
                    yield "Demo finished";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    yield "Demo interrupted";
                }
            }
            case "stop", "end", "shutdown" -> {
                LOG.info("Stop requested via console");
                Thread t = new Thread(server::stop, "yap-console-stop");
                t.start();
                yield "Stopping…";
            }
            default -> {
                var publicity = PublicityCommands.tryHandle(cmd, parts, line,
                        server.getConfig(), server.getResourcePacks());
                if (publicity.isPresent()) {
                    yield publicity.get();
                }
                var gameReply = server.tryDispatchGameCommand(line);
                if (gameReply.isPresent()) {
                    yield gameReply.get();
                }
                yield "Unknown command: " + cmd + " (type 'help')";
            }
        };
    }

    /** Apply / inspect the native YaPPerms rank pack. */
    public static String ranksCommand(YaPcoreServer server, String[] parts) {
        String sub = parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "status";
        try {
            return switch (sub) {
                case "status" -> {
                    boolean yapPerms = YapRanks.yapPermsInstalled(server.getPluginManager().getPluginsDir());
                    boolean applied = YapRanks.isApplied(server.getRootDir());
                    int cmds = YapRanks.loadCommands(server.getRootDir()).size();
                    yield "YaP ranks (YaPPerms)\n"
                            + "  yap-perms-jar=" + yapPerms + "\n"
                            + "  pack-applied=" + applied + "\n"
                            + "  reference-commands=" + cmds + "\n"
                            + "  auto-apply=" + server.getConfig().isYapRanksAutoApply() + "\n"
                            + "  paper-running=" + server.paperKernel().isRunning() + "\n"
                            + "  apply: ranks apply → yapperm applypack\n"
                            + "  assign: /yapperm user <name> parent set vip\n"
                            + "  dashboard: Ranks tab → Apply pack";
                }
                case "apply" -> {
                    boolean force = parts.length > 2 && "force".equalsIgnoreCase(parts[2]);
                    java.util.function.Function<String, String> dispatch = null;
                    if (server.getConfig().isFoliaAuthority() && server.foliaKernel().isRunning()) {
                        dispatch = server.foliaKernel()::dispatchConsoleCommand;
                    } else if (server.paperKernel().isRunning()) {
                        dispatch = server.paperKernel()::dispatchConsoleCommand;
                    }
                    if (dispatch == null) {
                        yield "Folia/Paper must be running to apply rank pack.";
                    }
                    if (!YapRanks.yapPermsInstalled(server.getPluginManager().getPluginsDir())) {
                        yield "yap-perms.jar not found in plugins/. Run: gradle installProductDefaults";
                    }
                    var result = YapRanks.apply(server.getRootDir(), dispatch, force);
                    yield result.summary();
                }
                case "reset-marker" -> {
                    YapRanks.clearApplied(server.getRootDir());
                    yield "Cleared config/yap-ranks-applied — next ranks apply will run the pack again.";
                }
                case "show" -> String.join("\n", YapRanks.loadCommands(server.getRootDir()));
                default -> "Usage: ranks <status|apply [force]|reset-marker|show>";
            };
        } catch (Exception e) {
            return "ranks failed: " + e.getMessage();
        }
    }
}
