package com.yapcore.staff;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Sends staff commands to YaPAdmin / Essentials / Moderation.
 * Prefer the typed builders below so menus stay on canonical argument shapes.
 */
public final class StaffCmds {

    private static final Logger LOGGER = LoggerFactory.getLogger("yap-staff");
    private static final long COOLDOWN_MS = 250L;
    /**
     * Vanilla chat_command decode kicks on long create lines (enchants, many flags).
     * Keep short cmds on chat_command; relay the rest over {@code yap:staff}.
     */
    private static final int CHAT_COMMAND_SAFE_LEN = 200;
    private static long lastSendMs;

    private StaffCmds() {
    }

    public static void run(String command) {
        if (command == null || command.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSendMs < COOLDOWN_MS) {
            return;
        }
        lastSendMs = now;
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft == null ? null : minecraft.getConnection();
        if (connection == null) {
            return;
        }
        String trimmed = command.startsWith("/") ? command.substring(1) : command;
        if (shouldUseStaffChannel(trimmed)) {
            try {
                connection.send(new ServerboundCustomPayloadPacket(StaffChannelPayload.run(trimmed)));
                return;
            } catch (Exception e) {
                LOGGER.warn("yap:staff payload failed; falling back to chat_command (len={})", trimmed.length(), e);
            }
        }
        connection.send(new ServerboundChatCommandPacket(trimmed));
    }

    private static boolean shouldUseStaffChannel(String commandWithoutSlash) {
        if (commandWithoutSlash.length() > CHAT_COMMAND_SAFE_LEN) {
            return true;
        }
        String lower = commandWithoutSlash.toLowerCase(Locale.ROOT);
        return lower.startsWith("yapitems create") || lower.startsWith("yapitems edit");
    }

    public static void runFmt(String format, Object... args) {
        run(String.format(Locale.ROOT, format, args));
    }

    // --- Canonical builders (menus should prefer these) ---

    /** Canonical: {@code /speed [player] <0-10> [fly|walk]}. */
    public static void speed(int level, boolean fly) {
        speed(level, fly, null);
    }

    public static void speed(int level, boolean fly, String playerOrNull) {
        int n = Math.max(0, Math.min(10, level));
        String mode = fly ? "fly" : "walk";
        if (playerOrNull == null || playerOrNull.isBlank()) {
            runFmt("speed %d %s", n, mode);
        } else {
            runFmt("speed %s %d %s", playerOrNull, n, mode);
        }
    }

    /** Canonical: {@code /echest [player]} — omit for self. */
    public static void echest(String playerOrNull) {
        if (playerOrNull == null || playerOrNull.isBlank()) {
            run("echest");
        } else {
            runFmt("echest %s", playerOrNull);
        }
    }

    public static void give(String material, int amount, String playerOrNull) {
        if (playerOrNull != null && !playerOrNull.isBlank()) {
            runFmt("yapadmin give %s %d %s", material, amount, playerOrNull);
        } else {
            runFmt("yapadmin give %s %d", material, amount);
        }
    }

    public static void customItemGive(String itemId, int amount, String playerOrNull) {
        if (playerOrNull != null && !playerOrNull.isBlank()) {
            runFmt("yapitems give %s %d %s", itemId, amount, playerOrNull);
        } else {
            runFmt("yapitems give %s %d", itemId, amount);
        }
    }

    public static void customItemCreate(String id, String template, String name, String abilityOrNull) {
        customItemCreateFull(id, template, name, abilityOrNull, null, null, null, null);
    }

