package com.yapcore.bedrock.ui;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Typed custom form builder (label / input / toggle / slider / dropdown).
 * Builds Cumulus-compatible content JSON and opens via {@link BedrockUiService#sendCustomForm}.
 */
public final class BedrockCustomFormBuilder {

    private final BedrockUiService service;
    private final Player player;
    private final String title;
    private final List<String> elements = new ArrayList<>();
    private Consumer<BedrockFormResult> onResult;

    public BedrockCustomFormBuilder(BedrockUiService service, Player player, String title) {
        this.service = service;
        this.player = player;
        this.title = title == null ? "" : title;
    }

    public BedrockCustomFormBuilder label(String text) {
        elements.add("{\"type\":\"label\",\"text\":\"" + escape(text) + "\"}");
        return this;
    }

    public BedrockCustomFormBuilder input(String text, String placeholder, String defaultValue) {
        elements.add("{\"type\":\"input\",\"text\":\"" + escape(text)
                + "\",\"placeholder\":\"" + escape(placeholder)
                + "\",\"default\":\"" + escape(defaultValue) + "\"}");
        return this;
    }

    public BedrockCustomFormBuilder toggle(String text, boolean defaultValue) {
        elements.add("{\"type\":\"toggle\",\"text\":\"" + escape(text)
                + "\",\"default\":" + defaultValue + "}");
        return this;
    }

    public BedrockCustomFormBuilder slider(String text, float min, float max, float step, float def) {
        elements.add("{\"type\":\"slider\",\"text\":\"" + escape(text)
                + "\",\"min\":" + min + ",\"max\":" + max + ",\"step\":" + step
                + ",\"default\":" + def + "}");
        return this;
    }

    public BedrockCustomFormBuilder dropdown(String text, String... options) {
        StringBuilder opts = new StringBuilder("[");
        for (int i = 0; i < options.length; i++) {
            if (i > 0) {
                opts.append(',');
            }
            opts.append('"').append(escape(options[i])).append('"');
        }
        opts.append(']');
        elements.add("{\"type\":\"dropdown\",\"text\":\"" + escape(text)
                + "\",\"options\":" + opts + ",\"default\":0}");
        return this;
    }

    public BedrockCustomFormBuilder onResult(Consumer<BedrockFormResult> handler) {
        this.onResult = handler;
        return this;
    }

    /** Content JSON array (without wrapping form envelope) — useful for tests. */
    public String contentJson() {
        return "[" + String.join(",", elements) + "]";
    }

    /** Open the form for the player. Returns form id or -1. */
    public int open() {
        return service.sendCustomForm(player, title, contentJson(), onResult);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
