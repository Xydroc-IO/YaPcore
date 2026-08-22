package com.yapcore.crossplay.bedrock;

import com.yapcore.crossplay.floodgate.FloodgateAuth;
import com.yapcore.crossplay.form.FormService;
import com.yapcore.crossplay.skin.SkinService;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Decodes Bedrock game batches → actions; builds login/spawn reply sequences.
 */
public final class BedrockGameplayBridge {

    private static final Logger LOG = Logger.getLogger("YaPcore.BedrockBridge");

    public record GameAction(String type, String username, Map<String, String> payload) {
    }

    private final BedrockSessionManager sessions;
    private final FloodgateAuth floodgate;
    private final SkinService skins;
    private final FormService forms;
    private final BedrockEntityTracker entities = new BedrockEntityTracker();
    private final BedrockInventoryAuthority inventory = new BedrockInventoryAuthority();
    private final BedrockContainerBridge containers = new BedrockContainerBridge();
    private final AtomicLong runtimeIds = new AtomicLong(1);
    private final Map<Long, Integer> chunkRadius = new ConcurrentHashMap<>();
    private final Map<Long, Long> runtimeByGuid = new ConcurrentHashMap<>();
    private final Map<Long, Integer> pendingProtocol = new ConcurrentHashMap<>();
    /** P4.5 — continuous Paper column stream (flat only via -Dyapcore.bedrock.flat-chunks). */
    private final BedrockColumnStreamer columns = new BedrockColumnStreamer();
    /** G.28 — last pushed Paper storage fingerprint per user (external /give detect). */
    private final ConcurrentHashMap<String, Long> inventoryFingerprint = new ConcurrentHashMap<>();
    private BiConsumer<Long, List<ByteBuf>> outbound = (guid, packets) -> {
    };
    private volatile BedrockPaperWorldSync paperWorld;

    public BedrockGameplayBridge(BedrockSessionManager sessions,
                                 FloodgateAuth floodgate,
                                 SkinService skins,
                                 FormService forms) {
        this.sessions = sessions;
        this.floodgate = floodgate;
        this.skins = skins;
        this.forms = forms;
    }

    public void setPaperWorld(BedrockPaperWorldSync paperWorld) {
        this.paperWorld = paperWorld;
        inventory.attachPaper(paperWorld);
        inventory.attachContainers(containers);
        containers.attachPaper(paperWorld);
        containers.attachInventory(inventory);
    }

    public BedrockEntityTracker entities() {
        return entities;
    }

    public BedrockContainerBridge containers() {
        return containers;
    }

    public void setOutbound(BiConsumer<Long, List<ByteBuf>> outbound) {
        this.outbound = outbound != null ? outbound : (g, p) -> {
        };
        entities.setBroadcast((except, packets) -> {
            for (Long guid : sessions.allGuids()) {
                if (except != null && except >= 0 && except.equals(guid)) {
                    continue;
                }
                this.outbound.accept(guid, copyPackets(packets));
            }
        });
        containers.setSender((username, pkt) -> {
            BedrockSessionManager.BedrockSession s = sessions.byUsername(username);
            if (s != null) {
                this.outbound.accept(s.guid(), List.of(pkt));
            } else {
                pkt.release();
            }
        });
    }

    private static List<ByteBuf> copyPackets(List<ByteBuf> packets) {
        List<ByteBuf> copy = new ArrayList<>(packets.size());
        for (ByteBuf p : packets) {
            copy.add(p.retainedDuplicate());
        }
        return copy;
    }

    public List<GameAction> onGameBatch(long guid, String address, ByteBuf batch) {
        List<GameAction> actions = new ArrayList<>();
        while (batch.isReadable()) {
            try {
                int len = BedrockPacketCodec.readUnsignedVarInt(batch);
                if (len <= 0 || batch.readableBytes() < len) {
                    break;
                }
                ByteBuf pkt = batch.readSlice(len);
                BedrockPacketCodec.Decoded decoded = BedrockPacketCodec.decode(pkt);
                handlePacket(guid, address, decoded, actions);
            } catch (Exception e) {
                LOG.fine("BE batch parse: " + e.getMessage());
                break;
            }
        }
        return actions;
    }

