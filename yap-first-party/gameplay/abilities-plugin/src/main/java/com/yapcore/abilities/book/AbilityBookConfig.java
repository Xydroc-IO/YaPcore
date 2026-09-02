package com.yapcore.abilities.book;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AbilityBookConfig {

    public enum OpenTrigger {
        COMMAND,
        TOME,
        SNEAK_SWAP;

        public static OpenTrigger parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String key = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            if ("TOME_RIGHT_CLICK".equals(key) || "TOME_CLICK".equals(key)) {
                return TOME;
            }
            if ("SNEAK_SWAP_HANDS".equals(key) || "SNEAK_F".equals(key)) {
                return SNEAK_SWAP;
            }
            try {
                return OpenTrigger.valueOf(key);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private final boolean enabled;
    private final String title;
    private final int abilitiesPerPage;
    private final boolean showLocked;
    private final Set<OpenTrigger> openTriggers;
    private final boolean tomeEnabled;
    private final String tomeMaterialName;
    private final int tomeCustomModelData;
    private final String tomeDisplayName;
    private final List<String> tomeLore;
    private final boolean giveTomeOnFirstJoin;

    public AbilityBookConfig(FileConfiguration c) {
        enabled = c.getBoolean("ability-book.enabled", true);
        title = c.getString("ability-book.title", "Ability Book");
        abilitiesPerPage = Math.max(6, Math.min(21, c.getInt("ability-book.abilities-per-page", 21)));
        showLocked = c.getBoolean("ability-book.show-locked", true);

        Set<OpenTrigger> triggers = EnumSet.noneOf(OpenTrigger.class);
        List<String> rawTriggers = c.getStringList("ability-book.open-triggers");
        if (rawTriggers == null || rawTriggers.isEmpty()) {
            triggers.add(OpenTrigger.COMMAND);
            triggers.add(OpenTrigger.TOME);
            triggers.add(OpenTrigger.SNEAK_SWAP);
        } else {
            for (String line : rawTriggers) {
                OpenTrigger t = OpenTrigger.parse(line);
                if (t != null) {
                    triggers.add(t);
                }
            }
        }
        if (triggers.isEmpty()) {
            triggers.add(OpenTrigger.COMMAND);
        }
        openTriggers = Set.copyOf(triggers);

        tomeEnabled = c.getBoolean("ability-book.tome.enabled", true);
        tomeMaterialName = c.getString("ability-book.tome.material", "ENCHANTED_BOOK");
        tomeCustomModelData = c.getInt("ability-book.tome.custom-model-data", 79100);
        tomeDisplayName = c.getString("ability-book.tome.display-name", "§dAbility Tome");
        List<String> lore = c.getStringList("ability-book.tome.lore");
        tomeLore = lore == null || lore.isEmpty()
                ? List.of("§7Right-click to open your ability book", "§8Drag spells onto keys §f4–9")
                : List.copyOf(lore);
        giveTomeOnFirstJoin = c.getBoolean("ability-book.tome.give-on-first-join", true);
    }

    public boolean enabled() {
        return enabled;
    }

    public String title() {
        return title;
    }

    public int abilitiesPerPage() {
        return abilitiesPerPage;
    }

    public boolean showLocked() {
        return showLocked;
    }

    public Set<OpenTrigger> openTriggers() {
        return openTriggers;
    }

    public boolean openTrigger(OpenTrigger trigger) {
        return openTriggers.contains(trigger);
    }

    public boolean tomeEnabled() {
        return tomeEnabled;
    }

    public org.bukkit.Material tomeMaterial() {
        org.bukkit.Material mat = org.bukkit.Material.matchMaterial(tomeMaterialName);
        return mat != null && mat.isItem() ? mat : org.bukkit.Material.ENCHANTED_BOOK;
    }

    public String tomeMaterialName() {
        return tomeMaterialName;
    }

    public int tomeCustomModelData() {
        return tomeCustomModelData;
    }

    public String tomeDisplayName() {
        return tomeDisplayName;
    }

    public List<String> tomeLore() {
        return tomeLore;
    }

    public boolean giveTomeOnFirstJoin() {
        return giveTomeOnFirstJoin;
    }
}
