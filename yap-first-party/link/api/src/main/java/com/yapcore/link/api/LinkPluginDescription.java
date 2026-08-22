package com.yapcore.link.api;

import java.util.List;

/** Metadata from {@code link-plugin.json} inside a plugin jar. */
public record LinkPluginDescription(
        String id,
        String name,
        String version,
        String main,
        List<String> authors
) {
}
