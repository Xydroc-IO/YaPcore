package com.yapcore.bedrockblocks;

import com.yapcore.sched.YapSched;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * End-to-end Folia placement for all 24 catalog Bedrock port blocks.
 * <p>
 * Carrier visuals use {@link ItemDisplay} (Paper {@code BlockDisplay} only accepts
 * block states; item CMD models require an item stack on an ItemDisplay).
 */
public final class PortBlockService implements PortBlocksService {

    private final JavaPlugin plugin;
    private final PortBlockCatalog catalog;
    private final PortBlockKeys keys;

    public PortBlockService(JavaPlugin plugin, PortBlockCatalog catalog, PortBlockKeys keys) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.keys = keys;
    }

    public PortBlockCatalog catalog() {
        return catalog;
    }

    public PortBlockKeys keys() {
        return keys;
    }

    @Override
    public boolean place(Block block, String portIdOrShort, BlockFace face) {
        Optional<PortBlockDefinition> opt = catalog.resolve(portIdOrShort);
        if (opt.isEmpty()) {
            return false;
        }
        PortBlockDefinition def = opt.get();
        BlockFace attach = face == null ? BlockFace.NORTH : face;
        Location regionLoc = block.getLocation();
        YapSched.region(plugin, regionLoc, () -> placeNow(block, def, attach));
        return true;
    }

    /** Place synchronously (caller must already be on the region thread). */
    public void placeNow(Block block, PortBlockDefinition def, BlockFace face) {
        clearPortAt(block, false);
        switch (def.placement()) {
            case NATIVE_LIGHT -> placeLight(block, def);
            case NATIVE_STONECUTTER -> placeStonecutter(block, def);
            case NATIVE_FRAME -> placeFrame(block, def, face);
            case CARRIER_DISPLAY -> placeCarrier(block, def);
        }
    }

    private void placeLight(Block block, PortBlockDefinition def) {
        block.setType(Material.LIGHT, false);
        if (block.getBlockData() instanceof Light light) {
            light.setLevel(Math.max(0, Math.min(15, def.lightLevel())));
            block.setBlockData(light, false);
        }
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().spawn(loc, Marker.class, marker -> {
            tagPort(marker.getPersistentDataContainer(), def.jePortId());
            marker.setPersistent(true);
        });
    }

    private void placeStonecutter(Block block, PortBlockDefinition def) {
        block.setType(Material.STONECUTTER, false);
        if (block.getState() instanceof TileState tile) {
            tagPort(tile.getPersistentDataContainer(), def.jePortId());
            tile.update(true, false);
        }
    }

    private void placeFrame(Block block, PortBlockDefinition def, BlockFace face) {
        BlockFace attach = sanitizeFrameFace(face);
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);
        Class<? extends ItemFrame> type = def.glowFrame()
                ? org.bukkit.entity.GlowItemFrame.class
                : ItemFrame.class;
        block.getWorld().spawn(loc, type, frame -> {
            frame.setFacingDirection(attach, true);
            frame.setFixed(true);
            frame.setVisible(true);
            frame.setPersistent(true);
            tagPort(frame.getPersistentDataContainer(), def.jePortId());
        });
    }

    private void placeCarrier(Block block, PortBlockDefinition def) {
        block.setType(Material.BARRIER, false);
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);
        ItemStack visual = buildDisplayItem(def);
        block.getWorld().spawn(loc, ItemDisplay.class, display -> {
            display.setItemStack(visual);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(),
                    new Vector3f(1.001f, 1.001f, 1.001f),
                    new AxisAngle4f()));
            display.setPersistent(true);
            tagPort(display.getPersistentDataContainer(), def.jePortId());
        });
    }

    private static BlockFace sanitizeFrameFace(BlockFace face) {
        return switch (face) {
            case UP, DOWN, NORTH, SOUTH, EAST, WEST -> face;
            default -> BlockFace.NORTH;
        };
    }

    @Override
    public boolean breakPort(Block block, boolean dropItem) {
        Optional<PortBlockDefinition> at = resolveAt(block);
        if (at.isEmpty()) {
            return false;
        }
        Location regionLoc = block.getLocation();
        YapSched.region(plugin, regionLoc, () -> clearPortAt(block, dropItem));
        return true;
    }

    /** Clear port synchronously (region thread). */
    public void clearPortAt(Block block, boolean dropItem) {
        Optional<PortBlockDefinition> at = resolveAt(block);
        boolean ours = at.isPresent();
        removeAttachedEntities(block);
        if (ours) {
            Material type = block.getType();
            if (type == Material.LIGHT || type == Material.STONECUTTER || type == Material.BARRIER) {
                block.setType(Material.AIR, false);
            }
        }
        if (dropItem && at.isPresent()) {
            ItemStack stack = giveItem(at.get().jePortId(), 1);
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), stack);
        }
    }

    private void removeAttachedEntities(Block block) {
        for (Entity ent : findPortEntities(block)) {
            ent.remove();
        }
    }

    private Collection<Entity> findPortEntities(Block block) {
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        return block.getWorld().getNearbyEntities(center, 0.75, 0.75, 0.75, ent ->
                readPortId(ent.getPersistentDataContainer()).isPresent()
                        && (ent instanceof ItemDisplay
                        || ent instanceof ItemFrame
                        || ent instanceof Marker));
    }

    @Override
    public ItemStack giveItem(String portIdOrShort, int amount) {
        PortBlockDefinition def = catalog.resolve(portIdOrShort)
                .orElseThrow(() -> new IllegalArgumentException("Unknown port: " + portIdOrShort));
        return buildGiveItem(def, Math.max(1, amount));
    }

    @Override
    public boolean giveItem(Player player, String portIdOrShort, int amount) {
        Optional<PortBlockDefinition> opt = catalog.resolve(portIdOrShort);
        if (opt.isEmpty()) {
            return false;
        }
        ItemStack stack = buildGiveItem(opt.get(), Math.max(1, amount));
        var leftover = player.getInventory().addItem(stack);
        leftover.values().forEach(left ->
                player.getWorld().dropItemNaturally(player.getLocation(), left));
        return true;
    }

    private ItemStack buildGiveItem(PortBlockDefinition def, int amount) {
        PortBlockCatalog.ItemModel model = catalog.itemModel(def.shortName())
                .orElse(new PortBlockCatalog.ItemModel(Material.PAPER, 7001));
        ItemStack stack = new ItemStack(model.material(), amount);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(def.shortName(), NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text(def.jePortId(), NamedTextColor.DARK_GRAY),
                Component.text("Bedrock catalog port", NamedTextColor.GRAY)));
        meta.setCustomModelData(model.customModelData());
        tagPort(meta.getPersistentDataContainer(), def.jePortId());
        stack.setItemMeta(meta);
        return stack;
    }

    /** Display item for carrier ItemDisplay (CMD item model under yapbedrock). */
    private ItemStack buildDisplayItem(PortBlockDefinition def) {
        PortBlockCatalog.ItemModel model = catalog.itemModel(def.shortName())
                .orElse(new PortBlockCatalog.ItemModel(Material.PAPER, 7001));
        ItemStack stack = new ItemStack(model.material(), 1);
        ItemMeta meta = stack.getItemMeta();
        meta.setCustomModelData(model.customModelData());
        tagPort(meta.getPersistentDataContainer(), def.jePortId());
        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    public Optional<PortBlockDefinition> resolve(String portIdOrShort) {
        return catalog.resolve(portIdOrShort);
    }

    @Override
    public Optional<PortBlockDefinition> resolveAt(Block block) {
        for (Entity ent : findPortEntities(block)) {
            Optional<String> id = readPortId(ent.getPersistentDataContainer());
            if (id.isPresent()) {
                return catalog.resolve(id.get());
            }
        }
        if (block.getState() instanceof TileState tile) {
            Optional<String> id = readPortId(tile.getPersistentDataContainer());
            if (id.isPresent()) {
                return catalog.resolve(id.get());
            }
        }
        return Optional.empty();
    }

    @Override
    public List<PortBlockDefinition> list() {
        return catalog.all();
    }

    public Optional<String> portIdOfItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        return readPortId(stack.getItemMeta().getPersistentDataContainer());
    }

    void tagPort(PersistentDataContainer pdc, String jePortId) {
        pdc.set(keys.portId(), PersistentDataType.STRING, jePortId);
    }

    Optional<String> readPortId(PersistentDataContainer pdc) {
        if (!pdc.has(keys.portId(), PersistentDataType.STRING)) {
            return Optional.empty();
        }
        return Optional.ofNullable(pdc.get(keys.portId(), PersistentDataType.STRING));
    }
}
