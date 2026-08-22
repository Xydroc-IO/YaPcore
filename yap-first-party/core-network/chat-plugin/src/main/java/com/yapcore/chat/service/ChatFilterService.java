package com.yapcore.chat.service;

import com.yapcore.chat.ChatConfig;

import java.util.Locale;

public final class ChatFilterService {

    private final ChatConfig config;

    public ChatFilterService(ChatConfig config) {
        this.config = config;
    }

    public FilterResult filter(String message) {
        if (!config.filterEnabled() || message == null) {
            return new FilterResult(message, false, false);
        }
        String lower = message.toLowerCase(Locale.ROOT);
        String result = message;
        boolean matched = false;
        for (String word : config.filterWords()) {
            if (lower.contains(word)) {
                matched = true;
                if (config.filterBlockOnMatch()) {
                    return new FilterResult(message, true, true);
                }
                result = result.replaceAll("(?i)" + java.util.regex.Pattern.quote(word), config.filterReplacement());
            }
        }
        return new FilterResult(result, matched, false);
    }

    public record FilterResult(String message, boolean matched, boolean blocked) {
    }
}
