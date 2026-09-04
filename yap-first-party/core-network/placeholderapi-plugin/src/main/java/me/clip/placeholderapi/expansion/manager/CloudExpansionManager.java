package me.clip.placeholderapi.expansion.manager;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import me.clip.placeholderapi.PlaceholderAPIPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * eCloud is intentionally unavailable — YaP ships a curated local-expansions product.
 * Drop expansion jars into {@code plugins/PlaceholderAPI/expansions/} and use
 * {@code /papi reload} or {@code /papi register <jar>}.
 */
public final class CloudExpansionManager {

    public CloudExpansionManager(@NotNull PlaceholderAPIPlugin plugin) {
        // no-op
    }

    public void load() {
        // intentionally empty
    }

    public void kill() {
        // intentionally empty
    }

    @NotNull
    public Optional<Object> findCloudExpansionByName(@NotNull String name) {
        return Optional.empty();
    }

    @NotNull
    public Set<String> getCloudExpansions() {
        return Collections.emptySet();
    }
}
