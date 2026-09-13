package com.yapcore.link.bedrock;

/**
 * Marker and mode constants for Link-native Bedrock.
 *
 * <p>See {@code docs/geyser-join-reference/ADR-001-link-native-geyser.md} and
 * {@code docs/geyser-join-reference/LINK_NATIVE_PORT.md}.
 */
public final class BedrockNative {

    /** Current UDP forwarder path (no Link-side Bedrock session). */
    public static final String FORWARDER = "forwarder";

    /** First-party Geyser-model Bedrock on Link (YaP rewrite). */
    public static final String NATIVE = "native";

    /** Emergency stock-Geyser-style backup path; not the product default. */
    public static final String GEYSER_BACKUP = "geyser-backup";

    private BedrockNative() {
    }
}
