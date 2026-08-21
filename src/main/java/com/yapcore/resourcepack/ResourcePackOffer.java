package com.yapcore.resourcepack;

/**
 * Offer sent to a connecting client so the pack downloads seamlessly.
 */
public record ResourcePackOffer(
        String packId,
        String url,
        String sha1Hex,
        String prompt,
        boolean forced,
        boolean javaCompatible,
        boolean bedrockCompatible
) {
}
