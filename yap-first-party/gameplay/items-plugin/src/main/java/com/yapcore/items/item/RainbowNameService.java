package com.yapcore.items.item;

import com.yapcore.items.ItemsKeys;
import com.yapcore.items.ItemsPlugin;
import com.yapcore.sched.YapSched;
import com.yapcore.sched.YapTask;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Animates display names on custom items tagged with {@code rainbow: true}.
 * Skips the held hotbar slot so re-setting the stack does not bob the hand item.
 * Mesh rainbow tint is handled client-side by yap-visuals.
 */
public final class RainbowNameService {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final char[] COLORS = {'c', '6', 'e', 'a', 'b', 'd', '5'};

    private final ItemsPlugin plugin;
    private final ItemsKeys keys;
    private final ItemRegistry registry;
    private final AtomicInteger phase = new AtomicInteger();
    private YapTask task;

    public RainbowNameService(ItemsPlugin plugin, ItemsKeys keys, ItemRegistry registry) {
        this.plugin = plugin;
        this.keys = keys;
        this.registry = registry;
    }

    public void start() {
        stop();
        task = YapSched.globalTimer(plugin, this::tick, 10L, 10L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (!registry.hasRainbowItems()) {
            return;
        }
        int p = phase.getAndIncrement();
        for (Player player : Bukkit.getOnlinePlayers()) {
            YapSched.entity(plugin, player, () -> refresh(player, p));
        }
    }

    private void refresh(Player player, int phaseValue) {
        PlayerInventory inv = player.getInventory();
        int held = inv.getHeldItemSlot();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (i == held) {
                continue;
            }
            if (refreshSlot(contents[i], phaseValue)) {
                inv.setItem(i, contents[i]);
            }
        }
    }

    private boolean refreshSlot(ItemStack stack, int phaseValue) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte flag = meta.getPersistentDataContainer().get(keys.rainbow(), PersistentDataType.BYTE);
        if (flag == null || flag == 0) {
            return false;
        }
        String id = meta.getPersistentDataContainer().get(keys.itemId(), PersistentDataType.STRING);
        if (id == null || id.isBlank()) {
            return false;
        }
        var defOpt = registry.get(id);
        if (defOpt.isEmpty() || !defOpt.get().rainbow()) {
            return false;
        }
        String colored = colorize(stripCodes(defOpt.get().name()), phaseValue);
        meta.displayName(LEGACY.deserialize(colored));
        stack.setItemMeta(meta);
        return true;
    }

    public static String colorize(String text, int phaseValue) {
        String plain = stripCodes(text == null ? "" : text);
        if (plain.isEmpty()) {
            return "&f";
        }
        StringBuilder sb = new StringBuilder(plain.length() * 3);
        int idx = 0;
        for (int i = 0; i < plain.length(); i++) {
            char ch = plain.charAt(i);
            if (ch == ' ') {
                sb.append(' ');
                continue;
            }
            sb.append('&').append(COLORS[Math.floorMod(phaseValue + idx, COLORS.length)]).append(ch);
            idx++;
        }
        return sb.toString();
    }

    static String stripCodes(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String s = raw.replace('§', '&');
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if ("0123456789abcdefklmnorABCDEFKLMNOR".indexOf(n) >= 0) {
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }
}
