package com.yapcore.network.publicity;

import com.yapcore.config.ServerConfig;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Join addresses for clients on the same machine as the server.
 * Prefer 127.0.0.1 / localhost to avoid hairpin-NAT failures.
 */
public final class LocalJoinAddresses {

    private final ServerConfig config;

    public LocalJoinAddresses(ServerConfig config) {
        this.config = config;
    }

    public int gamePort() {
        return config.getPort();
    }

    /** Primary same-PC Java/crossplay address. */
    public String loopback() {
        return "127.0.0.1:" + gamePort();
    }

    public String localhostName() {
        return "localhost:" + gamePort();
    }

    public String ipv6Loopback() {
        return "[::1]:" + gamePort();
    }

    public List<String> allPreferred() {
        Set<String> out = new LinkedHashSet<>();
        out.add(loopback());
        out.add(localhostName());
        PublicEndpoint.guessLocalIpv4().ifPresent(ip -> out.add(ip + ":" + gamePort()));
        return new ArrayList<>(out);
    }

    public String tip() {
        return "On this PC use 127.0.0.1:" + gamePort()
                + " (not your public IP — hairpin NAT often blocks that).";
    }
}
