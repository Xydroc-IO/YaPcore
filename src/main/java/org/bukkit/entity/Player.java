package org.bukkit.entity;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.permissions.Permissible;

import java.util.UUID;

/**
 * Player handle. Inventory / teleport / world mutations are SYNC-bridged.
 */
public interface Player extends Entity, OfflinePlayer, Permissible, Audience {

    @Override
    UUID getUniqueId();

    String getDisplayName();

    void setDisplayName(String name);

    Location getLocation();

    void teleport(Location location);

    PlayerInventory getInventory();

    Inventory getEnderChest();

    @Override
    boolean isOnline();

    void giveExp(int amount);

    void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut);

    void closeInventory();

    void openInventory(Inventory inventory);

    InventoryView getOpenInventory();

    World getWorld();

    void playSound(Location location, Sound sound, float volume, float pitch);

    void kick(Component message);

    void kickPlayer(String message);

    GameMode getGameMode();

    void setGameMode(GameMode mode);

    void sendPluginMessage(org.bukkit.plugin.Plugin source, String channel, byte[] message);

    default ItemStack getItemInHand() {
        return getInventory().getItemInMainHand();
    }

    @Override
    default void sendMessage(Component message) {
        sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacySection().serialize(message));
    }
}