    private void handlePacket(long guid, String address, BedrockPacketCodec.Decoded decoded,
                              List<GameAction> actions) {
        BedrockPacketIds kind = BedrockPacketIds.byId(decoded.id());
        if (kind == null) {
            LOG.fine("BE unknown pkt id=" + decoded.id() + " guid=" + Long.toHexString(guid));
            return;
        }
        BedrockSessionManager.BedrockSession s = sessions.get(guid);
        String user = s != null ? s.username() : "BedrockPlayer";
        switch (kind) {
            case REQUEST_NETWORK_SETTINGS -> {
                int proto = 0;
                try {
                    ByteBuf b = decoded.body().duplicate();
                    if (b.readableBytes() >= 4) {
                        proto = b.readInt(); // i32 BE per minecraft-data
                    }
                } catch (Exception ignored) {
                    // default
                }
                if (proto != 0) {
                    pendingProtocol.put(guid, proto);
                }
                sendNetworkSettings(guid);
            }
            case LOGIN -> beginLogin(guid, address, decoded.body(), actions);
            case CLIENT_TO_SERVER_HANDSHAKE, RESOURCE_PACK_CLIENT_RESPONSE, SET_LOCAL_PLAYER_AS_INITIALIZED ->
                    sendSpawnSequence(guid, sessions.get(guid));
            case MOVE_PLAYER -> {
                var move = BedrockPacketCodec.tryDecodeMove(decoded.body());
                if (move != null) {
                    actions.add(moveAction(user, move.x(), move.y(), move.z(), move.yaw(), move.pitch()));
                    streamColumnsAround(guid, (int) move.x(), (int) move.y(), (int) move.z(), false);
                }
            }
            case PLAYER_AUTH_INPUT -> {
                var auth = BedrockPacketCodec.tryDecodeAuthInput(decoded.body());
                if (auth != null) {
                    Map<String, String> p = new HashMap<>();
                    p.put("x", Integer.toString((int) auth.x()));
                    p.put("y", Integer.toString((int) auth.y()));
                    p.put("z", Integer.toString((int) auth.z()));
                    p.put("yaw", Float.toString(auth.yaw()));
                    p.put("pitch", Float.toString(auth.pitch()));
                    p.put("tick", Long.toString(auth.tick()));
                    actions.add(new GameAction("MOVE", user, p));
                    Long runtime = runtimeByGuid.get(guid);
                    if (runtime != null) {
                        entities.move(runtime, auth.x(), auth.y(), auth.z(), auth.yaw(), auth.pitch());
                    }
                    streamColumnsAround(guid, (int) auth.x(), (int) auth.y(), (int) auth.z(), false);
                    maybePushPaperInventory(guid, user);
                    pushOpenContainerProgress(guid, user);
                    if ((auth.tick() & 7L) == 0L) {
                        mirrorPaperPlayers();
                    }
                }
            }
            case TEXT -> {
                var text = BedrockPacketCodec.tryDecodeText(decoded.body());
                if (text != null) {
                    actions.add(new GameAction("CHAT", user, Map.of("msg", text.message())));
                }
            }
            case PLAYER_ACTION -> {
                var act = BedrockPacketCodec.tryDecodePlayerAction(decoded.body());
                if (act != null && act.isBreakRelated()) {
                    actions.add(new GameAction("BREAK", user, Map.of(
                            "x", Integer.toString(act.x()),
                            "y", Integer.toString(act.y()),
                            "z", Integer.toString(act.z()),
                            "face", Integer.toString(act.face()),
                            "action", Integer.toString(act.action())
                    )));
                    sendBlockUpdate(guid, act.x(), act.y(), act.z(), 0);
                    resendColumn(guid, act.x() >> 4, act.z() >> 4);
                } else if (act != null) {
                    actions.add(new GameAction("PLACE", user, Map.of(
                            "x", Integer.toString(act.x()),
                            "y", Integer.toString(act.y()),
                            "z", Integer.toString(act.z()),
                            "face", Integer.toString(act.face()),
                            "action", Integer.toString(act.action())
                    )));
                    resendColumn(guid, act.x() >> 4, act.z() >> 4);
                } else {
                    actions.add(new GameAction("BREAK", user, Map.of("pkt", kind.name())));
                }
            }
            case INVENTORY_TRANSACTION -> {
                var tx = BedrockPacketCodec.tryDecodeInventoryTransaction(decoded.body());
                if (tx != null && tx.hasPos() && tx.likelyUseItemOn()) {
                    String block = paperBlockHint(tx.x(), tx.y(), tx.z());
                    int ctype = BedrockContainerBridge.typeForBlock(block);
                    if (block != null && (block.contains("CHEST") || block.contains("BARREL")
                            || block.contains("SHULKER") || block.contains("FURNACE")
                            || block.contains("SMOKER") || block.contains("BLAST")
                            || block.contains("ENCHANT") || block.contains("HOPPER")
                            || block.contains("CRAFTING") || block.contains("WORKBENCH"))) {
                        if (block.contains("CRAFTING") || block.contains("WORKBENCH")) {
                            ctype = BedrockContainerBridge.TYPE_WORKBENCH;
                        }
                        containers.open(user, ctype, tx.x(), tx.y(), tx.z());
                        if (ctype == BedrockContainerBridge.TYPE_ENCHANT) {
                            pushEnchantOptions(guid, user);
                        }
                        actions.add(new GameAction("OPEN_CONTAINER", user, Map.of(
                                "type", Integer.toString(ctype),
                                "x", Integer.toString(tx.x()),
                                "y", Integer.toString(tx.y()),
                                "z", Integer.toString(tx.z()),
                                "block", block
                        )));
                    } else {
                        actions.add(new GameAction("PLACE", user, Map.of(
                                "x", Integer.toString(tx.x()),
                                "y", Integer.toString(tx.y()),
                                "z", Integer.toString(tx.z()),
                                "tx", Integer.toString(tx.transactionType())
                        )));
                    }
                } else if (tx != null && tx.hasPos()) {
                    actions.add(new GameAction("BREAK", user, Map.of(
                            "x", Integer.toString(tx.x()),
                            "y", Integer.toString(tx.y()),
                            "z", Integer.toString(tx.z())
                    )));
                    sendBlockUpdate(guid, tx.x(), tx.y(), tx.z(), 0);
                } else {
                    actions.add(new GameAction("BREAK", user, Map.of("pkt", kind.name())));
                }
            }
            case INTERACT -> {
                var interact = BedrockPacketCodec.tryDecodeInteract(decoded.body());
                if (interact != null) {
                    // Bedrock interact actions: 1=hurt, 2=interact / open, 3=open inventory
                    byte act = interact.action();
                    if (act == 1 || act == 4) {
                        actions.add(new GameAction("ATTACK", user, Map.of(
                                "target", Long.toString(interact.targetRuntimeId()),
                                "targetName", nameForRuntime(interact.targetRuntimeId()),
                                "targetUuid", uuidForRuntime(interact.targetRuntimeId()),
                                "action", Byte.toString(act)
                        )));
                    } else {
                        // Interact / open — villager by actor type (not display-name substring)
                        BedrockEntityTracker.Tracked target = entities.get(interact.targetRuntimeId());
                        String targetName = target != null ? target.name() : nameForRuntime(interact.targetRuntimeId());
                        String actorType = target != null ? target.actorType() : "";
                        if (isVillagerActor(actorType, targetName)) {
                            long trader = interact.targetRuntimeId();
                            BedrockContainerBridge.OpenWindow w = containers.openVillager(
                                    user, trader, targetName);
                            pushVillagerTrade(guid, user, w, trader);
                            actions.add(new GameAction("OPEN_CONTAINER", user, Map.of(
                                    "type", Integer.toString(BedrockContainerBridge.TYPE_VILLAGER),
                                    "target", targetName == null ? "" : targetName,
                                    "runtime", Long.toString(trader)
                            )));
                        } else {
                            actions.add(new GameAction("INTERACT", user, Map.of(
                                    "target", Long.toString(interact.targetRuntimeId()),
                                    "targetName", targetName == null ? "" : targetName,
                                    "action", Byte.toString(act)
                            )));
                        }
                    }
                } else {
                    actions.add(new GameAction("ATTACK", user, Map.of("pkt", kind.name())));
                }
            }
            case CONTAINER_CLOSE -> {
                containers.handleClientClose(user, decoded.body());
                actions.add(new GameAction("CLOSE_CONTAINER", user, Map.of("pkt", kind.name())));
            }
            case ANIMATE ->
                    actions.add(new GameAction("ATTACK", user, Map.of("pkt", kind.name())));
            case MOB_EQUIPMENT -> {
                var eq = BedrockPacketCodec.tryDecodeMobEquipment(decoded.body());
                if (eq != null) {
                    inventory.setHeldHotbar(user, eq.hotbarSlot());
                    pushInventory(guid, user);
                    actions.add(new GameAction("INV", user, Map.of(
                            "pkt", "MOB_EQUIPMENT",
                            "hotbar", Integer.toString(eq.hotbarSlot())
                    )));
                } else {
                    actions.add(new GameAction("INV", user, Map.of("pkt", kind.name())));
                }
            }
            case INVENTORY_CONTENT, INVENTORY_SLOT, PLAYER_HOTBAR ->
                    actions.add(new GameAction("INV", user, Map.of("pkt", kind.name())));
            case ITEM_STACK_REQUEST -> {
                var req = BedrockPacketCodec.tryDecodeItemStackRequest(decoded.body());
                if (req != null) {
                    inventory.ensure(user);
                    boolean mutated = inventory.applyActions(user, req.actions());
                    outbound.accept(guid, List.of(BedrockPacketCodec.itemStackResponseOk(req.requestId())));
                    if (mutated) {
                        pushInventory(guid, user);
                        pushOpenContainer(guid, user);
                        BedrockContainerBridge.OpenWindow ow = containers.current(user);
                        if (ow != null && ow.type() == BedrockContainerBridge.TYPE_ENCHANT) {
                            pushEnchantOptions(guid, user);
                        }
                        if (ow != null && ow.type() == BedrockContainerBridge.TYPE_VILLAGER) {
                            pushVillagerTrade(guid, user, ow, ow.entityRuntimeId());
                        }
                        if (ow != null && ow.type() == BedrockContainerBridge.TYPE_FURNACE) {
                            pushFurnaceProgress(guid, ow);
                        }
                    }
                    actions.add(new GameAction("INV", user, Map.of(
                            "requestId", Integer.toString(req.requestId()),
                            "actions", Integer.toString(req.actionCount()),
                            "mutated", Boolean.toString(mutated)
                    )));
                } else {
                    actions.add(new GameAction("INV", user, Map.of("pkt", kind.name())));
                }
            }
            case MODAL_FORM_RESPONSE -> forms.handleResponse(user, decoded.body());
            case PLAYER_SKIN -> skins.ingestClientSkin(user, decoded.body());
            case COMMAND_REQUEST -> {
                var cmd = BedrockPacketCodec.tryDecodeCommandRequest(decoded.body());
                String line = "/";
                if (cmd != null && cmd.command() != null && !cmd.command().isBlank()) {
                    line = cmd.command().trim();
                    if (!line.startsWith("/")) {
                        line = "/" + line;
                    }
                }
                String result = com.yapcore.game.GameCommandBridge.dispatch(line, null);
                boolean ok = result != null
                        && !result.startsWith("Paper not")
                        && !result.startsWith("Game not ready")
                        && !result.startsWith("Folia is not")
                        && !result.startsWith("Could not")
                        && !result.startsWith("Paper command error")
                        && !result.startsWith("Folia stdin error");
                // Local shadow give/clear for BE-only sessions (no Bukkit player)
                applyCommandInventoryHints(user, line);
                outbound.accept(guid, List.of(
                        BedrockPacketCodec.commandOutputSimple(result == null ? "" : result, ok),
                        BedrockPacketCodec.textChat("YaPcore", result == null ? line : result)
                ));
                pushInventory(guid, user);
                actions.add(new GameAction("COMMAND", user, Map.of("msg", line, "result",
                        result == null ? "" : result)));
            }
            case REQUEST_CHUNK_RADIUS -> handleChunkRadius(guid, decoded.body(), actions, user);
            default -> LOG.fine("BE pkt " + kind + " id=" + decoded.id());
        }
    }

