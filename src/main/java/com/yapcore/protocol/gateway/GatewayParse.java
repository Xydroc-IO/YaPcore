package com.yapcore.protocol.gateway;

final class GatewayParse {

    private GatewayParse() {
    }

    static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
