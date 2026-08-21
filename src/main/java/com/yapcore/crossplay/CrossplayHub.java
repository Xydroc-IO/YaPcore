package com.yapcore.crossplay;

import com.yapcore.client.ClientEdition;
import com.yapcore.client.ClientSession;
import com.yaplabs.yapengine.YapEngine;
import com.yaplabs.yapengine.core.spatial.BitwiseQuadrantIndex;
import com.yaplabs.yapengine.core.spatial.SpatialQuadrant;
import com.yaplabs.yapengine.sequencing.SequenceToken;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Geyser-class crossplay hub: Java + Bedrock land in one shared world roster
 * and the same YapEngine spatial partition.
 */
public final class CrossplayHub {

    private static final Logger LOG = Logger.getLogger("YaPcore.Crossplay");

    private final YapEngine engine;
    private final GeyserStyleTranslator translator = new GeyserStyleTranslator();
    private final ConcurrentHashMap<String, UnifiedPlayer> byName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UnifiedPlayer> bySession = new ConcurrentHashMap<>();

    public CrossplayHub(YapEngine engine) {
        this.engine = Objects.requireNonNull(engine);
    }

    public GeyserStyleTranslator translator() {
        return translator;
    }

    public UnifiedPlayer join(ClientSession session) {
        Objects.requireNonNull(session);
        UnifiedPlayer player = new UnifiedPlayer(session);
        player.setPosition(8, 64, -8);
        player.setDimension("overworld");
        byName.put(player.getUsername().toLowerCase(), player);
        bySession.put(player.getSessionId(), player);

        engine.gameCore().getPartition().registerEntity(
                player.getUsername(), player.getBlockX(), player.getBlockZ());
        SpatialQuadrant q = BitwiseQuadrantIndex.fromBlock(player.getBlockX(), player.getBlockZ());
        SequenceToken token = SequenceToken.next("crossplay:" + player.getUsername());
        engine.gameCore().dispatch(
                player.getBlockX(),
                player.getBlockZ(),
                token,
                "crossplay-join:" + player.getUsername(),
                () -> LOG.info("Shared-world spawn " + player + " quadrant=" + q)
        );

        translator.onJoin(player);
        LOG.info("Crossplay join " + player.getUsername()
                + " via " + player.getEdition()
                + " → shared overworld (same map as all editions)");
        return player;
    }

    public void leave(ClientSession session) {
        if (session == null) {
            return;
        }
        UnifiedPlayer removed = bySession.remove(session.getSessionId());
        if (removed != null) {
            byName.remove(removed.getUsername().toLowerCase());
            engine.gameCore().getPartition().unregisterEntity(removed.getUsername());
            translator.onLeave(removed);
            LOG.info("Crossplay leave " + removed.getUsername());
        }
    }

    public void handleAction(ClientSession session, String action, Map<String, String> payload) {
        get(session.getUsername()).ifPresent(player ->
                translator.translate(player, action, payload == null ? Map.of() : payload, engine));
    }

    public Optional<UnifiedPlayer> get(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byName.get(username.toLowerCase()));
    }

    public Collection<UnifiedPlayer> all() {
        return byName.values();
    }

    public long countEdition(ClientEdition edition) {
        return byName.values().stream().filter(p -> p.getEdition() == edition).count();
    }

    public int size() {
        return byName.size();
    }

    public void clear() {
        byName.clear();
        bySession.clear();
    }
}
