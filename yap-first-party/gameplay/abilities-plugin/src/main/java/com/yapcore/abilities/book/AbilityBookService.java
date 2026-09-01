package com.yapcore.abilities.book;

import com.yapcore.abilities.AbilityCategory;
import com.yapcore.abilities.AbilityDefinition;
import com.yapcore.abilities.AbilityService;
import com.yapcore.abilities.bar.AbilityBarService;
import com.yapcore.bedrock.ui.BedrockUiServices;
import com.yapcore.mmo.SkillProgress;
import com.yapcore.mmo.SkillService;
import com.yapcore.mmo.SkillServices;
import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

public final class AbilityBookService {

    private final JavaPlugin plugin;
    private final AbilityBookConfig config;
    private final AbilityBookKeys keys;
    private final AbilityBookMenu menu;
    private final AbilityBookBedrockUi bedrockUi;
    private final AbilityBookPlayerStore playerStore;
    private final AbilityBarService bar;
    private final AbilityService abilities;

    public AbilityBookService(
            JavaPlugin plugin,
            AbilityBookConfig config,
            AbilityBookKeys keys,
            AbilityBookMenu menu,
            AbilityBookBedrockUi bedrockUi,
            AbilityBookPlayerStore playerStore,
            AbilityBarService bar,
            AbilityService abilities
    ) {
        this.plugin = plugin;
        this.config = config;
        this.keys = keys;
        this.menu = menu;
        this.bedrockUi = bedrockUi;
        this.playerStore = playerStore;
        this.bar = bar;
        this.abilities = abilities;
    }

    public AbilityBookConfig config() {
        return config;
    }

    public AbilityBookKeys keys() {
        return keys;
    }

    public AbilityBookMenu menu() {
        return menu;
    }

    public AbilityBookPlayerStore playerStore() {
        return playerStore;
    }

    public void open(Player player) {
        open(player, null, 1);
    }

    public void open(Player player, String categoryRaw, int page) {
        if (!config.enabled()) {
            player.sendMessage("§cAbility book is disabled.");
            return;
        }
        if (!player.hasPermission("yapabilities.use")) {
            player.sendMessage("§cNo permission.");
            return;
        }
        AbilityCategory category = parseCategory(categoryRaw);
        if (BedrockUiServices.find().map(b -> b.isBedrock(player)).orElse(false)) {
            bedrockUi.open(player);
            return;
        }
        menu.open(player, category, page);
    }

    public ItemStack createTome() {
        return AbilityBookItems.createTome(plugin, config, keys);
    }

    public void giveTome(Player player, boolean markReceived) {
        if (!config.tomeEnabled()) {
            player.sendMessage("§cAbility tomes are disabled.");
            return;
        }
        ItemStack tome = createTome();
        player.getInventory().addItem(tome).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        if (markReceived) {
            playerStore.markTomeReceived(player.getUniqueId());
        }
        player.sendMessage("§aYou received an §dAbility Tome§a.");
    }

    public void maybeGiveFirstJoinTome(Player player) {
        if (!config.tomeEnabled() || !config.giveTomeOnFirstJoin()) {
            return;
        }
        if (playerStore.hasReceivedTome(player.getUniqueId())) {
            return;
        }
        giveTome(player, true);
    }

    public void handleNavClick(Player player, AbilityBookHolder holder, String action) {
        if (action == null) {
            return;
        }
        if ("close".equals(action)) {
            player.closeInventory();
            return;
        }
        if ("clear".equals(action)) {
            if (!player.hasPermission("yapabilities.bar")) {
                player.sendMessage("§cNo permission.");
                return;
            }
            bar.store().clear(player.getUniqueId());
            bar.syncBar(player);
            holder.setPendingAbilityId(null);
            player.sendMessage("§7Cleared all ability bar bindings.");
            menu.refresh(player, holder);
            return;
        }
        if (action.startsWith("page:")) {
            String dir = action.substring("page:".length());
            if ("prev".equals(dir)) {
                holder.setPage(Math.max(1, holder.page() - 1));
            } else if ("next".equals(dir)) {
                holder.setPage(holder.page() + 1);
            }
            menu.refresh(player, holder);
            return;
        }
        if (action.startsWith("cat:")) {
            String cat = action.substring("cat:".length());
            if ("ALL".equalsIgnoreCase(cat)) {
                holder.setCategoryFilter(null);
            } else {
                holder.setCategoryFilter(AbilityCategory.parse(cat));
            }
            holder.setPage(1);
            holder.setPendingAbilityId(null);
            menu.refresh(player, holder);
        }
    }

