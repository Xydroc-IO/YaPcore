package com.sk89q.worldedit;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.EditSession;
import com.sk89q.worldedit.session.SessionManager;
import org.bukkit.World;

/**
 * Minimal EngineHub-compatible facade backed by YaPWorld (Folia-safe).
 * Not a full WorldEdit implementation — common soft-deps only.
 */
public final class WorldEdit {

    private static WorldEdit instance = new WorldEdit();
    private final SessionManager sessions = new SessionManager();
    private EditSessionFactory editSessionFactory = new EditSessionFactory();

    private WorldEdit() {
    }

    public static WorldEdit getInstance() {
        return instance;
    }

    /** Used by YaPWorld / WorldEdit shim on enable. */
    public static void bind(WorldEdit we) {
        if (we != null) {
            instance = we;
        }
    }

    public SessionManager getSessionManager() {
        return sessions;
    }

    public EditSessionFactory getEditSessionFactory() {
        return editSessionFactory;
    }

    public void setEditSessionFactory(EditSessionFactory factory) {
        if (factory != null) {
            this.editSessionFactory = factory;
        }
    }

    public EditSession newEditSession(World world) {
        return editSessionFactory.getEditSession(BukkitAdapter.adapt(world), -1);
    }

    public static final class EditSessionFactory {
        public EditSession getEditSession(com.sk89q.worldedit.world.World world, int maxBlocks) {
            return new EditSession(world, maxBlocks);
        }
    }
}