    private static GameAction moveAction(String user, float x, float y, float z, float yaw, float pitch) {
        return new GameAction("MOVE", user, Map.of(
                "x", Integer.toString((int) x),
                "y", Integer.toString((int) y),
                "z", Integer.toString((int) z),
                "yaw", Float.toString(yaw),
                "pitch", Float.toString(pitch)
        ));
    }

    private void sendNetworkSettings(long guid) {
        LOG.info("BE network settings → guid=" + Long.toHexString(guid));
        outbound.accept(guid, List.of(BedrockPacketCodec.networkSettingsUncompressed()));
        // Next batches must include compression-method byte (255=none).
        markCompressionHeader(guid);
    }

    private void markCompressionHeader(long guid) {
        // DualStackGateway outbound closes over rakNet peers; set via callback if present.
        if (compressionArmed != null) {
            compressionArmed.accept(guid);
        }
    }

    private java.util.function.LongConsumer compressionArmed;

    /** Called by DualStackGateway so RakNetPeer can arm compressor-in-header. */
    public void setCompressionArmed(java.util.function.LongConsumer compressionArmed) {
        this.compressionArmed = compressionArmed;
    }

    private void handleChunkRadius(long guid, ByteBuf body, List<GameAction> actions, String user) {
        int radius = 8;
        try {
            radius = Math.max(2, Math.min(16, BedrockPacketCodec.readUnsignedVarInt(body.duplicate())));
        } catch (Exception ignored) {
            // default
        }
        chunkRadius.put(guid, radius);
        columns.setRadius(guid, radius);
        List<ByteBuf> out = new ArrayList<>();
        out.add(BedrockPacketCodec.chunkRadiusUpdated(radius));
        double[] spawn = paperSpawnOrDefault();
        int sx = (int) Math.floor(spawn[0]);
        int sy = (int) Math.floor(spawn[1]);
        int sz = (int) Math.floor(spawn[2]);
        out.add(BedrockPacketCodec.networkChunkPublisherUpdate(sx, sy, sz, radius * 16));
        // Initial ring + continuous fill on MOVE (P4.5)
        for (BedrockColumnStreamer.Column c : columns.initialRing(guid, sx, sz, 2)) {
            out.add(chunkFor(c.cx(), c.cz()));
        }
        outbound.accept(guid, out);
        mirrorPaperPlayers();
        actions.add(new GameAction("MOVE", user, Map.of("chunkRadius", Integer.toString(radius))));
    }

