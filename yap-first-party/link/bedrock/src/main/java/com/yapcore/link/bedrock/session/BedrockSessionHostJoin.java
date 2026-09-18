package com.yapcore.link.bedrock.session;

import com.yapcore.link.bedrock.codec.LinkCloudburstCodecs;
import com.yapcore.link.bedrock.downstream.JavaDownstreamClient;
import com.yapcore.link.bedrock.floodgate.LinkFloodgateAuth;
import com.yapcore.link.bedrock.probe.BedrockJoinProbe;
import com.yapcore.link.bedrock.translator.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

/** Phase 2/3 Java downstream + join session bootstrap (split from {@link BedrockSessionHost}). */
final class BedrockSessionHostJoin {

    private static final Logger LOG = Logger.getLogger("YaP.Link.Bedrock");

    private BedrockSessionHostJoin() {}

    static void startPhase2And3(BedrockSessionHost host, BedrockSessionHost.ClientState state) {
        if (state.downstream != null || host.group == null) {
            return;
        }
        if (state.codec == null && LinkCloudburstCodecs.isModern(state.protocol)) {
            state.codec = LinkCloudburstCodecs.open(state.protocol);
        }
        LinkFloodgateAuth.Identity identity = state.identity;
        UUID javaUuid = identity != null ? identity.javaUuid() : null;
        String user = state.username != null ? state.username : "BedrockPlayer";
        if (javaUuid == null) {
            javaUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + user).getBytes(StandardCharsets.UTF_8));
        }
        long runtimeId = state.guid != 0L ? Math.abs(state.guid) : ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        state.joinSession = LinkBedrockSession.open(
                state.guid,
                runtimeId,
                user,
                javaUuid,
                state.protocol,
                state.codec,
                packets -> host.sendPackets(state, packets));
        state.joinSession.setJoinPhase(LinkBedrockSession.JoinPhase.AWAITING_JAVA_LOGIN);

        String clientIp = state.peer != null && state.peer.address() != null
                ? state.peer.address().getAddress().getHostAddress()
                : "127.0.0.1";
        JavaDownstreamClient.Listener listener = new JavaDownstreamClient.Listener() {
            @Override
            public void onLoginSuccess(UUID uuid, String username) {
                BedrockJoinProbe.noteEvent(state.guid, "java_login_success " + username);
            }

            @Override
            public void onConfigurationComplete() {
                BedrockJoinProbe.noteEvent(state.guid, "java_configuration_complete");
            }

            @Override
            public void onLoginPlay(JavaDownstreamClient.LoginPlayInfo info) {
                state.phase = BedrockSessionHost.LoginPhase.JOINING;
                BedrockJoinProbe.notePhase(state.guid, state.phase.name());
                BedrockJoinProbe.noteEvent(state.guid,
                        "java_login_play entity=" + info.entityId() + " view=" + info.viewDistance()
                                + " dim=" + info.dimensionName());
                LOG.info("BE Folia login_play user=" + state.username
                        + " dim=" + info.dimensionName()
                        + " entity=" + info.entityId()
                        + " backend=" + host.config.javaBackend().getHostString()
                        + ":" + host.config.javaBackend().getPort());
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    join.setJavaEntityId(info.entityId());
                    join.onJavaLoginPlay(info);
                }
            }

            @Override
            public void onRespawn(JavaDownstreamClient.RespawnInfo info) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    join.onJavaRespawn(info);
                }
            }

            @Override
            public void onLevelChunk(int chunkX, int chunkZ, io.netty.buffer.ByteBuf payload) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    join.bufferOrTranslateLevelChunk(chunkX, chunkZ, payload);
                } else if (payload != null) {
                    payload.release();
                }
            }

            @Override
            public void onBlockUpdate(int x, int y, int z, int blockState) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaBlockUpdateTranslator.onBlockUpdate(join, x, y, z, blockState);
                }
            }

            @Override
            public void onSectionBlocksUpdate(int sectionX, int sectionY, int sectionZ,
                                              int[] cells) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaBlockUpdateTranslator.onSectionBlocksUpdate(join, sectionX, sectionY, sectionZ, cells);
                }
            }

            @Override
            public void onPlayerPosition(double x, double y, double z, float yaw, float pitch, int teleportId) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaMoveTranslator.onPlayerPosition(join, x, y, z, yaw, pitch, teleportId);
                }
            }

            @Override
            public void onEntityMove(int entityId, double x, double y, double z,
                                     float yaw, float pitch, boolean teleport) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaMoveTranslator.onEntityMove(join, entityId, x, y, z, yaw, pitch, teleport);
                }
            }

            @Override
            public void onEntityRelativeMove(int entityId, double dx, double dy, double dz,
                                             float yaw, float pitch, boolean rotationOnly,
                                             boolean onGround) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaMoveTranslator.onEntityRelativeMove(join, entityId, dx, dy, dz,
                            yaw, pitch, rotationOnly, onGround);
                }
            }

            @Override
            public void onEntityTeleport(int entityId, double x, double y, double z,
                                         float yaw, float pitch, int relatives, boolean onGround) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaMoveTranslator.onEntityTeleport(join, entityId, x, y, z, yaw, pitch, relatives);
                }
            }

            @Override
            public void onAddEntity(int entityId, java.util.UUID uuid, String typeKey,
                                    double x, double y, double z, float yaw, float pitch) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaEntityTranslator.onAddEntity(join, entityId, uuid, typeKey, x, y, z, yaw, pitch);
                }
            }

            @Override
            public void onRemoveEntities(int[] entityIds) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaEntityTranslator.onRemoveEntities(join, entityIds);
                }
            }

            @Override
            public void onContainerSetContent(int windowId, com.yapcore.link.bedrock.downstream.JeItemStackCodec.Stack[] stacks) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    if (state.downstream != null) {
                        join.rememberJeContainerState(state.downstream.lastContainerStateId);
                    }
                    JavaInventoryTranslator.onContainerSetContent(join, windowId, stacks);
                }
            }

            @Override
            public void onContainerSetSlot(int windowId, int slot,
                                           com.yapcore.link.bedrock.downstream.JeItemStackCodec.Stack stack) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    if (state.downstream != null) {
                        join.rememberJeContainerState(state.downstream.lastContainerStateId);
                    }
                    JavaInventoryTranslator.onContainerSetSlot(join, windowId, slot, stack);
                }
            }

            @Override
            public void onOpenScreen(int windowId, int menuTypeId) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaOpenScreenTranslator.onOpenScreen(join, windowId, menuTypeId);
                }
            }

            @Override
            public void onCommands(java.util.List<String> literalNames) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaCommandsTranslator.onCommands(join, literalNames);
                }
            }

            @Override
            public void onCommandsTree(com.yapcore.link.bedrock.downstream.JavaCommandsTree.Parsed tree) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaCommandsTranslator.onCommandsTree(join, tree);
                }
            }

            @Override
            public void onHurtAnimation(int entityId, float yaw) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaEntityCombatTranslator.onHurtAnimation(join, entityId, yaw);
                }
            }

            @Override
            public void onSetHealth(float health, int food, float saturation) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaEntityCombatTranslator.onSetHealth(join, health, food, saturation);
                }
            }

            @Override
            public void onSystemChat(String plain) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    ChatTranslator.javaSystemChatToBedrock(join, plain);
                }
            }

            @Override
            public void onActionBar(String plain) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaHudTranslator.onActionBar(join, plain);
                }
            }

            @Override
            public void onTitle(String plain) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaHudTranslator.onTitle(join, plain);
                }
            }

            @Override
            public void onSubtitle(String plain) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaHudTranslator.onSubtitle(join, plain);
                }
            }

            @Override
            public void onTitleTimes(int fadeInTicks, int stayTicks, int fadeOutTicks) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaHudTranslator.onTitleTimes(join, fadeInTicks, stayTicks, fadeOutTicks);
                }
            }

            @Override
            public void onClearTitles(boolean reset) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaHudTranslator.onClearTitles(join, reset);
                }
            }

            @Override
            public void onBossEvent(java.util.UUID bossId, int action, String title, float pct, int color) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaHudTranslator.onBossEvent(join, bossId, action, title, pct, color);
                }
            }

            @Override
            public void onSetObjective(String objectiveId, int mode, String displayName, String criteria) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaHudTranslator.onSetObjective(join, objectiveId, mode, displayName, criteria);
                }
            }

            @Override
            public void onSetDisplayObjective(int position, String objectiveId) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaHudTranslator.onSetDisplayObjective(join, position, objectiveId);
                }
            }

            @Override
            public void onSetScore(String owner, String objective, int score) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaHudTranslator.onSetScore(join, owner, objective, score);
                }
            }

            @Override
            public void onResetScore(String owner, String objective) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaHudTranslator.onResetScore(join, owner, objective);
                }
            }

            @Override
            public void onPlayerChat(String source, String plain) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    String name = source;
                    try {
                        java.util.UUID u = java.util.UUID.fromString(source);
                        String remembered = join.playerName(u);
                        if (remembered != null) {
                            name = remembered;
                        }
                    } catch (Exception ignored) {
                        // keep source
                    }
                    ChatTranslator.javaPlayerChatToBedrock(join, name, plain);
                    BedrockJoinProbe.noteEvent(state.guid,
                            "java_player_chat→be src=" + name + " len="
                                    + (plain == null ? 0 : plain.length()));
                }
            }

            @Override
            public void onPlayerInfoAdd(java.util.UUID uuid, String name) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaPlayerListTranslator.onPlayerInfoAdd(join, uuid, name);
                }
            }

            @Override
            public void onPlayerInfoRemove(java.util.UUID uuid) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaPlayerListTranslator.onPlayerInfoRemove(join, uuid);
                }
            }

            @Override
            public void onSetEntityData(int entityId) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaEntityTranslator.onSetEntityData(join, entityId);
                }
            }

            @Override
            public void onEntityCustomName(int entityId, String plainName, boolean visible) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaEntityTranslator.onCustomName(join, entityId, plainName, visible);
                }
            }

            @Override
            public void onEntityHealth(int entityId, float health) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaEntityCombatTranslator.onEntityHealth(join, entityId, health);
                }
            }

            @Override
            public void onUpdateAttributes(int entityId, float health) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaEntityCombatTranslator.onEntityHealth(join, entityId, health);
                }
            }

            @Override
            public void onEntityMotion(int entityId, double mx, double my, double mz) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaEntityTranslator.onEntityMotion(join, entityId, mx, my, mz);
                }
            }

            @Override
            public void onSetEquipment(int entityId, int slot,
                                      com.yapcore.link.bedrock.downstream.JeItemStackCodec.Stack stack) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaEntityEquipmentTranslator.onSetEquipment(join, entityId, slot, stack);
                }
            }

            @Override
            public void onCustomPayload(String channel, byte[] data) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    BedrockFormBridge.onJavaCustomPayload(join, channel, data);
                }
            }

            @Override
            public void onBungeeConnect(String targetServer) {
                BedrockSessionHostTransfer.transferToBackend(host, state, targetServer);
            }

            @Override
            public void onEntityEvent(int entityId, int status) {
                LinkBedrockSession join = state.joinSession;
                if (join == null) {
                    return;
                }
                // Living death / hurt status for remote entities.
                if (status == 3 || status == 2) {
                    JavaEntityCombatTranslator.onEntityEvent(join, entityId, status);
                }
                // Geyser: entity_event 24–28 → PermissionLevel 0–4 on the local player.
                if (entityId == join.javaEntityId() && status >= 24 && status <= 28) {
                    int level = status - 24;
                    if (join.setJavaPermissionLevel(level)) {
                        BedrockJoinProbe.noteEvent(state.guid,
                                "java_entity_event→op_level=" + level);
                        LOG.info("JE op permission level=" + level + " user=" + state.username);
                        if (join.joinPhase() == LinkBedrockSession.JoinPhase.SPAWNED
                                && BedrockSetLocalPlayerAsInitializedTranslator
                                        .postInitAbilitiesEnabled()) {
                            BedrockSetLocalPlayerAsInitializedTranslator
                                    .sendPostInitInteract(join);
                        }
                    }
                }
            }

            @Override
            public void onLevelEvent(int eventId, double x, double y, double z, int data) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaSoundTranslator.onLevelEvent(join, eventId, x, y, z, data);
                }
            }

            @Override
            public void onSound(String soundId, double x, double y, double z) {
                LinkBedrockSession join = state.joinSession;
                if (join != null) {
                    JavaSoundTranslator.onSound(join, soundId, x, y, z);
                }
            }

            @Override
            public void onDisconnect(String reason) {
                BedrockJoinProbe.noteEvent(state.guid, "java_downstream_disconnect " + reason);
                LOG.info("JE downstream end user=" + state.username + " reason=" + reason);
            }
        };

        java.net.InetSocketAddress backend = BedrockSessionHostTransfer.resolveBackend(host, user);
        state.downstream = new JavaDownstreamClient(
                host.group,
                backend,
                user,
                javaUuid,
                clientIp,
                host.config.linkHome(),
                listener);
        state.joinSession.setDownstream(state.downstream);
        String backendStr = backend.getHostString() + ":" + backend.getPort();
        // SUMMARY + timeline — playable checklist requires java_backend=127.0.0.1:25566
        BedrockJoinProbe.noteJavaBackend(state.guid, backendStr);
        state.downstream.connect();
        LOG.info("BE Phase 2 started JavaDownstream → " + backendStr
                + " user=" + user + " uuid=" + javaUuid
                + " (must match JE lobby for same world)");
        BedrockJoinProbe.noteEvent(state.guid,
                "java_downstream_connect host=" + backend.getHostString()
                        + " port=" + backend.getPort());

    }
}
