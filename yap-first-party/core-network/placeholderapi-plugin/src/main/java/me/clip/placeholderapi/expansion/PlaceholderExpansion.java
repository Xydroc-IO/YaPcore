package me.clip.placeholderapi.expansion;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import me.clip.placeholderapi.PlaceholderHook;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registerable placeholder expansion (clip PlaceholderAPI-compatible).
 * YaPcore clean-room — not GPL PlaceholderAPI source.
 */
public abstract class PlaceholderExpansion extends PlaceholderHook {

    protected Type expansionType = Type.INTERNAL;

    @NotNull
    public abstract String getIdentifier();

    @NotNull
    public abstract String getAuthor();

    @NotNull
    public abstract String getVersion();

    @NotNull
    public String getName() {
        return getIdentifier();
    }

    @Nullable
    public String getRequiredPlugin() {
        return getPlugin();
    }

    @NotNull
    public List<String> getPlaceholders() {
        return Collections.emptyList();
    }

    public boolean persist() {
        return false;
    }

    public final boolean isRegistered() {
        return getPlaceholderAPI().getLocalExpansionManager()
                .findExpansionByIdentifier(getIdentifier())
                .map(it -> it.equals(this))
                .orElse(false);
    }

    public boolean canRegister() {
        return getRequiredPlugin() == null
                || Bukkit.getPluginManager().getPlugin(getRequiredPlugin()) != null;
    }

    public boolean register() {
        return getPlaceholderAPI().getLocalExpansionManager().register(this);
    }

    public final boolean unregister() {
        return getPlaceholderAPI().getLocalExpansionManager().unregister(this);
    }

    @NotNull
    public final PlaceholderAPIPlugin getPlaceholderAPI() {
        return PlaceholderAPIPlugin.getInstance();
    }

    public Type getExpansionType() {
        return expansionType;
    }

    public void setExpansionType(Type expansionType) {
        this.expansionType = expansionType;
    }

    @Nullable
    public final ConfigurationSection getConfigSection() {
        return getPlaceholderAPI().getConfig()
                .getConfigurationSection("expansions." + getIdentifier());
    }

    @Nullable
    public final ConfigurationSection getConfigSection(@NotNull final String path) {
        final ConfigurationSection section = getConfigSection();
        return section == null ? null : section.getConfigurationSection(path);
    }

    @Nullable
    public final Object get(@NotNull final String path, final Object def) {
        final ConfigurationSection section = getConfigSection();
        return section == null ? def : section.get(path, def);
    }

    public final int getInt(@NotNull final String path, final int def) {
        final ConfigurationSection section = getConfigSection();
        return section == null ? def : section.getInt(path, def);
    }

    public final long getLong(@NotNull final String path, final long def) {
        final ConfigurationSection section = getConfigSection();
        return section == null ? def : section.getLong(path, def);
    }

    public final double getDouble(@NotNull final String path, final double def) {
        final ConfigurationSection section = getConfigSection();
        return section == null ? def : section.getDouble(path, def);
    }

    @Nullable
    public final String getString(@NotNull final String path, @Nullable final String def) {
        final ConfigurationSection section = getConfigSection();
        return section == null ? def : section.getString(path, def);
    }

    @NotNull
    public final List<String> getStringList(@NotNull final String path) {
        final ConfigurationSection section = getConfigSection();
        return section == null ? Collections.emptyList() : section.getStringList(path);
    }

    public final boolean getBoolean(@NotNull final String path, final boolean def) {
        final ConfigurationSection section = getConfigSection();
        return section == null ? def : section.getBoolean(path, def);
    }

    public final boolean configurationContains(@NotNull final String path) {
        final ConfigurationSection section = getConfigSection();
        return section != null && section.contains(path);
    }

    public void log(Level level, String msg) {
        getPlaceholderAPI().getLogger().log(level, "[" + getName() + "] " + msg);
    }

    public void log(Level level, String msg, Throwable throwable) {
        getPlaceholderAPI().getLogger().log(level, "[" + getName() + "] " + msg, throwable);
    }

    public void info(String msg) {
        log(Level.INFO, msg);
    }

    public void warning(String msg) {
        log(Level.WARNING, msg);
    }

    public void severe(String msg) {
        log(Level.SEVERE, msg);
    }

    public void severe(String msg, Throwable throwable) {
        log(Level.SEVERE, msg, throwable);
    }

    @Override
    public final boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlaceholderExpansion expansion)) {
            return false;
        }
        return getIdentifier().equals(expansion.getIdentifier())
                && getAuthor().equals(expansion.getAuthor())
                && getVersion().equals(expansion.getVersion());
    }

    @Override
    public final int hashCode() {
        return getIdentifier().hashCode();
    }

    @Override
    public final String toString() {
        return String.format(
                "PlaceholderExpansion[name: '%s', author: '%s', version: '%s', type: '%s']",
                getName(), getAuthor(), getVersion(), getExpansionType());
    }

    @Deprecated
    public String getPlugin() {
        return null;
    }

    @Deprecated
    public String getDescription() {
        return null;
    }

    @Deprecated
    public String getLink() {
        return null;
    }

    public enum Type {
        INTERNAL,
        EXTERNAL
    }
}