    private void sendBlockUpdate(long guid, int x, int y, int z, int runtimeAir) {
        outbound.accept(guid, List.of(
                BedrockPacketCodec.updateBlock(x, y, z, runtimeAir, 0, 0)
        ));
    }

    private void beginLogin(long guid, String address, ByteBuf body, List<GameAction> actions) {
        FloodgateAuth.Identity identity = floodgate.authenticate(body, address);
        int proto = pendingProtocol.getOrDefault(guid, identity.protocol());
        if (proto <= 0) {
            proto = identity.protocol() > 0 ? identity.protocol() : 712;
        }
        long runtime = runtimeIds.getAndIncrement();
        sessions.open(guid, identity.username(), proto, address);
        runtimeByGuid.put(guid, runtime);
        skins.registerDefault(identity.username(), identity.javaUuid());
        entities.addPlayer(runtime, runtime, identity.javaUuid(), identity.username(),
                8.5f, 65.62f, -7.5f, false);
        // Tell everyone else about the new player
        ByteBuf announce = BedrockPacketCodec.addPlayer(
                identity.javaUuid(), identity.username(), runtime, 8.5f, 65.62f, -7.5f, 0f, 0f);
        for (Long other : sessions.allGuids()) {
            if (!other.equals(guid)) {
                outbound.accept(other, List.of(announce.retainedDuplicate()));
            }
        }
        announce.release();
        actions.add(new GameAction("JOIN", identity.username(), Map.of(
                "protocol", Integer.toString(proto),
                "xuid", identity.xuid(),
                "uuid", identity.javaUuid().toString(),
                "floodgate", "true",
                "runtimeId", Long.toString(runtime)
        )));
        List<ByteBuf> out = new ArrayList<>();
        out.add(BedrockPacketCodec.playStatus(BedrockPacketCodec.PlayStatus.LOGIN_SUCCESS));
        out.add(BedrockPacketCodec.resourcePacksInfoEmpty());
        out.add(BedrockPacketCodec.resourcePackStackEmpty());
        double[] spawn = paperSpawnOrDefault();
        int sx = (int) Math.floor(spawn[0]);
        int sy = (int) Math.floor(spawn[1]);
        int sz = (int) Math.floor(spawn[2]);
        out.add(BedrockPacketCodec.startGame(runtime, runtime, "YaPcore",
                sx, sy, sz, identity.javaUuid()));
        out.add(BedrockPacketCodec.updateAttributesDefault(runtime));
        out.add(BedrockPacketCodec.setTime(1000));
        out.add(BedrockPacketCodec.setDifficulty(1));
        out.add(BedrockPacketCodec.setCommandsEnabled(true));
        out.add(BedrockPacketCodec.availableCommandsRich());
        out.add(BedrockPacketCodec.creativeContentFull());
        out.add(BedrockNbtDumps.availableEntityIdentifiers());
        out.add(BedrockNbtDumps.biomeDefinitionList());
        out.add(BedrockPacketCodec.playerListAddSelf(identity.javaUuid(), runtime, identity.username()));
        // Local player uses player_list + start_game; add_player is for remote peers only
        out.add(BedrockPacketCodec.playStatus(BedrockPacketCodec.PlayStatus.PLAYER_SPAWN));
        out.add(BedrockPacketCodec.chunkRadiusUpdated(8));
        out.add(BedrockPacketCodec.networkChunkPublisherUpdate(sx, sy, sz, 128));
        columns.setRadius(guid, 8);
        // Initial ring must not block Netty on Paper column scans (98k getBlock/column).
        // Flat keeps spawn grounded; Paper columns stream right after on a virtual thread.
        List<BedrockColumnStreamer.Column> ring = columns.initialRing(guid, sx, sz, 2);
        for (BedrockColumnStreamer.Column c : ring) {
            out.add(BedrockPacketCodec.levelChunkFlat(c.cx(), c.cz()));
        }
        inventory.ensure(identity.username());
        out.add(BedrockPacketCodec.inventoryContent(0, inventory.storageNetworkIds(identity.username())));
        out.addAll(entities.snapshotPackets(runtime));
        outbound.accept(guid, out);
        final long g = guid;
        Thread.ofVirtual().name("yap-be-paper-chunks-" + identity.username()).start(() -> {
            try {
                List<ByteBuf> paperChunks = new ArrayList<>();
                for (BedrockColumnStreamer.Column c : ring) {
                    paperChunks.add(chunkFor(c.cx(), c.cz()));
                }
                if (!paperChunks.isEmpty()) {
                    outbound.accept(g, paperChunks);
                }
            } catch (Exception e) {
                LOG.fine("paper chunk warm: " + e.getMessage());
            }
        });
        // Paper Player inject after spawn — never block RakNet login
        BedrockPaperWorldSync sync = paperWorld;
        if (sync != null && sync.isEnabled()) {
            final String user = identity.username();
            final java.util.UUID uuid = identity.javaUuid();
            final double ix = spawn[0];
            final double iy = spawn[1] + 0.1;
            final double iz = spawn[2];
            Thread.ofVirtual().name("yap-be-paper-inject-" + user).start(() -> {
                boolean ok = sync.injectPlayer(user, uuid, ix, iy, iz);
                if (!ok) {
                    sync.injectBedrockPlayer(uuid, user);
                    return;
                }
                try {
                    Object player = sync.findOnlinePlayer(user);
                    skins.applyToPaperPlayer(user, player);
                } catch (Throwable e) {
                    LOG.fine("skin→Paper: " + e.getMessage());
                }
                int[][] paperStacks = sync.snapshotInventoryStacksLiveOnly(user, 36);
                if (paperStacks != null) {
                    inventory.seedStorage(user, paperStacks[0], paperStacks[1]);
                    outbound.accept(g, List.of(
                            BedrockPacketCodec.inventoryContent(0, paperStacks[0], paperStacks[1])));
                } else {
                    int[] paperInv = sync.snapshotInventoryNetworkIds(user, 36);
                    if (paperInv != null) {
                        outbound.accept(g, List.of(BedrockPacketCodec.inventoryContent(0, paperInv)));
                    }
                }
                LOG.info("BE→Paper player online " + user);
            });
        }
        mirrorPaperPlayers();
        LOG.info("BE login " + identity.username() + " xuid=" + identity.xuid()
                + " uuid=" + identity.javaUuid());
    }

