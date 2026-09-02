package com.yapcore.moderation.seen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Import names from vanilla usercache.json into the seen-player directory. */
public final class UserCacheSeed {

    private static final Pattern OBJECT = Pattern.compile("\\{([^{}]*)\\}");
    private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern UUID_FIELD = Pattern.compile("\"uuid\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private UserCacheSeed() {
    }

    public static int apply(SeenPlayerRepository seen, Path... caches) {
        int n = 0;
        for (Path cache : caches) {
            if (cache == null || !Files.isRegularFile(cache)) {
                continue;
            }
            try {
                n += applyFile(seen, cache);
            } catch (Exception ignored) {
            }
        }
        return n;
    }

    private static int applyFile(SeenPlayerRepository seen, Path cache) throws IOException, java.sql.SQLException {
        String raw = Files.readString(cache, StandardCharsets.UTF_8);
        Matcher obj = OBJECT.matcher(raw);
        int n = 0;
        while (obj.find()) {
            String body = obj.group(1);
            Matcher um = UUID_FIELD.matcher(body);
            Matcher nm = NAME.matcher(body);
            if (!um.find() || !nm.find()) {
                continue;
            }
            UUID uuid = SeenPlayerRepository.parseUuid(um.group(1));
            String name = nm.group(1);
            if (uuid == null || name == null || name.isBlank()) {
                continue;
            }
            seen.seedName(uuid, name);
            n++;
        }
        return n;
    }
}
