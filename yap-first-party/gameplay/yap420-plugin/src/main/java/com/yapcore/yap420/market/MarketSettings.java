package com.yapcore.yap420.market;

import com.yapcore.yap420.item.Yap420ItemIds;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Dealer + pack ladder settings from config.yml {@code market}. */
public final class MarketSettings {

    private final boolean enabled;
    private final boolean buyEnabled;
    private final PackMath pack;
    private final Map<String, Double> sellPrices;
    private final Map<String, Double> buyPrices;
    private final Messages messages;

    public MarketSettings(
            boolean enabled,
            boolean buyEnabled,
            PackMath pack,
            Map<String, Double> sellPrices,
            Map<String, Double> buyPrices,
            Messages messages
    ) {
        this.enabled = enabled;
        this.buyEnabled = buyEnabled;
        this.pack = pack;
        this.sellPrices = sellPrices;
        this.buyPrices = buyPrices;
        this.messages = messages;
    }

    public static MarketSettings load(FileConfiguration c) {
        ConfigurationSection sec = c.getConfigurationSection("market");
        if (sec == null) {
            return defaults();
        }
        PackMath pack = new PackMath(
                sec.getInt("grams-per-ounce", 28),
                sec.getInt("ounces-per-brick", 16));
        Map<String, Double> sell = loadPrices(sec.getConfigurationSection("sell"), defaultSell());
        Map<String, Double> buy = loadPrices(sec.getConfigurationSection("buy"), defaultBuy());
        ConfigurationSection msgSec = sec.getConfigurationSection("messages");
        Messages msg = new Messages(
                msg(msgSec, "sold", "&aSold &f{amount}x {item}&a for &f{money}&a."),
                msg(msgSec, "sold-all", "&aSold &f{count}&a stacks for &f{money}&a."),
                msg(msgSec, "bought", "&aBought &f{amount}x {item}&a for &f{money}&a."),
                msg(msgSec, "packed", "&aPacked &f{amount}x {item}&a."),
                msg(msgSec, "unpacked", "&aUnpacked into &f{amount}x {item}&a."),
                msg(msgSec, "need-items", "&cNeed &f{need}x {item}&c (have &f{have}&c)."),
                msg(msgSec, "no-economy", "&cEconomy unavailable — enable YaPPlayerData."),
                msg(msgSec, "disabled", "&cYaP420 dealer is disabled."),
                msg(msgSec, "cannot-afford", "&cNeed &f{money}&c (balance &f{balance}&c)."),
                msg(msgSec, "nothing-to-sell", "&cNo sellable YaP420 items in inventory."),
                msg(msgSec, "inventory-full", "&cInventory full.")
        );
        return new MarketSettings(
                sec.getBoolean("enabled", true),
                sec.getBoolean("buy-enabled", true),
                pack,
                sell,
                buy,
                msg
        );
    }

    public static MarketSettings defaults() {
        return new MarketSettings(true, true, new PackMath(28, 16), defaultSell(), defaultBuy(),
                new Messages(
                        "&aSold &f{amount}x {item}&a for &f{money}&a.",
                        "&aSold &f{count}&a stacks for &f{money}&a.",
                        "&aBought &f{amount}x {item}&a for &f{money}&a.",
                        "&aPacked &f{amount}x {item}&a.",
                        "&aUnpacked into &f{amount}x {item}&a.",
                        "&cNeed &f{need}x {item}&c (have &f{have}&c).",
                        "&cEconomy unavailable — enable YaPPlayerData.",
                        "&cYaP420 dealer is disabled.",
                        "&cNeed &f{money}&c (balance &f{balance}&c).",
                        "&cNo sellable YaP420 items in inventory.",
                        "&cInventory full."
                ));
    }

    private static String msg(ConfigurationSection sec, String key, String fallback) {
        if (sec == null) {
            return fallback;
        }
        return sec.getString(key, fallback);
    }

    private static Map<String, Double> loadPrices(ConfigurationSection sec, Map<String, Double> fallback) {
        if (sec == null) {
            return fallback;
        }
        Map<String, Double> out = new LinkedHashMap<>();
        for (String key : sec.getKeys(false)) {
            if (key == null || key.isBlank()) {
                continue;
            }
            double price = sec.getDouble(key, -1);
            if (price >= 0) {
                out.put(key.trim().toLowerCase(Locale.ROOT), price);
            }
        }
        return out.isEmpty() ? fallback : Collections.unmodifiableMap(out);
    }

    private static Map<String, Double> defaultSell() {
        Map<String, Double> m = new LinkedHashMap<>();
        // Bud ≡ gram
        m.put(Yap420ItemIds.BUD_CURED_SATIVA, 8.0);
        m.put(Yap420ItemIds.BUD_CURED_INDICA, 9.0);
        m.put(Yap420ItemIds.GRAM_SATIVA, 8.0);
        m.put(Yap420ItemIds.GRAM_INDICA, 9.0);
        // 28g packs — slight bulk discount vs loose grams
        m.put(Yap420ItemIds.OUNCE_SATIVA, 200.0);
        m.put(Yap420ItemIds.OUNCE_INDICA, 225.0);
        // 16 oz = 1 pound (item id still yap420_brick_*)
        m.put(Yap420ItemIds.BRICK_SATIVA, 2800.0);
        m.put(Yap420ItemIds.BRICK_INDICA, 3200.0);
        m.put(Yap420ItemIds.JOINT_SATIVA, 18.0);
        m.put(Yap420ItemIds.JOINT_INDICA, 20.0);
        m.put(Yap420ItemIds.BLUNT_SATIVA, 35.0);
        m.put(Yap420ItemIds.BLUNT_INDICA, 40.0);
        m.put(Yap420ItemIds.BROWNIE, 28.0);
        m.put(Yap420ItemIds.HEMP_FIBER, 3.0);
        return Collections.unmodifiableMap(m);
    }

    private static Map<String, Double> defaultBuy() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put(Yap420ItemIds.SEED_SATIVA, 12.0);
        m.put(Yap420ItemIds.SEED_INDICA, 12.0);
        m.put(Yap420ItemIds.ROLLING_PAPER, 4.0);
        m.put(Yap420ItemIds.DRYING_RACK, 80.0);
        m.put(Yap420ItemIds.PACKAGING_PRESS, 120.0);
        return Collections.unmodifiableMap(m);
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean buyEnabled() {
        return buyEnabled;
    }

    public PackMath pack() {
        return pack;
    }

    public Map<String, Double> sellPrices() {
        return sellPrices;
    }

    public Map<String, Double> buyPrices() {
        return buyPrices;
    }

    public Messages messages() {
        return messages;
    }

    public double sellPrice(String itemId) {
        if (itemId == null) {
            return -1;
        }
        return sellPrices.getOrDefault(itemId.toLowerCase(Locale.ROOT), -1.0);
    }

    public double buyPrice(String itemId) {
        if (itemId == null) {
            return -1;
        }
        return buyPrices.getOrDefault(itemId.toLowerCase(Locale.ROOT), -1.0);
    }

    public record Messages(
            String sold,
            String soldAll,
            String bought,
            String packed,
            String unpacked,
            String needItems,
            String noEconomy,
            String disabled,
            String cannotAfford,
            String nothingToSell,
            String inventoryFull
    ) {
    }
}