    /**
     * P4.5 — Paper-backed column is the product default when world sync is attached.
     * Flat only when {@code -Dyapcore.bedrock.flat-chunks=true} (or Paper unavailable).
     * Soft fallback: if Paper snapshot returns null, flat keeps the client grounded
     * unless {@code -Dyapcore.bedrock.paper-chunks-fallback-flat=false}.
     */
    private ByteBuf chunkFor(int cx, int cz) {
        boolean forceFlat = Boolean.getBoolean("yapcore.bedrock.flat-chunks");
        BedrockPaperWorldSync sync = paperWorld;
        if (!forceFlat && sync != null && sync.isEnabled()) {
            int[][] column = sync.snapshotColumnHashedStates(cx, cz);
            if (column != null) {
                return BedrockPacketCodec.levelChunkFromColumn(cx, cz, column);
            }
            // Paper attached but column not ready — soft flat unless explicitly disabled
            if (!"false".equalsIgnoreCase(System.getProperty("yapcore.bedrock.paper-chunks-fallback-flat", "true"))) {
                return BedrockPacketCodec.levelChunkFlat(cx, cz);
            }
            return BedrockPacketCodec.levelChunkEmpty(cx, cz);
        }
        return BedrockPacketCodec.levelChunkFlat(cx, cz);
    }

    /** Continuous Paper column stream — never block Netty on full-column scans. */
    private void streamColumnsAround(long guid, int blockX, int blockY, int blockZ, boolean force) {
        List<BedrockColumnStreamer.Column> need = columns.missingAround(guid, blockX, blockZ, force);
        if (need.isEmpty()) {
            return;
        }
        // Immediate publisher + flat placeholders keep the client grounded
        List<ByteBuf> quick = new ArrayList<>(need.size() + 1);
        quick.add(BedrockPacketCodec.networkChunkPublisherUpdate(blockX, blockY, blockZ,
                columns.radius(guid) * 16));
        for (BedrockColumnStreamer.Column c : need) {
            quick.add(BedrockPacketCodec.levelChunkFlat(c.cx(), c.cz()));
        }
        outbound.accept(guid, quick);
        final long g = guid;
        Thread.ofVirtual().name("yap-be-paper-stream-" + guid).start(() -> {
            try {
                List<ByteBuf> paper = new ArrayList<>(need.size());
                for (BedrockColumnStreamer.Column c : need) {
                    paper.add(chunkFor(c.cx(), c.cz()));
                }
                if (!paper.isEmpty()) {
                    outbound.accept(g, paper);
                }
            } catch (Exception e) {
                LOG.fine("paper stream: " + e.getMessage());
            }
        });
    }

