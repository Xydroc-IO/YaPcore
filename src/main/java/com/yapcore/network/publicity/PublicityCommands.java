package com.yapcore.network.publicity;

import com.yapcore.config.ServerConfig;
import com.yapcore.resourcepack.ResourcePackManager;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

/** Console commands for domain / internet pointing. */
public final class PublicityCommands {

    private PublicityCommands() {
    }

    public static Optional<String> tryHandle(String cmd,
                                             String[] parts,
                                             String line,
                                             ServerConfig config,
                                             ResourcePackManager packs) {
        return switch (cmd) {
            case "public", "network", "endpoints" ->
                    Optional.of(new PublicEndpoint(config).banner());
            case "domain" -> Optional.of(handleDomain(parts, config, packs));
            case "expose", "internet" -> Optional.of(handleExpose(parts, config, packs));
            case "publichost" -> Optional.of(handlePublicHost(parts, config, packs));
            default -> Optional.empty();
        };
    }

    public static String helpLines() {
        return """
                              public / network     Show public join URLs + DNS SRV hint
                              domain [name]        Get/set server-domain (e.g. play.example.com)
                              publichost [host]    Get/set public-host / IP
                              expose [on|off]      Toggle internet-exposed + bind 0.0.0.0
                """;
    }

    private static String handleDomain(String[] parts, ServerConfig config, ResourcePackManager packs) {
        if (parts.length == 1) {
            String d = config.getServerDomain();
            return "server-domain=" + (d.isBlank() ? "(unset)" : d)
                    + "\n" + new PublicEndpoint(config).javaJoinAddress();
        }
        try {
            String domain = parts[1].trim();
            config.setServerDomain(domain);
            if (config.getPublicHost().isBlank()) {
                config.setPublicHost(domain);
            }
            syncPackHost(config, packs);
            config.save();
            return "Domain set to " + domain + "\n" + new PublicEndpoint(config).banner();
        } catch (IOException e) {
            return "domain save failed: " + e.getMessage();
        }
    }

    private static String handlePublicHost(String[] parts, ServerConfig config, ResourcePackManager packs) {
        if (parts.length == 1) {
            return "public-host=" + blankAsUnset(config.getPublicHost())
                    + " effective=" + new PublicEndpoint(config).publicHost();
        }
        try {
            config.setPublicHost(parts[1].trim());
            syncPackHost(config, packs);
            config.save();
            return "public-host set\n" + new PublicEndpoint(config).banner();
        } catch (IOException e) {
            return "publichost save failed: " + e.getMessage();
        }
    }

    private static String handleExpose(String[] parts, ServerConfig config, ResourcePackManager packs) {
        if (parts.length == 1) {
            return "internet-exposed=" + config.isInternetExposed()
                    + " bind=" + config.getBindHost();
        }
        String arg = parts[1].toLowerCase(Locale.ROOT);
        boolean on = arg.equals("on") || arg.equals("true") || arg.equals("1") || arg.equals("yes");
        boolean off = arg.equals("off") || arg.equals("false") || arg.equals("0") || arg.equals("no");
        if (!on && !off) {
            return "Usage: expose on|off";
        }
        try {
            config.setInternetExposed(on);
            PublicEndpoint ep = new PublicEndpoint(config);
            if (on) {
                ep.applyInternetBind();
            }
            syncPackHost(config, packs);
            config.save();
            return (on ? "Internet exposure ON" : "Internet exposure OFF")
                    + "\n" + ep.banner();
        } catch (IOException e) {
            return "expose save failed: " + e.getMessage();
        }
    }

    private static void syncPackHost(ServerConfig config, ResourcePackManager packs) {
        PublicEndpoint ep = new PublicEndpoint(config);
        if (config.getResourcePackPublicHost().isBlank()) {
            config.setResourcePackPublicHost(ep.publicHost());
        }
        packs.setPublicHost(ep.publicHost());
    }

    private static String blankAsUnset(String s) {
        return s == null || s.isBlank() ? "(unset)" : s;
    }
}
