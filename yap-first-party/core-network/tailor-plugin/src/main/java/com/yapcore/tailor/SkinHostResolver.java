package com.yapcore.tailor;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Resolve the client-reachable pack/skin HTTP base when YaPTailor config leaves
 * {@code skin-host-public-base-url} empty.
 */
final class SkinHostResolver {

    private SkinHostResolver() {
    }

    /**
     * Prefer explicit env/sys props, then same-JVM chassis {@code SkinService}, then nothing.
     * Never invent a LAN-only URL — other players must be able to GET {@code /skin/{uuid}.png}.
     */
    static Optional<String> resolvePublicBase(Logger log) {
        for (String key : new String[]{
                "YAP_SKIN_HOST_PUBLIC_BASE_URL",
                "YAP_PACK_BASE_URL",
                "yap.skin.host.public.base.url",
                "yap.pack.base.url"
        }) {
            String v = System.getenv(key);
            if (v == null || v.isBlank()) {
                v = System.getProperty(key);
            }
            String normalized = TailorConfig.normalizeSkinHostBase(v);
            if (normalized != null) {
                if (log != null) {
                    log.info("YaPTailor skin host from " + key + " → " + normalized);
                }
                return Optional.of(normalized);
            }
        }
        Optional<String> fromChassis = fromChassisSkinService();
        if (fromChassis.isPresent()) {
            if (log != null) {
                log.info("YaPTailor skin host from chassis SkinService → " + fromChassis.get());
            }
            return fromChassis;
        }
        return Optional.empty();
    }

    private static Optional<String> fromChassisSkinService() {
        try {
            Class<?> holder = Class.forName("com.yapcore.crossplay.bedrock.BedrockUiGatewayHolder");
            Object gateway = holder.getMethod("gateway").invoke(null);
            if (gateway == null) {
                return Optional.empty();
            }
            Object skinService = gateway.getClass().getMethod("skinService").invoke(gateway);
            if (skinService == null) {
                return Optional.empty();
            }
            Object base = skinService.getClass().getMethod("publicSkinBaseUrl").invoke(skinService);
            return Optional.ofNullable(TailorConfig.normalizeSkinHostBase(base == null ? null : base.toString()));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