    private void pushEnchantOptions(long guid, String user) {
        BedrockContainerBridge.OpenWindow w = containers.current(user);
        if (w == null) {
            return;
        }
        BedrockPaperWorldSync sync = paperWorld;
        java.util.List<BedrockPaperRecipes.EnchantOption> opts = java.util.List.of();
        if (sync != null && sync.isEnabled()) {
            opts = new BedrockPaperRecipes(sync).enchantOptionsFor(user);
        }
        // Fail closed when Paper has no offers — do not ship fake Protection/Unbreaking
        if (opts.isEmpty()) {
            outbound.accept(guid, List.of(BedrockPacketCodec.playerEnchantOptions(java.util.List.of())));
            return;
        }
        java.util.List<ByteBuf> pkts = new java.util.ArrayList<>();
        pkts.add(BedrockPacketCodec.playerEnchantOptions(opts));
        for (int i = 0; i < opts.size(); i++) {
            pkts.add(BedrockPacketCodec.containerSetData(w.windowId(), i, opts.get(i).cost()));
        }
        outbound.accept(guid, pkts);
    }

    private void pushVillagerTrade(long guid, String user, BedrockContainerBridge.OpenWindow w,
                                   long traderRuntime) {
        if (w == null) {
            return;
        }
        BedrockPaperWorldSync sync = paperWorld;
        java.util.List<int[]> offers = java.util.List.of();
        if (sync != null && sync.isEnabled()) {
            sync.openMerchant(user, nameForRuntime(traderRuntime));
            offers = sync.snapshotMerchantOffers(user, 16);
        }
        Long playerRt = runtimeByGuid.get(guid);
        outbound.accept(guid, List.of(BedrockPacketCodec.updateTrade(
                w.windowId(), BedrockContainerBridge.TYPE_VILLAGER,
                offers.size(), 0, true, false,
                traderRuntime, playerRt == null ? 0L : playerRt,
                "Villager", offers)));
    }

    /** After dig/place — drop Paper cache and re-send that column. */
    private void resendColumn(long guid, int cx, int cz) {
        BedrockPaperWorldSync sync = paperWorld;
        if (sync != null) {
            sync.invalidateColumn(cx, cz);
        }
        columns.invalidateAllSessions(cx, cz);
        outbound.accept(guid, List.of(chunkFor(cx, cz)));
        columns.markSent(guid, cx, cz);
    }

    private double[] paperSpawnOrDefault() {
        BedrockPaperWorldSync sync = paperWorld;
        if (sync != null && sync.isEnabled()) {
            double[] s = sync.spawnPosition();
            if (s != null) {
                return s;
            }
        }
        return new double[]{8, 64, -8};
    }

    /** Mirror Paper JE online players + nearby living entities into the BE entity roster. */
    private void mirrorPaperPlayers() {
        BedrockPaperWorldSync sync = paperWorld;
        if (sync == null || !sync.isEnabled()) {
            return;
        }
        for (BedrockPaperWorldSync.OnlinePlayer p : sync.listOnlinePlayers()) {
            if (p.name() == null || p.uuid() == null) {
                continue;
            }
            if (entities.runtimeFor(p.name()) != null) {
                Long rt = entities.runtimeFor(p.name());
                entities.move(rt, (float) p.x(), (float) p.y(), (float) p.z(), 0f, 0f);
                float[] hp = sync.snapshotPlayerHealth(p.name());
                if (hp != null && rt != null) {
                    entities.updateData(rt, hp[0], p.name(), false);
                }
                continue;
            }
            long runtime = runtimeIds.getAndIncrement();
            entities.addPlayer(runtime, runtime, p.uuid(), p.name(),
                    (float) p.x(), (float) p.y(), (float) p.z(), true);
        }
        double[] spawn = paperSpawnOrDefault();
        for (BedrockPaperWorldSync.NearbyLiving e : sync.listNearbyLiving(spawn[0], spawn[1], spawn[2], 48)) {
            if (e.uuid() == null) {
                continue;
            }
            Long existing = entities.runtimeForUuid(e.uuid());
            if (existing != null) {
                entities.move(existing, (float) e.x(), (float) e.y(), (float) e.z(), 0f, 0f);
                entities.updateData(existing, e.health(), e.name(), false);
                continue;
            }
            long runtime = runtimeIds.getAndIncrement();
            String type = e.entityType() == null ? "minecraft:pig" : e.entityType();
            entities.addActor(runtime, runtime, e.uuid(), type,
                    (float) e.x(), (float) e.y(), (float) e.z(), true);
        }
    }

    private String uuidForRuntime(long runtimeId) {
        BedrockEntityTracker.Tracked t = entities.get(runtimeId);
        return t != null && t.uuid() != null ? t.uuid().toString() : "";
    }

    private String nameForRuntime(long runtimeId) {
        BedrockEntityTracker.Tracked t = entities.get(runtimeId);
        return t != null && t.name() != null ? t.name() : "";
    }

