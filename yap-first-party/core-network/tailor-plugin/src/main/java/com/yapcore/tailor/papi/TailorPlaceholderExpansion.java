package com.yapcore.tailor.papi;

import com.yapcore.tailor.ActiveSkin;
import com.yapcore.tailor.TailorException;
import com.yapcore.tailor.TailorServiceImpl;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Concrete PlaceholderExpansion — only loaded via {@link Class#forName} when PAPI is present.
 */
public final class TailorPlaceholderExpansion extends PlaceholderExpansion {

    private final TailorServiceImpl service;

    public TailorPlaceholderExpansion(TailorServiceImpl service) {
        this.service = service;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "yaptailor";
    }

    @Override
    public @NotNull String getAuthor() {
        return "YapLabs";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        try {
            String key = params.toLowerCase(Locale.ROOT);
            return switch (key) {
                case "skin", "url" -> service.getActiveSkin(player.getUniqueId())
                        .map(s -> s.sourceUrl() == null ? "" : s.sourceUrl()).orElse("");
                case "model" -> service.getActiveSkin(player.getUniqueId())
                        .map(s -> s.model().name().toLowerCase(Locale.ROOT)).orElse("");
                case "slots", "wardrobe" -> Integer.toString(service.listWardrobe(player.getUniqueId()).size());
                case "cape" -> service.getActiveSkin(player.getUniqueId())
                        .map(s -> s.capeUrl() == null ? "" : s.capeUrl()).orElse("");
                default -> null;
            };
        } catch (TailorException e) {
            return "";
        }
    }
}
