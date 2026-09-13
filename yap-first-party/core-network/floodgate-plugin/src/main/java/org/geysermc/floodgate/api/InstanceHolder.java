package org.geysermc.floodgate.api;

/**
 * Minimal Floodgate {@code InstanceHolder} so GrimAC can resolve Bedrock players via
 * {@link FloodgateApi#getInstance()}. YaP does not ship full Floodgate; this is the Grim hook.
 */
public final class InstanceHolder {

    private static volatile FloodgateApi api;

    private InstanceHolder() {
    }

    public static FloodgateApi getApi() {
        return api;
    }

    /** Register YaP's Floodgate-compatible API (call once from YaPFloodgate onEnable). */
    public static void setApi(FloodgateApi floodgateApi) {
        api = floodgateApi;
    }
}
