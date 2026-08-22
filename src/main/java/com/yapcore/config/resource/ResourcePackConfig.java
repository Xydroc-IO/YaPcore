package com.yapcore.config.resource;

import com.yapcore.config.ConfigSupport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Resource / texture pack settings. */
public final class ResourcePackConfig {

    private final Properties props;

    public ResourcePackConfig(Properties props) {
        this.props = props;
    }

    public static void applyDefaults(Properties props) {
        props.setProperty("resource-pack-enabled", "true");
        props.setProperty("resource-pack-dir", "resourcepacks");
        props.setProperty("resource-pack-file", "yapcore-default.zip");
        props.setProperty("resource-pack-files", "yapcore-default.zip");
        props.setProperty("resource-pack-http-port", "8081");
        props.setProperty("resource-pack-public-host", "");
        props.setProperty("resource-pack-url", "");
        props.setProperty("resource-pack-forced", "false");
        props.setProperty("resource-pack-prompt",
                "This server offers a resource pack. Click Yes to download, or No to play without it.");
    }

    public boolean isResourcePackEnabled() {
        return Boolean.parseBoolean(props.getProperty("resource-pack-enabled", "true"));
    }

    public void setResourcePackEnabled(boolean enabled) {
        props.setProperty("resource-pack-enabled", Boolean.toString(enabled));
    }

    public Path getResourcePackDir() {
        return Path.of(props.getProperty("resource-pack-dir", "resourcepacks"));
    }

    public String getResourcePackFile() {
        List<String> files = getResourcePackFiles();
        return files.isEmpty() ? props.getProperty("resource-pack-file", "") : files.get(0);
    }

    public void setResourcePackFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            setResourcePackFiles(List.of());
            return;
        }
        setResourcePackFiles(List.of(fileName.trim()));
    }

    /** Ordered active pack zip names. Empty = no packs. */
    public List<String> getResourcePackFiles() {
        String multi = props.getProperty("resource-pack-files", "");
        List<String> out = new ArrayList<>();
        if (multi != null && !multi.isBlank()) {
            for (String part : multi.split(",")) {
                String n = part.trim();
                if (!n.isEmpty() && !out.contains(n)) {
                    out.add(n);
                }
            }
        }
        if (out.isEmpty()) {
            String single = props.getProperty("resource-pack-file", "");
            if (single != null && !single.isBlank()) {
                out.add(single.trim());
            }
        }
        return List.copyOf(out);
    }

    public void setResourcePackFiles(List<String> fileNames) {
        List<String> clean = new ArrayList<>();
        if (fileNames != null) {
            for (String n : fileNames) {
                if (n == null) {
                    continue;
                }
                String t = n.trim();
                if (!t.isEmpty() && !clean.contains(t)) {
                    clean.add(t);
                }
            }
        }
        props.setProperty("resource-pack-files", String.join(",", clean));
        props.setProperty("resource-pack-file", clean.isEmpty() ? "" : clean.get(0));
    }

    public int getResourcePackHttpPort() {
        return ConfigSupport.parseInt(props, "resource-pack-http-port", 8081);
    }

    public void setResourcePackHttpPort(int port) {
        props.setProperty("resource-pack-http-port", Integer.toString(port));
    }

    public String getResourcePackPublicHost() {
        return props.getProperty("resource-pack-public-host", "");
    }

    public void setResourcePackPublicHost(String host) {
        props.setProperty("resource-pack-public-host", host == null ? "" : host);
    }

    /**
     * Optional absolute pack URL. Use {@code {file}} for the active zip name.
     * Empty → build from public host / pack port (see {@code PublicEndpoint#packUrl}).
     */
    public String getResourcePackUrl() {
        return props.getProperty("resource-pack-url", "");
    }

    public void setResourcePackUrl(String url) {
        props.setProperty("resource-pack-url", url == null ? "" : url);
    }

    public boolean isResourcePackForced() {
        return Boolean.parseBoolean(props.getProperty("resource-pack-forced", "false"));
    }

    public void setResourcePackForced(boolean forced) {
        props.setProperty("resource-pack-forced", Boolean.toString(forced));
    }

    public String getResourcePackPrompt() {
        return props.getProperty("resource-pack-prompt",
                "This server uses a resource pack for the best experience.");
    }

    public void setResourcePackPrompt(String prompt) {
        props.setProperty("resource-pack-prompt", prompt == null ? "" : prompt);
    }
}
