package com.yapcore.config.network;

import com.yapcore.config.ConfigSupport;

import java.util.Properties;

/** Internet exposure, public join endpoints, SRV, and nginx front-door settings. */
public final class PublicEndpointConfig {

    private final Properties props;

    public PublicEndpointConfig(Properties props) {
        this.props = props;
    }

    public static void applyDefaults(Properties props) {
        props.setProperty("internet-exposed", "false");
        props.setProperty("public-host", "");
        props.setProperty("server-domain", "");
        props.setProperty("public-port", "0");
        props.setProperty("public-bedrock-port", "0");
        props.setProperty("public-pack-port", "0");
        props.setProperty("srv-enabled", "true");
        props.setProperty("srv-priority", "0");
        props.setProperty("srv-weight", "5");
        props.setProperty("nginx-public-port", "25565");
        props.setProperty("nginx-pack-port", "80");
        props.setProperty("nginx-domain", "");
    }

    /** When true, listen for remote clients and print public join URLs. */
    public boolean isInternetExposed() {
        return Boolean.parseBoolean(props.getProperty("internet-exposed", "false"));
    }

    public void setInternetExposed(boolean exposed) {
        props.setProperty("internet-exposed", Boolean.toString(exposed));
    }

    /** Public DNS name or IP players connect to (may differ from bind-host). */
    public String getPublicHost() {
        return props.getProperty("public-host", "");
    }

    public void setPublicHost(String host) {
        props.setProperty("public-host", host == null ? "" : host.trim());
    }

    /** Alias for public-host when you want an explicit domain field. */
    public String getServerDomain() {
        return props.getProperty("server-domain", "");
    }

    public void setServerDomain(String domain) {
        props.setProperty("server-domain", domain == null ? "" : domain.trim());
    }

    /** Advertised Java TCP port after NAT (0 = same as server port). */
    public int getPublicPort() {
        return ConfigSupport.parseInt(props, "public-port", 0);
    }

    public void setPublicPort(int port) {
        props.setProperty("public-port", Integer.toString(Math.max(0, port)));
    }

    public int getPublicBedrockPort() {
        return ConfigSupport.parseInt(props, "public-bedrock-port", 0);
    }

    public void setPublicBedrockPort(int port) {
        props.setProperty("public-bedrock-port", Integer.toString(Math.max(0, port)));
    }

    public int getPublicPackPort() {
        return ConfigSupport.parseInt(props, "public-pack-port", 0);
    }

    public void setPublicPackPort(int port) {
        props.setProperty("public-pack-port", Integer.toString(Math.max(0, port)));
    }

    public boolean isSrvEnabled() {
        return Boolean.parseBoolean(props.getProperty("srv-enabled", "true"));
    }

    public void setSrvEnabled(boolean enabled) {
        props.setProperty("srv-enabled", Boolean.toString(enabled));
    }

    public int getSrvPriority() {
        return ConfigSupport.parseInt(props, "srv-priority", 0);
    }

    public int getSrvWeight() {
        return ConfigSupport.parseInt(props, "srv-weight", 5);
    }

    public int getNginxPublicPort() {
        return ConfigSupport.parseInt(props, "nginx-public-port", 25565);
    }

    public void setNginxPublicPort(int port) {
        props.setProperty("nginx-public-port", Integer.toString(Math.max(1, port)));
    }

    public int getNginxPackPort() {
        return ConfigSupport.parseInt(props, "nginx-pack-port", 80);
    }

    public void setNginxPackPort(int port) {
        props.setProperty("nginx-pack-port", Integer.toString(Math.max(1, port)));
    }

    public String getNginxDomain() {
        return props.getProperty("nginx-domain", "");
    }

    public void setNginxDomain(String domain) {
        props.setProperty("nginx-domain", domain == null ? "" : domain.trim());
    }
}