    public void handleAbilityClick(
            Player player,
            AbilityBookHolder holder,
            AbilityDefinition ability,
            boolean shiftClick,
            boolean rightClick,
            Collection<SkillProgress> skills
    ) {
        if (ability == null) {
            return;
        }
        if (!AbilityUnlocks.isUnlocked(player, ability, skills)) {
            player.sendMessage("§cLocked: " + AbilityUnlocks.requirementsText(ability, skills));
            return;
        }
        if (rightClick) {
            player.sendMessage("§6" + ability.displayName() + " §7(" + ability.id() + ")");
            player.sendMessage("§7" + AbilityUnlocks.requirementsText(ability, skills));
            if (ability.cooldownTicks() > 0) {
                player.sendMessage("§7Cooldown: §f" + (ability.cooldownTicks() / 20.0) + "s");
            }
            return;
        }
        if (shiftClick) {
            bindToFirstEmpty(player, holder, ability.id());
            return;
        }
        holder.setPendingAbilityId(ability.id());
        player.sendActionBar(Component.text("§eSelected §f" + ability.displayName()
                + " §7— click a bar slot (keys " + bar.config().firstKey()
                + "–" + bar.config().lastKey() + ")"));
        menu.refresh(player, holder);
    }

    public void handleBarSlotClick(
            Player player,
            AbilityBookHolder holder,
            int barIndex,
            boolean rightClick,
            String abilityFromCursor
    ) {
        if (!player.hasPermission("yapabilities.bar")) {
            player.sendMessage("§cNo permission.");
            return;
        }
        if (rightClick) {
            bar.bind(player, barIndex + 1, "", true);
            holder.setPendingAbilityId(null);
            menu.refresh(player, holder);
            return;
        }
        String abilityId = abilityFromCursor;
        if (abilityId == null || abilityId.isBlank()) {
            abilityId = holder.pendingAbilityId();
        }
        if (abilityId == null || abilityId.isBlank()) {
            player.sendActionBar(Component.text("§7Select an ability first, or drag one here."));
            return;
        }
        bindAbility(player, holder, barIndex, abilityId);
    }

    public void handleDragToBar(Player player, AbilityBookHolder holder, String abilityId, int barIndex) {
        if (!player.hasPermission("yapabilities.bar")) {
            player.sendMessage("§cNo permission.");
            return;
        }
        if (abilityId == null || abilityId.isBlank()) {
            return;
        }
        bindAbility(player, holder, barIndex, abilityId);
    }

    private void bindToFirstEmpty(Player player, AbilityBookHolder holder, String abilityId) {
        if (!player.hasPermission("yapabilities.bar")) {
            player.sendMessage("§cNo permission.");
            return;
        }
        for (int i = 0; i < bar.config().slotCount(); i++) {
            String existing = bar.store().get(player.getUniqueId(), i);
            if (existing == null || existing.isBlank()) {
                bindAbility(player, holder, i, abilityId);
                return;
            }
        }
        player.sendMessage("§cAll bar slots full — right-click a slot to clear one.");
    }

    private void bindAbility(Player player, AbilityBookHolder holder, int barIndex, String abilityId) {
        if (abilities.get(abilityId).isEmpty()) {
            player.sendMessage("§cUnknown ability.");
            return;
        }
        SkillService skills = SkillServices.find().orElse(null);
        if (skills != null) {
            skills.getAll(player.getUniqueId()).thenAccept(all -> YapSched.entity(plugin, player, () -> {
                abilities.get(abilityId).ifPresent(def -> {
                    if (!AbilityUnlocks.isUnlocked(player, def, all)) {
                        player.sendMessage("§cThat ability is locked.");
                        return;
                    }
                    bar.bind(player, barIndex + 1, abilityId, true);
                    holder.setPendingAbilityId(null);
                    menu.refresh(player, holder);
                });
            }));
        } else {
            bar.bind(player, barIndex + 1, abilityId, true);
            holder.setPendingAbilityId(null);
            menu.refresh(player, holder);
        }
    }

    private static AbilityCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if ("all".equalsIgnoreCase(raw)) {
            return null;
        }
        return AbilityCategory.parse(raw);
    }
}
