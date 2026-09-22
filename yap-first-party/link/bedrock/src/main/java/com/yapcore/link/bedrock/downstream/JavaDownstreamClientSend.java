package com.yapcore.link.bedrock.downstream;

import java.nio.charset.StandardCharsets;

/** Outbound JE play packets for {@link JavaDownstreamClient} (≤500-line domain gate). */
final class JavaDownstreamClientSend {

    private final JavaDownstreamClient client;

    JavaDownstreamClientSend(JavaDownstreamClient client) {
        this.client = client;
    }

    void sendMovePosRot(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        sendMovePosRot(x, y, z, yaw, pitch, onGround, false);
    }

    void sendMovePosRot(double x, double y, double z, float yaw, float pitch,
                        boolean onGround, boolean horizontalCollision) {
        client.writePlay(JavaPlayWire.movePosRot(x, y, z, yaw, pitch, onGround, horizontalCollision));
    }

    /** Proto 776+ — Geyser sends after every PlayerAuthInput once SPAWNED. */
    void sendClientTickEnd() {
        client.writePlay(JavaPlayWire.clientTickEnd());
    }

    /** Proto 776+ player_input — send before move packets (Geyser InputCache order). */
    void sendPlayerInput(boolean forward, boolean backward, boolean left, boolean right,
                         boolean jump, boolean shift, boolean sprint) {
        client.writePlay(JavaPlayWire.playerInput(forward, backward, left, right, jump, shift, sprint));
    }

    void sendAcceptTeleport(int teleportId) {
        client.writePlay(JavaPlayWire.acceptTeleport(teleportId));
    }

    void sendSetCarriedItem(int hotbarSlot) {
        client.writePlay(JavaPlayWire.setCarriedItem(hotbarSlot));
    }

    void sendContainerClick(int windowId, int stateId, int slot, int button, int mode,
                            Object ignoredCarried) {
        client.writePlay(JavaPlayInventoryWire.containerClick(windowId, stateId, slot, button, mode));
    }

    void sendContainerClose(int windowId) {
        client.writePlay(JavaPlayInventoryWire.containerClose(windowId));
    }

    void sendRenameItem(String name) {
        client.writePlay(JavaPlayInventoryWire.renameItem(name));
    }

    void sendCustomPayload(String channel, byte[] data) {
        client.writePlay(JavaPlayInventoryWire.customPayload(channel, data));
    }

    /**
     * After Login (play), register BungeeCord so YaPPortals {@code Connect} reaches this
     * downstream — same requirement as native JE {@code ClientSession.ensureProxyChannelsRegistered}.
     */
    void ensureProxyChannelsRegistered() {
        if (!client.markProxyChannelsRegistered()) {
            return;
        }
        // Null-separated Identifier list (Paper rejects uppercase / legacy BungeeCord here).
        byte[] payload = "bungeecord:main".getBytes(StandardCharsets.UTF_8);
        sendCustomPayload("minecraft:register", payload);
        JavaDownstreamClient.LOG.info("JE REGISTER bungeecord:main → backend user=" + client.username);
    }

    void sendPlayerAction(int status, int x, int y, int z, int face, int sequence) {
        client.writePlay(JavaPlayWire.playerAction(status, x, y, z, face, sequence));
    }

    void sendUseItemOn(int x, int y, int z, int face,
                       float cx, float cy, float cz, boolean inside, int hand, int sequence) {
        client.writePlay(JavaPlayWire.useItemOn(x, y, z, face, cx, cy, cz, inside, hand, sequence));
    }

    void sendInteractAttack(int entityId, boolean sneaking) {
        client.writePlay(JavaPlayWire.interactAttack(entityId, sneaking));
    }

    void sendInteractUse(int entityId, double x, double y, double z) {
        client.writePlay(JavaPlayWire.interactUse(entityId, x, y, z));
    }

    void sendClientCommandRespawn() {
        client.writePlay(JavaPlayWire.clientCommandRespawn());
    }

    /**
     * Geyser {@code sendJavaClientSettings}: update Folia's per-player chunk send radius.
     * Call after Bedrock {@code RequestChunkRadius} or when server view is known.
     */
    void sendClientInformationView(int viewDistance) {
        int view = Math.max(2, Math.min(32, viewDistance));
        client.requestedViewDistance = view;
        client.writePlay(JavaPlayWire.clientInformation(view));
        JavaDownstreamClient.LOG.info("JE Client Information (play) view=" + view + " user=" + client.username);
    }

    void sendSwingArm(int hand) {
        client.writePlay(JavaPlayWire.swingArm(hand));
    }

    void sendChatCommand(String commandWithoutSlash) {
        client.writePlay(JavaPlayWire.chatCommand(commandWithoutSlash));
    }

    void sendChatMessage(String message) {
        client.writePlay(JavaPlayWire.chatMessage(message));
    }
}
