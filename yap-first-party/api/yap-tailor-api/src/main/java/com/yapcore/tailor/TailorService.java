package com.yapcore.tailor;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** First-party skin / wardrobe API (FabricTailor-class). */
public interface TailorService {

    /** Download, validate, persist, and apply a skin URL to the player. */
    ActiveSkin applySkin(Player player, String skinUrl) throws TailorException;

    /** Apply a skin to an offline UUID (persist only; online players are refreshed). */
    ActiveSkin applySkin(UUID playerUuid, String skinUrl) throws TailorException;

    /** Clear custom skin and restore default Mojang look where possible. */
    void clearSkin(Player player) throws TailorException;

    void clearSkin(UUID playerUuid) throws TailorException;

    /** Set slim / wide model for the active skin. */
    ActiveSkin setModel(Player player, SkinModel model) throws TailorException;

    ActiveSkin setModel(UUID playerUuid, SkinModel model) throws TailorException;

    /** Set or clear cape URL on the active skin. Pass null or blank to clear. */
    ActiveSkin setCape(Player player, String capeUrl) throws TailorException;

    ActiveSkin setCape(UUID playerUuid, String capeUrl) throws TailorException;

    List<WardrobeSlot> listWardrobe(UUID playerUuid) throws TailorException;

    /** Save current active skin into a named wardrobe slot (creates or replaces by name). */
    WardrobeSlot saveWardrobeSlot(UUID playerUuid, String name) throws TailorException;

    void deleteWardrobeSlot(UUID playerUuid, long slotId) throws TailorException;

    WardrobeSlot renameSlot(UUID playerUuid, long slotId, String newName) throws TailorException;

    /** Apply a wardrobe slot as the active skin. */
    ActiveSkin applyWardrobeSlot(Player player, long slotId) throws TailorException;

    Optional<ActiveSkin> getActiveSkin(UUID playerUuid) throws TailorException;

    /** Re-apply active skin to an online player (join / refresh). */
    void refreshPlayer(Player player) throws TailorException;
}