    private void sendSpawnSequence(long guid, BedrockSessionManager.BedrockSession session) {
        if (session == null) {
            return;
        }
        long runtime = runtimeIds.getAndIncrement();
        UUID uuid = floodgate.uuidFor(session.username());
        int radius = chunkRadius.getOrDefault(guid, 8);
        double[] spawn = paperSpawnOrDefault();
        int sx = (int) Math.floor(spawn[0]);
        int sy = (int) Math.floor(spawn[1]);
        int sz = (int) Math.floor(spawn[2]);
        List<ByteBuf> out = new ArrayList<>();
        out.add(BedrockPacketCodec.startGame(runtime, runtime, "YaPcore", sx, sy, sz, uuid));
        out.add(BedrockPacketCodec.playStatus(BedrockPacketCodec.PlayStatus.PLAYER_SPAWN));
        out.add(BedrockPacketCodec.chunkRadiusUpdated(radius));
        out.add(BedrockPacketCodec.networkChunkPublisherUpdate(sx, sy, sz, radius * 16));
        columns.setRadius(guid, radius);
        List<BedrockColumnStreamer.Column> ring = columns.initialRing(guid, sx, sz, 2);
        for (BedrockColumnStreamer.Column c : ring) {
            out.add(BedrockPacketCodec.levelChunkFlat(c.cx(), c.cz()));
        }
        out.addAll(entities.snapshotPackets());
        outbound.accept(guid, out);
        final long g = guid;
        Thread.ofVirtual().name("yap-be-paper-chunks-spawn-" + guid).start(() -> {
            try {
                List<ByteBuf> paperChunks = new ArrayList<>();
                for (BedrockColumnStreamer.Column c : ring) {
                    paperChunks.add(chunkFor(c.cx(), c.cz()));
                }
                if (!paperChunks.isEmpty()) {
                    outbound.accept(g, paperChunks);
                }
            } catch (Exception e) {
                LOG.fine("spawn paper chunks: " + e.getMessage());
            }
        });
        mirrorPaperPlayers();
    }

    public ByteBuf encodeChatToClient(String source, String message) {
        return BedrockPacketCodec.textChat(source, message);
    }

    public ByteBuf encodeBlockUpdate(int x, int y, int z, int runtimeId) {
        return BedrockPacketCodec.updateBlock(x, y, z, runtimeId, 0, 0);
    }

    public void onDisconnect(long guid) {
        Long runtime = runtimeByGuid.remove(guid);
        BedrockSessionManager.BedrockSession session = sessions.get(guid);
        if (session != null) {
            entities.removeByName(session.username());
            BedrockPaperWorldSync sync = paperWorld;
            if (sync != null) {
                sync.ejectPlayer(session.username());
                sync.ejectBedrockPlayer(session.username());
            }
        } else if (runtime != null) {
            entities.remove(runtime);
        }
        sessions.close(guid);
        chunkRadius.remove(guid);
        columns.clear(guid);
        if (session != null && session.username() != null) {
            inventoryFingerprint.remove(session.username().toLowerCase());
        }
    }

    private String paperBlockHint(int x, int y, int z) {
        BedrockPaperWorldSync sync = paperWorld;
        if (sync == null || !sync.isEnabled()) {
            return null;
        }
        return sync.materialAt(x, y, z);
    }

    /** Push shadow (and Paper-synced) inventory → BE inventory_content. */
    private void pushInventory(long guid, String username) {
        inventory.ensure(username);
        BedrockPaperWorldSync sync = paperWorld;
        if (sync != null && sync.isEnabled()) {
            int[][] paper = sync.snapshotInventoryStacksLiveOnly(username, 36);
            if (paper != null) {
                inventory.seedStorage(username, paper[0], paper[1]);
                inventoryFingerprint.put(username.toLowerCase(), fingerprintStacks(paper[0], paper[1]));
                outbound.accept(guid, List.of(BedrockPacketCodec.inventoryContent(0, paper[0], paper[1])));
                return;
            }
        }
        int[] ids = inventory.storageNetworkIds(username);
        int[] counts = inventory.storageCounts(username);
        inventoryFingerprint.put(username.toLowerCase(), fingerprintStacks(ids, counts));
        outbound.accept(guid, List.of(BedrockPacketCodec.inventoryContent(0, ids, counts)));
    }

    /**
     * G.28 — when Paper inventory changes externally (JE/console /give), push to BE
     * without waiting for a BE stack-request.
     */
    private void maybePushPaperInventory(long guid, String username) {
        BedrockPaperWorldSync sync = paperWorld;
        if (sync == null || !sync.isEnabled() || !sync.hasInjectedPlayer(username)) {
            return;
        }
        int[][] paper = sync.snapshotInventoryStacksLiveOnly(username, 36);
        if (paper == null) {
            return;
        }
        long fp = fingerprintStacks(paper[0], paper[1]);
        Long prev = inventoryFingerprint.get(username.toLowerCase());
        if (prev != null && prev == fp) {
            return;
        }
        inventory.seedStorage(username, paper[0], paper[1]);
        inventoryFingerprint.put(username.toLowerCase(), fp);
        outbound.accept(guid, List.of(BedrockPacketCodec.inventoryContent(0, paper[0], paper[1])));
    }

    private static long fingerprintStacks(int[] ids, int[] counts) {
        long h = 1125899906842597L;
        int n = ids == null ? 0 : ids.length;
        for (int i = 0; i < n; i++) {
            h = 31 * h + (ids[i] & 0xffffffffL);
            int c = counts != null && i < counts.length ? counts[i] : 0;
            h = 31 * h + (c & 0xffffffffL);
        }
        return h;
    }

