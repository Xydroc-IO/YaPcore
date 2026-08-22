package com.yapcore.bedrock.ui;

import java.util.Optional;

/**
 * Optional chassis-level Bedrock UI backend installed by YaPcore when the dual-stack
 * gateway is running (FormService + BedrockUiBridge packet path).
 */
public final class BedrockUiBackend {

    private static volatile BedrockUiService delegate;

    private BedrockUiBackend() {
    }

    public static void install(BedrockUiService service) {
        delegate = service;
    }

    public static void clear() {
        delegate = null;
    }

    public static Optional<BedrockUiService> get() {
        return Optional.ofNullable(delegate);
    }
}