    public static void customItemCreateFull(
            String id,
            String template,
            String name,
            String abilityOrNull,
            String damageOrNull,
            String rangeOrNull,
            String cooldownOrNull,
            String gearAttackOrNull) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        if (abilityOrNull != null && !abilityOrNull.isBlank()) {
            map.put(abilityOrNull, "right_click");
        }
        customItemCreateFull(id, template, name, map, damageOrNull, rangeOrNull, cooldownOrNull, gearAttackOrNull);
    }

    public static void customItemCreateFull(
            String id,
            String template,
            String name,
            java.util.List<String> abilities,
            String damageOrNull,
            String rangeOrNull,
            String cooldownOrNull,
            String gearAttackOrNull) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        if (abilities != null) {
            boolean first = true;
            for (String a : abilities) {
                if (a == null || a.isBlank() || "none".equalsIgnoreCase(a)) {
                    continue;
                }
                map.put(a.trim().toLowerCase(Locale.ROOT), first ? "right_click" : "together");
                first = false;
            }
        }
        customItemCreateFull(id, template, name, map, damageOrNull, rangeOrNull, cooldownOrNull, gearAttackOrNull);
    }

    public static void customItemCreateFull(
            String id,
            String template,
            String name,
            java.util.Map<String, String> abilitiesWithTriggers,
            String damageOrNull,
            String rangeOrNull,
            String cooldownOrNull,
            String gearAttackOrNull) {
        customItemCreateFull(
                id, template, name, abilitiesWithTriggers,
                damageOrNull, rangeOrNull, cooldownOrNull, gearAttackOrNull,
                null, null, null, null, false);
    }

    public static void customItemCreateFull(
            String id,
            String template,
            String name,
            java.util.Map<String, String> abilitiesWithTriggers,
            String damageOrNull,
            String rangeOrNull,
            String cooldownOrNull,
            String gearAttackOrNull,
            String effectOrNull,
            String radiusOrNull,
            String amountOrNull,
            String projectileOrNull) {
        customItemCreateFull(
                id, template, name, abilitiesWithTriggers,
                damageOrNull, rangeOrNull, cooldownOrNull, gearAttackOrNull,
                effectOrNull, radiusOrNull, amountOrNull, projectileOrNull, false);
    }

    public static void customItemCreateFull(
            String id,
            String template,
            String name,
            java.util.Map<String, String> abilitiesWithTriggers,
            String damageOrNull,
            String rangeOrNull,
            String cooldownOrNull,
            String gearAttackOrNull,
            String effectOrNull,
            String radiusOrNull,
            String amountOrNull,
            String projectileOrNull,
            boolean replace) {
        customItemCreateFull(
                id, template, name, abilitiesWithTriggers,
                damageOrNull, rangeOrNull, cooldownOrNull, gearAttackOrNull,
                effectOrNull, radiusOrNull, amountOrNull, projectileOrNull,
                null, null, false, false, replace);
    }

    public static void customItemCreateFull(
            String id,
            String template,
            String name,
            java.util.Map<String, String> abilitiesWithTriggers,
            String damageOrNull,
            String rangeOrNull,
            String cooldownOrNull,
            String gearAttackOrNull,
            String effectOrNull,
            String radiusOrNull,
            String amountOrNull,
            String projectileOrNull,
            String durationTicksOrNull,
            String amplifierOrNull,
            boolean replace) {
        customItemCreateFull(
                id, template, name, abilitiesWithTriggers,
                damageOrNull, rangeOrNull, cooldownOrNull, gearAttackOrNull,
                effectOrNull, radiusOrNull, amountOrNull, projectileOrNull,
                durationTicksOrNull, amplifierOrNull, false, false, replace);
    }

    public static void customItemCreateFull(
            String id,
            String template,
            String name,
            java.util.Map<String, String> abilitiesWithTriggers,
            String damageOrNull,
            String rangeOrNull,
            String cooldownOrNull,
            String gearAttackOrNull,
            String effectOrNull,
            String radiusOrNull,
            String amountOrNull,
            String projectileOrNull,
            String durationTicksOrNull,
            String amplifierOrNull,
            boolean glow,
            boolean unbreakable,
            boolean replace) {
        customItemCreateFull(
                id, template, name, abilitiesWithTriggers,
                damageOrNull, rangeOrNull, cooldownOrNull, gearAttackOrNull,
                effectOrNull, radiusOrNull, amountOrNull, projectileOrNull,
                durationTicksOrNull, amplifierOrNull, glow, unbreakable, null, replace);
    }

    public static void customItemCreateFull(
            String id,
            String template,
            String name,
            java.util.Map<String, String> abilitiesWithTriggers,
            String damageOrNull,
            String rangeOrNull,
            String cooldownOrNull,
            String gearAttackOrNull,
            String effectOrNull,
            String radiusOrNull,
            String amountOrNull,
            String projectileOrNull,
            String durationTicksOrNull,
            String amplifierOrNull,
            boolean glow,
            boolean unbreakable,
            String enchantsCompactOrNull,
            boolean replace) {
        customItemCreateFull(
                id, template, name, abilitiesWithTriggers,
                damageOrNull, rangeOrNull, cooldownOrNull, gearAttackOrNull,
                effectOrNull, radiusOrNull, amountOrNull, projectileOrNull,
                durationTicksOrNull, amplifierOrNull, glow, false, unbreakable, enchantsCompactOrNull,
                null, null, null, true, replace);
    }

    public static void customItemCreateFull(
            String id,
            String template,
            String name,
            java.util.Map<String, String> abilitiesWithTriggers,
            String damageOrNull,
            String rangeOrNull,
            String cooldownOrNull,
            String gearAttackOrNull,
            String effectOrNull,
            String radiusOrNull,
            String amountOrNull,
            String projectileOrNull,
            String durationTicksOrNull,
            String amplifierOrNull,
            boolean glow,
            boolean rainbow,
            boolean unbreakable,
            String enchantsCompactOrNull,
            String soundOrNull,
            String particleOrNull,
            String countOrNull,
            boolean fxEnabled,
            boolean replace) {
        StringBuilder sb = new StringBuilder();
        sb.append("yapitems create ").append(id)
                .append(" --template ").append(template);
        appendCreateName(sb, name == null || name.isBlank() ? id : name);
        if (abilitiesWithTriggers != null && !abilitiesWithTriggers.isEmpty()) {
            StringJoiner abilities = new StringJoiner(",");
            for (var e : abilitiesWithTriggers.entrySet()) {
                String type = e.getKey();
                if (type == null || type.isBlank() || "none".equalsIgnoreCase(type)) {
                    continue;
                }
                String trigger = e.getValue() == null || e.getValue().isBlank() ? "together" : e.getValue();
                abilities.add(type.trim().toLowerCase(Locale.ROOT) + ":" + trigger.trim().toLowerCase(Locale.ROOT));
            }
            if (abilities.length() > 0) {
                sb.append(" --abilities ").append(abilities);
            }
        }
        if (damageOrNull != null && !damageOrNull.isBlank()) {
            String dmg = damageOrNull.trim();
            if ("kill".equalsIgnoreCase(dmg) || "instakill".equalsIgnoreCase(dmg)
                    || "instant_kill".equalsIgnoreCase(dmg) || "instant-kill".equalsIgnoreCase(dmg)) {
                sb.append(" --damage -1");
            } else {
                sb.append(" --damage ").append(dmg);
            }
        }
        if (rangeOrNull != null && !rangeOrNull.isBlank()) {
            sb.append(" --range ").append(rangeOrNull);
        }
        if (cooldownOrNull != null && !cooldownOrNull.isBlank()) {
            sb.append(" --cooldown ").append(cooldownOrNull);
        }
        if (gearAttackOrNull != null && !gearAttackOrNull.isBlank() && !"0".equals(gearAttackOrNull)) {
            sb.append(" --gear-attack ").append(gearAttackOrNull);
        }
        if (effectOrNull != null && !effectOrNull.isBlank()) {
            sb.append(" --effect ").append(effectOrNull);
        }
        if (radiusOrNull != null && !radiusOrNull.isBlank()) {
            sb.append(" --radius ").append(radiusOrNull);
        }
        if (amountOrNull != null && !amountOrNull.isBlank()) {
            sb.append(" --amount ").append(amountOrNull);
        }
        if (projectileOrNull != null && !projectileOrNull.isBlank()) {
            sb.append(" --projectile ").append(projectileOrNull);
        }
        if (durationTicksOrNull != null && !durationTicksOrNull.isBlank()) {
            sb.append(" --duration ").append(durationTicksOrNull);
        }
        if (amplifierOrNull != null && !amplifierOrNull.isBlank()) {
            sb.append(" --amplifier ").append(amplifierOrNull);
        }
        if (!fxEnabled) {
            sb.append(" --no-fx");
        }
        if (soundOrNull != null && !soundOrNull.isBlank()) {
            sb.append(" --sound ").append(soundOrNull.trim().toUpperCase(Locale.ROOT));
        }
        if (particleOrNull != null && !particleOrNull.isBlank()) {
            sb.append(" --particle ").append(particleOrNull.trim().toUpperCase(Locale.ROOT));
        }
        if (countOrNull != null && !countOrNull.isBlank() && !"DEFAULT".equalsIgnoreCase(countOrNull)) {
            sb.append(" --count ").append(countOrNull.trim());
        }
        if (glow) {
            sb.append(" --glow");
        } else if (replace) {
            sb.append(" --no-glow");
        }
        if (rainbow) {
            sb.append(" --rainbow");
        } else if (replace) {
            sb.append(" --no-rainbow");
        }
        if (unbreakable) {
            sb.append(" --unbreakable");
        } else if (replace) {
            sb.append(" --no-unbreakable");
        }
        if (enchantsCompactOrNull != null && !enchantsCompactOrNull.isBlank()) {
            sb.append(" --enchants ").append(enchantsCompactOrNull.trim().toLowerCase(Locale.ROOT));
        } else if (replace) {
            sb.append(" --no-enchants");
        }
        if (isFurnitureTemplate(template)) {
            sb.append(" --furniture");
        }
        if (replace) {
            sb.append(" --replace");
        }
        run(sb.toString());
    }

    /** Prefer compact --name; use --nameb64 when spaces / odd chars would break chat_command. */
    private static void appendCreateName(StringBuilder sb, String name) {
        String n = name == null ? "" : name.replace('\n', ' ').replace('\r', ' ').trim();
        if (n.isEmpty()) {
            return;
        }
        if (n.matches("[A-Za-z0-9_&]+")) {
            sb.append(" --name ").append(n);
            return;
        }
        String b64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(n.getBytes(StandardCharsets.UTF_8));
        sb.append(" --nameb64 ").append(b64);
    }

    private static boolean isFurnitureTemplate(String template) {
        if (template == null || template.isBlank()) {
            return false;
        }
        String t = template.trim().toLowerCase(Locale.ROOT);
        return t.equals("prop") || t.equals("furniture") || t.startsWith("prop_");
    }

    public static void customItemDelete(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        runFmt("yapitems delete %s", itemId.trim().toLowerCase(Locale.ROOT));
    }

    public static void customItemCooldown(String itemId, String duration) {
        runFmt("yapitems cooldown %s %s", itemId, duration);
    }

    public static void money(int amount, String playerOrNull) {
        if (playerOrNull != null && !playerOrNull.isBlank()) {
            runFmt("yapadmin money %d %s", amount, playerOrNull);
        } else {
            runFmt("yapadmin money %d", amount);
        }
    }

    public static void spawnMob(String type, int amount, String playerOrNull) {
        if (playerOrNull != null && !playerOrNull.isBlank()) {
            runFmt("yapadmin spawnmob %s %d %s", type, amount, playerOrNull);
        } else {
            runFmt("yapadmin spawnmob %s %d", type, amount);
        }
    }

    public static void troll(String type, String player) {
        runFmt("yapadmin troll %s %s", type, player);
    }

    public static void mod(String verb, String player, String reason) {
        String r = reason == null || reason.isBlank() ? "Staff action" : reason;
        runFmt("yapadmin %s %s %s", verb, player, r);
    }

    public static void protectLookupUser(String player) {
        runFmt("yapprotect lookup user %s", player);
    }

    public static List<String> onlineNames(String filter) {
        Minecraft minecraft = Minecraft.getInstance();
        List<String> names = new ArrayList<>();
        if (minecraft == null || minecraft.getConnection() == null) {
            return names;
        }
        String needle = filter == null ? "" : filter.toLowerCase(Locale.ROOT).trim();
        for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
            String name = info.getProfile().name();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (!needle.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            names.add(name);
        }
        names.sort(Comparator.comparing(n -> n.toLowerCase(Locale.ROOT)));
        return names;
    }

    public static String requireTarget() {
        StaffSession session = YapStaffClient.session();
        if (!session.hasTarget()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.literal("§cSelect a player first."));
            }
            return null;
        }
        return session.targetName();
    }
}
