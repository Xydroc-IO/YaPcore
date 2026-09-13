package com.yapcore.resourcepack;

import com.yapcore.client.ClientSession;
import com.yapcore.config.ServerConfig;
import com.yapcore.network.publicity.PublicEndpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Offer / URL helpers for {@link ResourcePackManager}
 * (split for the ≤500-line domain gate).
 */
final class ResourcePackManagerOffers {

    private static final Logger LOG = Logger.getLogger("YaPcore.ResourcePacks");

    private ResourcePackManagerOffers() {
    }

    static List<ResourcePackOffer> createOffers(ResourcePackManager mgr, ClientSession session) {
        ServerConfig config = mgr.config();
        if (!config.isResourcePackEnabled()) {
            return List.of();
        }
        List<ResourcePackOffer> offers = new ArrayList<>();
        String prompt = config.getResourcePackPrompt();
        boolean forced = config.isResourcePackForced();
        for (ResourcePackInfo pack : mgr.getActivePacks()) {
            String name = pack.getFileName().toLowerCase(Locale.ROOT);
            boolean javaOk = name.endsWith(".zip");
            boolean bedrockOk = name.endsWith(".mcpack");
            String url = buildPublicUrl(mgr, pack.getFileName(), session);
            ResourcePackOffer offer = new ResourcePackOffer(
                    ResourcePackManager.packUuid(pack.getFileName(), pack.getSha1Hex()).toString(),
                    url,
                    pack.getSha1Hex(),
                    prompt == null || prompt.isBlank() ? pack.getPrompt() : prompt,
                    forced,
                    javaOk,
                    bedrockOk,
                    pack.getSizeBytes()
            );
            offers.add(offer);
        }
        if (!offers.isEmpty() && session != null) {
            session.offerResourcePack(offers.get(0));
            LOG.info("Offered " + offers.size() + " resource pack(s) to " + session.getUsername()
                    + " [" + session.getEdition() + "]");
        }
        return offers;
    }

    static Optional<ResourcePackOffer> createOffer(ResourcePackManager mgr, ClientSession session) {
        List<ResourcePackOffer> offers = createOffers(mgr, session);
        return offers.isEmpty() ? Optional.empty() : Optional.of(offers.get(0));
    }

    /**
     * Bedrock login CDN offer ({@code .mcpack} only). Phase 1: always empty
     * (Geyser empty-pack path).
     */
    static Optional<ResourcePackOffer> createBedrockOffer(String clientAddress) {
        LOG.info("BE pack offer empty (Phase-1 handshake only — no CDN)");
        return Optional.empty();
    }

    static String bedrockPackUrl(ResourcePackManager mgr, String fileName, String clientAddress) {
        ServerConfig config = mgr.config();
        PublicEndpoint ep = new PublicEndpoint(config);
        String override = config.getResourcePackUrl();
        if (override != null && !override.isBlank() && !isGithubAssetUrl(override)
                && override.toLowerCase(Locale.ROOT).contains(".mcpack")) {
            return override.replace("{file}", fileName);
        }
        java.net.InetSocketAddress client = parseLooseAddress(clientAddress);
        boolean viaLink = client != null && client.getAddress() != null && client.getAddress().isLoopbackAddress();
        if (viaLink) {
            String lan = PublicEndpoint.guessLocalIpv4().orElse(null);
            if (lan != null && !lan.isBlank()) {
                int port = config.getResourcePackHttpPort();
                String url = "http://" + lan + ":" + port + "/pack/" + fileName;
                LOG.info("BE pack via LAN (Link peer) → " + url);
                return url;
            }
        }
        String publicPackHost = config.getResourcePackPublicHost();
        if ((publicPackHost != null && !publicPackHost.isBlank()) || ep.hasConfiguredDomain()) {
            return ep.packUrlSelfHosted(fileName);
        }
        if (client != null) {
            return ep.packUrlForClient(fileName, client);
        }
        return ep.packUrlSelfHosted(fileName);
    }

    static boolean isGithubAssetUrl(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        return u.contains("github.com/") || u.contains("githubusercontent.com/");
    }

    static java.net.InetSocketAddress parseLooseAddress(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("/")) {
            s = s.substring(1);
        }
        int slash = s.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < s.length()) {
            s = s.substring(slash + 1);
        }
        int colon = s.lastIndexOf(':');
        if (colon <= 0 || colon >= s.length() - 1) {
            return null;
        }
        try {
            String host = s.substring(0, colon);
            int port = Integer.parseInt(s.substring(colon + 1));
            return new java.net.InetSocketAddress(host, port);
        } catch (Exception e) {
            return null;
        }
    }

    static String buildPublicUrl(ResourcePackManager mgr, String fileName) {
        return new PublicEndpoint(mgr.config()).packUrl(fileName);
    }

    static String buildPublicUrl(ResourcePackManager mgr, String fileName, ClientSession session) {
        var ep = new PublicEndpoint(mgr.config());
        if (session != null && session.getAddress() != null) {
            return ep.packUrlForClient(fileName, session.getAddress());
        }
        return ep.packUrl(fileName);
    }
}