    private void pushOpenContainer(long guid, String username) {
        BedrockContainerBridge.OpenWindow w = containers.current(username);
        if (w == null) {
            return;
        }
        int n = containers.slotsForType(w.type());
        int[][] snap = inventory.containerSnapshot(username, n);
        outbound.accept(guid, List.of(BedrockPacketCodec.inventoryContent(w.windowId(), snap[0], snap[1])));
    }

    private void pushOpenContainerProgress(long guid, String username) {
        BedrockContainerBridge.OpenWindow w = containers.current(username);
        if (w == null) {
            return;
        }
        if (w.type() == BedrockContainerBridge.TYPE_FURNACE) {
            pushFurnaceProgress(guid, w);
        }
    }

    private void pushFurnaceProgress(long guid, BedrockContainerBridge.OpenWindow w) {
        BedrockPaperWorldSync sync = paperWorld;
        if (sync == null || !sync.isEnabled() || w == null) {
            return;
        }
        int[] prog = sync.snapshotFurnaceProgress(w.x(), w.y(), w.z());
        if (prog == null || prog.length < 4) {
            return;
        }
        // Bedrock furnace properties: 0=cook tick, 1=cook total, 2=burn remaining, 3=burn max
        outbound.accept(guid, List.of(
                BedrockPacketCodec.containerSetData(w.windowId(), 0, prog[0]),
                BedrockPacketCodec.containerSetData(w.windowId(), 1, prog[1]),
                BedrockPacketCodec.containerSetData(w.windowId(), 2, prog[2]),
                BedrockPacketCodec.containerSetData(w.windowId(), 3, prog[3])
        ));
    }

    private static boolean isVillagerActor(String actorType, String name) {
        String a = actorType == null ? "" : actorType.toLowerCase(java.util.Locale.ROOT);
        if (a.contains("villager") || a.contains("wandering_trader")) {
            return true;
        }
        String n = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        return n.contains("villager") || n.contains("wandering");
    }

    /** Best-effort /give and /clear into shadow — skip when live Paper player owns inventory. */
    private void applyCommandInventoryHints(String username, String line) {
        if (line == null) {
            return;
        }
        BedrockPaperWorldSync sync = paperWorld;
        if (sync != null && sync.hasInjectedPlayer(username)) {
            // Paper already applied the command; refresh BE from live inventory
            return;
        }
        String cmd = line.startsWith("/") ? line.substring(1).trim() : line.trim();
        String lower = cmd.toLowerCase(java.util.Locale.ROOT);
        inventory.ensure(username);
        if (lower.equals("clear") || lower.startsWith("clear ")) {
            String[] parts = cmd.split("\\s+");
            // /clear [player] [item] — filter clear only when item token present and no player, or player is self
            if (parts.length >= 2 && !isSelfSelector(parts[1], username) && !looksLikeItemToken(parts[1])) {
                // Targeting another player — ignore for local shadow
                return;
            }
            if (parts.length >= 3 || (parts.length == 2 && looksLikeItemToken(parts[1]))) {
                String itemTok = parts.length >= 3 ? parts[2] : parts[1];
                int nid = networkIdForItemName(itemTok);
                if (nid > 0) {
                    inventory.clearItem(username, nid);
                } else {
                    inventory.clear(username);
                }
            } else {
                inventory.clear(username);
            }
            return;
        }
        if (lower.startsWith("give ")) {
            String[] parts = cmd.split("\\s+");
            // Forms: /give <player> <item> [count] | /give <item> [count] | /give @s/@p <item> [count]
            if (parts.length >= 2) {
                String item;
                int count;
                if (parts.length >= 3 && (isSelfSelector(parts[1], username) || !looksLikeItemToken(parts[1]))) {
                    if (!isSelfSelector(parts[1], username) && !parts[1].equalsIgnoreCase(username)) {
                        return; // other player target
                    }
                    item = parts[2];
                    count = parts.length >= 4 ? parseIntSafe(parts[3], 1) : 1;
                } else {
                    item = parts[1];
                    count = parts.length >= 3 ? parseIntSafe(parts[2], 1) : 1;
                }
                int nid = networkIdForItemName(item);
                if (nid > 0) {
                    inventory.give(username, nid, count);
                }
            }
        }
    }

    private static boolean isSelfSelector(String token, String username) {
        if (token == null) {
            return false;
        }
        String t = token.toLowerCase(java.util.Locale.ROOT);
        return t.equals("@s") || t.equals("@p") || t.equalsIgnoreCase(username);
    }

    private static boolean looksLikeItemToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        if (token.startsWith("@")) {
            return false;
        }
        // Numeric count alone is not an item
        try {
            Integer.parseInt(token);
            return false;
        } catch (NumberFormatException ignored) {
        }
        return true;
    }

    private static int networkIdForItemName(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0;
        }
        String name = raw.trim();
        if (!name.contains(":")) {
            name = "minecraft:" + name.toLowerCase(java.util.Locale.ROOT);
        } else {
            name = name.toLowerCase(java.util.Locale.ROOT);
        }
        // Strip trailing NBT / components for simple name match
        int brace = name.indexOf('{');
        if (brace > 0) {
            name = name.substring(0, brace);
        }
        int bracket = name.indexOf('[');
        if (bracket > 0) {
            name = name.substring(0, bracket);
        }
        for (BedrockItemStates.ItemState s : BedrockItemStates.all()) {
            if (s.name().equals(name)) {
                return s.runtimeId() & 0xFFFF;
            }
        }
        return 0;
    }

    private static int parseIntSafe(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
