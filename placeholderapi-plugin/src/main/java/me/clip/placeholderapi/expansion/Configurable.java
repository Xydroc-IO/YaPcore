package me.clip.placeholderapi.expansion;

import java.util.Map;

/** Expansion that contributes default config keys under {@code expansions.<id>}. */
public interface Configurable {

    Map<String, Object> getDefaults();
}
