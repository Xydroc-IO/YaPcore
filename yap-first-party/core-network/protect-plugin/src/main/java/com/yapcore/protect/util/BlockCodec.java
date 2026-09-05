package com.yapcore.protect.util;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encodes block material + BlockData, plus meaningful tile-entity state where Paper exposes it.
 *
 * <p>Wire format: {@code MATERIAL|blockData} optionally followed by {@code |#te;k=v;k=v…}.
 * Supported TE keys: sign front/back lines ({@code sf0}…{@code sb3}), skull owner name
 * ({@code skull}), banner base + patterns ({@code banner}, {@code bp}).
 *
 * <p><b>Limits (honest):</b> not full Mojang NBT. Containers use {@link InventoryCodec} on
 * {@code CONTAINER_INVENTORY} rows (not duplicated here). No lectern book pages, no shulker
 * nested NBT beyond inventory rows, no player-head texture profile hashes (name/UUID owner only),
 * no beacon/spawner/jukebox extras, no PersistentDataContainer blobs.
 */
public final class BlockCodec {

    static final String TE_MARK = "#te";

    private BlockCodec() {
    }

    public static String encode(Block block) {
        if (block == null || block.getType().isAir()) {
            return "AIR";
        }
        return encode(block.getState());
    }

    public static String encode(BlockState state) {
        if (state == null || state.getType().isAir()) {
            return "AIR";
        }
        String base = state.getType().name() + "|" + state.getBlockData().getAsString();
        String te = encodeTileExtras(state);
        return te.isEmpty() ? base : base + "|" + te;
    }

    public static void apply(Block block, String encoded) {
        if (encoded == null || encoded.isBlank() || "AIR".equalsIgnoreCase(encoded)) {
            block.setType(Material.AIR, false);
            return;
        }
        EncodedParts parts = splitEncoded(encoded);
        Material material = Material.matchMaterial(parts.material());
        if (material == null) {
            return;
        }
        block.setType(material, false);
        if (parts.blockData() != null && !parts.blockData().isBlank()) {
            try {
                block.setBlockData(Bukkit.createBlockData(parts.blockData()));
            } catch (IllegalArgumentException ignored) {
                // keep material if data string is stale across versions
            }
        }
        if (parts.tileExtras() != null && !parts.tileExtras().isBlank()) {
            applyTileExtras(block.getState(), parts.tileExtras());
        }
    }

    /** Package-visible for unit tests. */
    static EncodedParts splitEncoded(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return new EncodedParts("AIR", null, null);
        }
        String[] segs = encoded.split("\\|", -1);
        String material = segs[0];
        String blockData = null;
        String te = null;
        for (int i = 1; i < segs.length; i++) {
            String seg = segs[i];
            if (seg.startsWith(TE_MARK)) {
                te = seg;
            } else if (blockData == null) {
                blockData = seg;
            }
        }
        return new EncodedParts(material, blockData, te);
    }

    static String encodeTileExtras(BlockState state) {
        Map<String, String> kv = new LinkedHashMap<>();
        if (state instanceof Sign sign) {
            encodeSignSide(kv, "sf", sign.getSide(Side.FRONT));
            encodeSignSide(kv, "sb", sign.getSide(Side.BACK));
        }
        if (state instanceof Skull skull) {
            encodeSkull(kv, skull);
        }
        if (state instanceof Banner banner) {
            encodeBanner(kv, banner);
        }
        if (kv.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(TE_MARK);
        for (Map.Entry<String, String> e : kv.entrySet()) {
            sb.append(';').append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    static void applyTileExtras(BlockState state, String teSegment) {
        if (state == null || teSegment == null || teSegment.isBlank()) {
            return;
        }
        Map<String, String> kv = parseTe(teSegment);
        if (kv.isEmpty()) {
            return;
        }
        boolean dirty = false;
        if (state instanceof Sign sign) {
            dirty |= applySignSide(sign.getSide(Side.FRONT), "sf", kv);
            dirty |= applySignSide(sign.getSide(Side.BACK), "sb", kv);
        }
        if (state instanceof Skull skull) {
            dirty |= applySkull(skull, kv);
        }
        if (state instanceof Banner banner) {
            dirty |= applyBanner(banner, kv);
        }
        if (dirty) {
            state.update(true, false);
        }
    }

    /** Package-visible for unit tests. */
    static Map<String, String> parseTe(String teSegment) {
        Map<String, String> out = new LinkedHashMap<>();
        if (teSegment == null || teSegment.isBlank()) {
            return out;
        }
        String body = teSegment.startsWith(TE_MARK) ? teSegment.substring(TE_MARK.length()) : teSegment;
        if (body.startsWith(";")) {
            body = body.substring(1);
        }
        if (body.isBlank()) {
            return out;
        }
        for (String part : body.split(";")) {
            if (part.isBlank()) {
                continue;
            }
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            out.put(part.substring(0, eq), part.substring(eq + 1));
        }
        return out;
    }

    private static void encodeSignSide(Map<String, String> kv, String prefix, SignSide side) {
        if (side == null) {
            return;
        }
        String[] lines = side.getLines();
        for (int i = 0; i < lines.length && i < 4; i++) {
            String line = lines[i];
            if (line != null && !line.isEmpty()) {
                kv.put(prefix + i, b64(line));
            }
        }
    }

    private static boolean applySignSide(SignSide side, String prefix, Map<String, String> kv) {
        if (side == null) {
            return false;
        }
        boolean dirty = false;
        for (int i = 0; i < 4; i++) {
            String encoded = kv.get(prefix + i);
            if (encoded == null) {
                continue;
            }
            side.setLine(i, fromB64(encoded));
            dirty = true;
        }
        return dirty;
    }

    private static void encodeSkull(Map<String, String> kv, Skull skull) {
        try {
            if (skull.hasOwner()) {
                String owner = skull.getOwner();
                if (owner != null && !owner.isBlank()) {
                    kv.put("skull", b64(owner));
                    return;
                }
            }
            OfflinePlayer owning = skull.getOwningPlayer();
            if (owning != null && owning.getName() != null && !owning.getName().isBlank()) {
                kv.put("skull", b64(owning.getName()));
            }
        } catch (Throwable ignored) {
            // Paper API surface varies; skip rather than fail the block encode.
        }
    }

    private static boolean applySkull(Skull skull, Map<String, String> kv) {
        String encoded = kv.get("skull");
        if (encoded == null) {
            return false;
        }
        String name = fromB64(encoded);
        if (name.isBlank()) {
            return false;
        }
        try {
            if (skull.setOwner(name)) {
                return true;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
            skull.setOwningPlayer(offline);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void encodeBanner(Map<String, String> kv, Banner banner) {
        DyeColor base = banner.getBaseColor();
        if (base != null) {
            kv.put("banner", base.name());
        }
        List<Pattern> patterns = banner.getPatterns();
        if (patterns == null || patterns.isEmpty()) {
            return;
        }
        List<String> parts = new ArrayList<>();
        for (Pattern p : patterns) {
            if (p == null || p.getPattern() == null || p.getColor() == null) {
                continue;
            }
            parts.add(p.getPattern().name() + ":" + p.getColor().name());
        }
        if (!parts.isEmpty()) {
            kv.put("bp", String.join(",", parts));
        }
    }

    private static boolean applyBanner(Banner banner, Map<String, String> kv) {
        boolean dirty = false;
        String base = kv.get("banner");
        if (base != null && !base.isBlank()) {
            try {
                banner.setBaseColor(DyeColor.valueOf(base));
                dirty = true;
            } catch (IllegalArgumentException ignored) {
            }
        }
        String bp = kv.get("bp");
        if (bp != null && !bp.isBlank()) {
            List<Pattern> patterns = new ArrayList<>();
            for (String part : bp.split(",")) {
                String[] kvPair = part.split(":", 2);
                if (kvPair.length < 2) {
                    continue;
                }
                try {
                    PatternType type = PatternType.valueOf(kvPair[0]);
                    DyeColor color = DyeColor.valueOf(kvPair[1]);
                    patterns.add(new Pattern(color, type));
                } catch (IllegalArgumentException ignored) {
                }
            }
            banner.setPatterns(patterns);
            dirty = true;
        }
        return dirty;
    }

    static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    static String fromB64(String s) {
        try {
            return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    record EncodedParts(String material, String blockData, String tileExtras) {
    }
}
