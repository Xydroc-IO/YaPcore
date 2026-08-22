package com.yapcore.crossplay.bedrock;

import com.yapcore.protocol.DualStackGateway;

/** Exposes the live gateway to first-party plugins in the same JVM (M5 Bedrock UI bridge). */
public final class BedrockUiGatewayHolder {

    private static volatile DualStackGateway gateway;

    private BedrockUiGatewayHolder() {
    }

    public static void attach(DualStackGateway g) {
        gateway = g;
    }

    public static void detach() {
        gateway = null;
    }

    public static DualStackGateway gateway() {
        return gateway;
    }
}
