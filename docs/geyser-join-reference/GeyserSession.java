/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  io.netty.channel.Channel
 *  io.netty.channel.EventLoop
 *  lombok.Generated
 *  org.checkerframework.checker.index.qual.Positive
 *  org.checkerframework.checker.nullness.qual.MonotonicNonNull
 *  org.checkerframework.checker.nullness.qual.NonNull
 *  org.checkerframework.checker.nullness.qual.Nullable
 *  org.checkerframework.common.value.qual.IntRange
 */
package org.geysermc.geyser.session;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Generated;
import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.java.exception.MinecraftProfileNotFoundException;
import net.raphimc.minecraftauth.java.model.MinecraftProfile;
import net.raphimc.minecraftauth.java.model.MinecraftToken;
import net.raphimc.minecraftauth.util.MinecraftAuth4To5Migrator;
import org.checkerframework.checker.index.qual.Positive;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.common.value.qual.IntRange;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector2i;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.netty.channel.raknet.RakChildChannel;
import org.cloudburstmc.netty.handler.codec.raknet.common.RakSessionCodec;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer;
import org.cloudburstmc.protocol.bedrock.data.ChatRestrictionLevel;
import org.cloudburstmc.protocol.bedrock.data.ExperimentData;
import org.cloudburstmc.protocol.bedrock.data.GamePublishSetting;
import org.cloudburstmc.protocol.bedrock.data.GameRuleData;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.PlayerPermission;
import org.cloudburstmc.protocol.bedrock.data.SerializableVoxelShape;
import org.cloudburstmc.protocol.bedrock.data.ServerConfigurationJoinInfo;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.data.SpawnBiomeType;
import org.cloudburstmc.protocol.bedrock.data.biome.BiomeDefinitions;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission;
import org.cloudburstmc.protocol.bedrock.data.command.SoftEnumUpdateType;
import org.cloudburstmc.protocol.bedrock.data.definitions.DimensionDefinition;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.CraftingRecipeData;
import org.cloudburstmc.protocol.bedrock.packet.AvailableEntityIdentifiersPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BiomeDefinitionListPacket;
import org.cloudburstmc.protocol.bedrock.packet.CameraPresetsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ChunkRadiusUpdatedPacket;
import org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.DimensionDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.GameRulesChangedPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetCommandsEnabledPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetPlayerGameTypePacket;
import org.cloudburstmc.protocol.bedrock.packet.SetTimePacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.SyncEntityPropertyPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.bedrock.packet.TransferPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAdventureSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateClientInputLocksPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateSoftEnumPacket;
import org.cloudburstmc.protocol.bedrock.packet.VoxelShapesPacket;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;
import org.geysermc.api.util.BedrockPlatform;
import org.geysermc.api.util.InputMode;
import org.geysermc.api.util.UiProfile;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.util.FormBuilder;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.bedrock.camera.CameraData;
import org.geysermc.geyser.api.bedrock.camera.CameraShake;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.entity.EntityData;
import org.geysermc.geyser.api.entity.type.player.GeyserPlayerEntity;
import org.geysermc.geyser.api.event.bedrock.SessionLoginEvent;
import org.geysermc.geyser.api.network.RemoteServer;
import org.geysermc.geyser.api.skin.SkinData;
import org.geysermc.geyser.api.util.PlatformType;
import org.geysermc.geyser.command.CommandRegistry;
import org.geysermc.geyser.command.GeyserCommandSource;
import org.geysermc.geyser.entity.EntitySpectateHelper;
import org.geysermc.geyser.entity.GeyserEntityData;
import org.geysermc.geyser.entity.VanillaEntities;
import org.geysermc.geyser.entity.attribute.GeyserAttributeType;
import org.geysermc.geyser.entity.type.BoatEntity;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.ItemFrameEntity;
import org.geysermc.geyser.entity.type.Tickable;
import org.geysermc.geyser.entity.type.player.PlayerEntity;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.entity.vehicle.ClientVehicle;
import org.geysermc.geyser.erosion.AbstractGeyserboundPacketHandler;
import org.geysermc.geyser.erosion.ErosionCancellationException;
import org.geysermc.geyser.erosion.GeyserboundHandshakePacketHandler;
import org.geysermc.geyser.event.type.SessionDisconnectEventImpl;
import org.geysermc.geyser.impl.camera.CameraDefinitions;
import org.geysermc.geyser.impl.camera.GeyserCameraData;
import org.geysermc.geyser.input.InputLocksFlag;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.inventory.Inventory;
import org.geysermc.geyser.inventory.InventoryHolder;
import org.geysermc.geyser.inventory.PlayerInventory;
import org.geysermc.geyser.inventory.recipe.GeyserRecipe;
import org.geysermc.geyser.inventory.recipe.GeyserSmithingRecipe;
import org.geysermc.geyser.inventory.recipe.GeyserStonecutterData;
import org.geysermc.geyser.inventory.recipe.TrimRecipes;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.BlockItem;
import org.geysermc.geyser.level.BedrockDimension;
import org.geysermc.geyser.level.JavaDimension;
import org.geysermc.geyser.level.gamerule.GameRuleHandler;
import org.geysermc.geyser.level.physics.CollisionManager;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.network.netty.LocalSession;
import org.geysermc.geyser.platform.spigot.shaded.it.unimi.dsi.fastutil.Pair;
import org.geysermc.geyser.platform.spigot.shaded.it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.geysermc.geyser.platform.spigot.shaded.it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import org.geysermc.geyser.platform.spigot.shaded.it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.geysermc.geyser.platform.spigot.shaded.it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.geysermc.geyser.platform.spigot.shaded.it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.geysermc.geyser.platform.spigot.shaded.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.geysermc.geyser.platform.spigot.shaded.net.kyori.adventure.key.Key;
import org.geysermc.geyser.platform.spigot.shaded.net.kyori.adventure.text.Component;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.registry.type.ItemMappings;
import org.geysermc.geyser.session.DownstreamSession;
import org.geysermc.geyser.session.GeyserSessionAdapter;
import org.geysermc.geyser.session.PendingMicrosoftAuthentication;
import org.geysermc.geyser.session.UpstreamSession;
import org.geysermc.geyser.session.auth.AuthData;
import org.geysermc.geyser.session.auth.BedrockClientData;
import org.geysermc.geyser.session.cache.AdvancementsCache;
import org.geysermc.geyser.session.cache.BlockBreakHandler;
import org.geysermc.geyser.session.cache.BookEditCache;
import org.geysermc.geyser.session.cache.BundleCache;
import org.geysermc.geyser.session.cache.ChunkCache;
import org.geysermc.geyser.session.cache.ComponentCache;
import org.geysermc.geyser.session.cache.EntityCache;
import org.geysermc.geyser.session.cache.EntityEffectCache;
import org.geysermc.geyser.session.cache.FormCache;
import org.geysermc.geyser.session.cache.InputCache;
import org.geysermc.geyser.session.cache.LodestoneCache;
import org.geysermc.geyser.session.cache.PistonCache;
import org.geysermc.geyser.session.cache.PreferencesCache;
import org.geysermc.geyser.session.cache.RegistryCache;
import org.geysermc.geyser.session.cache.SkullCache;
import org.geysermc.geyser.session.cache.StructureBlockCache;
import org.geysermc.geyser.session.cache.TagCache;
import org.geysermc.geyser.session.cache.TeleportCache;
import org.geysermc.geyser.session.cache.WorldBorder;
import org.geysermc.geyser.session.cache.WorldCache;
import org.geysermc.geyser.session.cache.registry.JavaRegistries;
import org.geysermc.geyser.session.cache.tags.DialogTag;
import org.geysermc.geyser.session.cache.waypoint.GeyserWaypoint;
import org.geysermc.geyser.session.cache.waypoint.WaypointCache;
import org.geysermc.geyser.session.dialog.BuiltInDialog;
import org.geysermc.geyser.session.dialog.Dialog;
import org.geysermc.geyser.session.dialog.DialogManager;
import org.geysermc.geyser.skin.SkinManager;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.translator.inventory.InventoryTranslator;
import org.geysermc.geyser.translator.text.MessageTranslator;
import org.geysermc.geyser.util.ChunkUtils;
import org.geysermc.geyser.util.CooldownUtils;
import org.geysermc.geyser.util.EntityUtils;
import org.geysermc.geyser.util.GeyserIntegratedPackUtil;
import org.geysermc.geyser.util.InventoryUtils;
import org.geysermc.geyser.util.LoginEncryptionUtils;
import org.geysermc.geyser.util.MathUtils;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.BuiltinFlags;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.network.session.ClientNetworkSession;
import org.geysermc.mcprotocollib.protocol.ClientListener;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.ServerLink;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Pose;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.HandPreference;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.ResolvableProfile;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ChatVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ParticleStatus;
import org.geysermc.mcprotocollib.protocol.data.game.setting.SkinPart;
import org.geysermc.mcprotocollib.protocol.data.game.statistic.CustomStatistic;
import org.geysermc.mcprotocollib.protocol.data.game.statistic.Statistic;
import org.geysermc.mcprotocollib.protocol.data.handshake.HandshakeIntent;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.serverbound.ServerboundAcceptCodeOfConductPacket;
import org.geysermc.mcprotocollib.protocol.packet.cookie.serverbound.ServerboundCookieResponsePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandSignedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundCustomQueryAnswerPacket;

public class GeyserSession
implements GeyserConnection,
GeyserCommandSource {
    private static final String UNKNOWN_LOG_NAME = "This account";
    private final GeyserImpl geyser;
    private final UpstreamSession upstream;
    private DownstreamSession downstream;
    private final EventLoop tickEventLoop;
    private AuthData authData;
    private BedrockClientData clientData;
    private List<String> certChainData;
    private String token;
    private volatile @NonNull AbstractGeyserboundPacketHandler erosionHandler;
    private RemoteServer remoteServer;
    private final SessionPlayerEntity playerEntity;
    private final AdvancementsCache advancementsCache;
    private final BookEditCache bookEditCache;
    private final BundleCache bundleCache;
    private final ChunkCache chunkCache;
    private final ComponentCache componentCache;
    private final EntityCache entityCache;
    private final EntityEffectCache effectCache;
    private final FormCache formCache;
    private final GameRuleHandler gameRuleHandler;
    private final InputCache inputCache;
    private final LodestoneCache lodestoneCache;
    private final PistonCache pistonCache;
    private final PreferencesCache preferencesCache;
    private final RegistryCache registryCache;
    private final SkullCache skullCache;
    private final StructureBlockCache structureBlockCache;
    private final TagCache tagCache;
    private final WaypointCache waypointCache;
    private final WorldCache worldCache;
    private BlockBreakHandler blockBreakHandler;
    private TeleportCache unconfirmedTeleport;
    private @Nullable Entity spectatedEntity;
    private EntitySpectateHelper.SpectateMode spectateMode = EntitySpectateHelper.SpectateMode.FIRST_PERSON;
    private final WorldBorder worldBorder;
    private boolean isInWorldBorderWarningArea = false;
    private final InventoryHolder<PlayerInventory> playerInventoryHolder;
    private @Nullable InventoryHolder<? extends Inventory> inventoryHolder;
    private final DialogManager dialogManager = new DialogManager(this);
    private List<ServerLink> serverLinks = List.of();
    private List<String> knownCommands = List.of();
    private List<String> restrictedCommands = List.of();
    private boolean closingInventory;
    private int pendingOrCurrentBedrockInventoryId = -1;
    private boolean needAttackCooldownClear = false;
    private final AtomicInteger itemNetId = new AtomicInteger(2);
    private ScheduledFuture<?> containerOutputFuture;
    private final CollisionManager collisionManager;
    private BlockMappings blockMappings;
    private ItemMappings itemMappings;
    private final Map<Vector3i, ItemFrameEntity> itemFrameCache = new Object2ObjectOpenHashMap<Vector3i, ItemFrameEntity>();
    private final Map<UUID, ResolvableProfile> playerWithCustomHeads = new Object2ObjectOpenHashMap<UUID, ResolvableProfile>();
    private boolean droppingLecternBook;
    private Vector2i lastChunkPosition = null;
    private int clientRenderDistance = -1;
    private int serverRenderDistance = -1;
    protected boolean sentSpawnPacket;
    private @Nullable ServerConfigurationJoinInfo serverJoinInfo = null;
    boolean loggedIn;
    boolean loggingIn;
    private boolean spawned;
    private volatile boolean closed;
    private GameMode gameMode = GameMode.SURVIVAL;
    private Key worldName = null;
    private String[] levels;
    private boolean sneaking;
    private boolean shouldSendSneak;
    private Pose pose = Pose.STANDING;
    private boolean sprinting;
    private BedrockDimension bedrockOverworldDimension = BedrockDimension.OVERWORLD;
    private @MonotonicNonNull JavaDimension dimensionType = null;
    private BedrockDimension bedrockDimension = this.bedrockOverworldDimension;
    private Vector3i lastBlockPlacePosition;
    private BlockItem lastBlockPlaced;
    private boolean interacting;
    private Vector3i lastInteractionBlockPosition = Vector3i.ZERO;
    private int lastInteractionBlockFace = -1;
    private Vector3f lastInteractionPlayerPosition = Vector3f.ZERO;
    private Entity mouseoverEntity;
    private final Int2ObjectMap<List<String>> javaToBedrockRecipeIds = new Int2ObjectOpenHashMap<List<String>>();
    private final Int2ObjectMap<GeyserRecipe> craftingRecipes = new Int2ObjectOpenHashMap<GeyserRecipe>();
    private Pair<CraftingRecipeData, GeyserRecipe> lastCreatedRecipe = null;
    private final AtomicInteger lastRecipeNetId;
    private boolean cleanRecipesRequired = true;
    private Int2ObjectMap<GeyserStonecutterData> stonecutterRecipes = Int2ObjectMaps.emptyMap();
    private final List<GeyserSmithingRecipe> smithingRecipes = new ArrayList<GeyserSmithingRecipe>();
    private final TrimRecipes trimRecipes = new TrimRecipes();
    private boolean emulatePost1_13Logic = true;
    private boolean emulatePost1_16Logic = true;
    private boolean emulatePost1_18Logic = true;
    private boolean oldSmithingTable = false;
    private boolean isUsingExperimentalMinecartLogic = false;
    private boolean hasFishingRodCast = false;
    private double attackSpeed = 4.0;
    private long lastHitTime;
    private boolean steeringLeft;
    private boolean steeringRight;
    private boolean inClientPredictedVehicle;
    private long lastChargedProjectilesTime;
    private long lastInteractionTime;
    private boolean placedBucket;
    private @Nullable Vector3i lastLowerDoorPosition = null;
    private int armAnimationTicks = -1;
    private int lastAirHitTick;
    private final List<Long> attachedFireworkRockets = new CopyOnWriteArrayList<Long>();
    private boolean shouldClientTickClock = true;
    private boolean reducedDebugInfo = false;
    private int opPermissionLevel = 0;
    private boolean canFly = false;
    private boolean flying = false;
    private boolean noClip = false;
    private boolean instabuild = false;
    private float flySpeed;
    private float walkSpeed;
    private float rainStrength = 0.0f;
    private float thunderStrength = 0.0f;
    private final Object2IntMap<Statistic> statistics = new Object2IntOpenHashMap<Statistic>(0);
    private boolean waitingForStatistics = false;
    private boolean advancedTooltips = false;
    private ScheduledFuture<?> tickThread = null;
    private int ticks;
    private long clientTicks;
    private long gameTicks;
    private long dayTimeTicks;
    private double partialTimeTick;
    private float clockRate = 0.0f;
    private ScheduledFuture<?> mountVehicleScheduledFuture = null;
    private final Queue<Runnable> latencyPingCache = new ConcurrentLinkedQueue<Runnable>();
    private final List<BedrockPacket> queuedImmediatelyPackets = new ArrayList<BedrockPacket>();
    private @Nullable ItemData currentBook = null;
    private Map<String, byte[]> cookies = new Object2ObjectOpenHashMap<String, byte[]>();
    private final GeyserCameraData cameraData;
    private final GeyserEntityData entityData;
    private MinecraftProtocol protocol;
    private int nanosecondsPerTick = 50000000;
    private float millisecondsPerTick = 50.0f;
    private boolean tickingFrozen = false;
    private int stepTicks = 0;
    private boolean allowVibrantVisuals = true;
    private boolean hasAcceptedCodeOfConduct = false;
    private boolean integratedPackActive = false;
    private final Set<InputLocksFlag> inputLocksSet = EnumSet.noneOf(InputLocksFlag.class);
    private boolean inputLockDirty;
    private boolean pendingSpectator = false;
    private static final Ability[] USED_ABILITIES = Ability.values();
    private static final List<SkinPart> SKIN_PARTS = Arrays.asList(SkinPart.values());

    public GeyserSession(GeyserImpl geyser, BedrockServerSession bedrockServerSession, EventLoop tickEventLoop) {
        this.geyser = geyser;
        this.upstream = new UpstreamSession(bedrockServerSession);
        this.tickEventLoop = tickEventLoop;
        this.erosionHandler = new GeyserboundHandshakePacketHandler(this);
        this.advancementsCache = new AdvancementsCache(this);
        this.bookEditCache = new BookEditCache(this);
        this.bundleCache = new BundleCache(this);
        this.chunkCache = new ChunkCache(this);
        this.componentCache = new ComponentCache(this);
        this.entityCache = new EntityCache(this);
        this.effectCache = new EntityEffectCache();
        this.formCache = new FormCache(this);
        this.inputCache = new InputCache(this);
        this.lodestoneCache = new LodestoneCache();
        this.pistonCache = new PistonCache(this);
        this.preferencesCache = new PreferencesCache(this);
        this.registryCache = new RegistryCache(this);
        this.skullCache = new SkullCache(this);
        this.structureBlockCache = new StructureBlockCache();
        this.tagCache = new TagCache(this);
        this.waypointCache = new WaypointCache(this);
        this.worldCache = new WorldCache(this);
        this.cameraData = new GeyserCameraData(this);
        this.entityData = new GeyserEntityData(this);
        this.collisionManager = new CollisionManager(this);
        this.gameRuleHandler = new GameRuleHandler(this);
        this.blockBreakHandler = new BlockBreakHandler(this);
        this.worldBorder = new WorldBorder(this);
        this.playerEntity = new SessionPlayerEntity(this);
        this.collisionManager.updatePlayerBoundingBox(this.playerEntity.position());
        this.playerInventoryHolder = new InventoryHolder(this, new PlayerInventory(this), InventoryTranslator.PLAYER_INVENTORY_TRANSLATOR);
        this.inventoryHolder = null;
        this.lastRecipeNetId = new AtomicInteger(InventoryUtils.LAST_RECIPE_NET_ID + 1);
        this.spawned = false;
        this.loggedIn = false;
        this.remoteServer = geyser.defaultRemoteServer();
    }

    public void connect() {
        int minY = BedrockDimension.OVERWORLD.minY();
        int maxY = BedrockDimension.OVERWORLD.maxY();
        for (JavaDimension javaDimension : this.registryCache.registry(JavaRegistries.DIMENSION_TYPE).values()) {
            if (javaDimension.bedrockId() != 0) continue;
            minY = Math.min(minY, javaDimension.minY());
            maxY = Math.max(maxY, javaDimension.minY() + javaDimension.height());
        }
        minY = Math.max(minY, -512);
        maxY = Math.min(maxY, 512);
        if (minY < BedrockDimension.OVERWORLD.minY() || maxY > BedrockDimension.OVERWORLD.maxY()) {
            boolean isInOverworld = this.bedrockDimension == this.bedrockOverworldDimension;
            this.bedrockOverworldDimension = new BedrockDimension(minY, maxY - minY, true, 0);
            if (isInOverworld) {
                this.bedrockDimension = this.bedrockOverworldDimension;
            }
            this.geyser.getLogger().debug("Extending overworld dimension to " + minY + " - " + maxY);
            DimensionDataPacket dimensionDataPacket = new DimensionDataPacket();
            dimensionDataPacket.getDefinitions().add(new DimensionDefinition("minecraft:overworld", maxY, minY, 5, 3, GeyserIntegratedPackUtil.INTEGRATED_PACK_UUID));
            this.upstream.sendPacket(dimensionDataPacket);
        }
        this.startGame();
        this.sentSpawnPacket = true;
        this.syncEntityProperties();
        ItemComponentPacket componentPacket = new ItemComponentPacket();
        componentPacket.getItems().addAll(this.itemMappings.getItemDefinitions().values());
        this.upstream.sendPacket(componentPacket);
        ChunkUtils.sendEmptyChunks(this, this.playerEntity.position().toInt(), 0, false);
        this.sendRegistryDefinitions();
        this.sendInitialPlayerState();
        this.sendInitialGameRules();
        this.resetTimeParameters();
        if (this.pendingSpectator) {
            this.pendingSpectator = false;
            SetPlayerGameTypePacket gameTypePacket = new SetPlayerGameTypePacket();
            gameTypePacket.setGamemode(GameType.SURVIVAL_VIEWER.ordinal());
            this.upstream.sendPacket(gameTypePacket);
        }
    }

    private void sendRegistryDefinitions() {
        BiomeDefinitionListPacket biomeDefinitionListPacket = new BiomeDefinitionListPacket();
        biomeDefinitionListPacket.setBiomes((BiomeDefinitions)Registries.BIOMES.get());
        this.upstream.sendPacket(biomeDefinitionListPacket);
        AvailableEntityIdentifiersPacket entityPacket = new AvailableEntityIdentifiersPacket();
        entityPacket.setIdentifiers((NbtMap)Registries.BEDROCK_ENTITY_IDENTIFIERS.get());
        this.upstream.sendPacket(entityPacket);
        CameraPresetsPacket cameraPresetsPacket = new CameraPresetsPacket();
        cameraPresetsPacket.getPresets().addAll(CameraDefinitions.CAMERA_PRESETS);
        this.upstream.sendPacket(cameraPresetsPacket);
        CreativeContentPacket creativePacket = new CreativeContentPacket();
        creativePacket.getContents().addAll(this.itemMappings.getCreativeItems());
        creativePacket.getGroups().addAll(this.itemMappings.getCreativeItemGroups());
        this.upstream.sendPacket(creativePacket);
    }

    private void sendInitialPlayerState() {
        PlayStatusPacket playStatusPacket = new PlayStatusPacket();
        playStatusPacket.setStatus(PlayStatusPacket.Status.PLAYER_SPAWN);
        this.upstream.sendPacket(playStatusPacket);
        SetCommandsEnabledPacket setCommandsEnabledPacket = new SetCommandsEnabledPacket();
        setCommandsEnabledPacket.setCommandsEnabled(!this.geyser.config().gameplay().xboxAchievementsEnabled());
        this.upstream.sendPacket(setCommandsEnabledPacket);
        UpdateAttributesPacket attributesPacket = new UpdateAttributesPacket();
        attributesPacket.setRuntimeEntityId(this.getPlayerEntity().geyserId());
        attributesPacket.setAttributes(Collections.singletonList(GeyserAttributeType.MOVEMENT_SPEED.getAttribute()));
        this.upstream.sendPacket(attributesPacket);
    }

    private void sendInitialGameRules() {
        GameRulesChangedPacket gamerulePacket = new GameRulesChangedPacket();
        gamerulePacket.getGameRules().add(new GameRuleData<Boolean>("naturalregeneration", false));
        gamerulePacket.getGameRules().add(new GameRuleData<Boolean>("keepinventory", true));
        gamerulePacket.getGameRules().add(new GameRuleData<Integer>("spawnradius", 0));
        gamerulePacket.getGameRules().add(new GameRuleData<Boolean>("recipesunlock", true));
        if (!GeyserWaypoint.uses26_10WaypointPacket(this)) {
            gamerulePacket.getGameRules().add(new GameRuleData<Boolean>("locatorBar", false));
        } else {
            gamerulePacket.getGameRules().add(new GameRuleData<Boolean>("locatorBar", true));
        }
        this.upstream.sendPacket(gamerulePacket);
    }

    public void authenticate(String username) {
        if (this.loggedIn) {
            this.geyser.getLogger().severe(GeyserLocale.getLocaleStringLog("geyser.auth.already_loggedin", username));
            return;
        }
        this.loggingIn = true;
        this.protocol = new MinecraftProtocol(username.replace(' ', '_'));
        try {
            this.connectDownstream();
        }
        catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void authenticateWithAuthChain(String authChain) {
        if (this.loggedIn) {
            this.geyser.getLogger().severe(GeyserLocale.getLocaleStringLog("geyser.auth.already_loggedin", this.getAuthData() == null ? UNKNOWN_LOG_NAME : this.getAuthData().name()));
            return;
        }
        this.loggingIn = true;
        CompletableFuture.supplyAsync(() -> {
            MinecraftToken mcToken;
            MinecraftProfile mcProfile;
            JavaAuthManager authManager;
            try {
                JsonObject parsedAuthChain = (JsonObject)GeyserImpl.GSON.fromJson(authChain, JsonObject.class);
                if (parsedAuthChain.has("mcProfile")) {
                    parsedAuthChain = MinecraftAuth4To5Migrator.migrateJavaSave(parsedAuthChain, GeyserImpl.OAUTH_CONFIG);
                }
                authManager = JavaAuthManager.fromJson(PendingMicrosoftAuthentication.AUTH_CLIENT, parsedAuthChain);
                mcProfile = authManager.getMinecraftProfile().getUpToDate();
                mcToken = authManager.getMinecraftToken().getUpToDate();
            }
            catch (Exception e) {
                this.geyser.getLogger().error("Error while attempting to use auth chain for " + this.bedrockUsername() + "!", e);
                return Boolean.FALSE;
            }
            this.protocol = new MinecraftProtocol(new GameProfile(mcProfile.getId(), mcProfile.getName()), mcToken.getToken());
            this.geyser.saveAuthChain(this.bedrockUsername(), GeyserImpl.GSON.toJson((JsonElement)JavaAuthManager.toJson(authManager)));
            return Boolean.TRUE;
        }).whenComplete((successful, ex) -> {
            if (this.closed) {
                return;
            }
            if (successful == Boolean.FALSE) {
                this.connect();
                LoginEncryptionUtils.buildAndShowTokenExpiredWindow(this);
                return;
            }
            try {
                this.connectDownstream();
            }
            catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    public void authenticateWithMicrosoftCode() {
        this.authenticateWithMicrosoftCode(false);
    }

    public void authenticateWithMicrosoftCode(boolean offlineAccess) {
        if (this.loggedIn) {
            this.geyser.getLogger().severe(GeyserLocale.getLocaleStringLog("geyser.auth.already_loggedin", this.getAuthData() == null ? UNKNOWN_LOG_NAME : this.getAuthData().name()));
            return;
        }
        this.loggingIn = true;
        SetTimePacket packet = new SetTimePacket();
        packet.setTime(16000);
        this.sendUpstreamPacket(packet);
        PendingMicrosoftAuthentication.AuthenticationTask task = this.geyser.getPendingMicrosoftAuthentication().getOrCreateTask(this.getAuthData().xuid());
        if (task.getAuthentication() != null && task.getAuthentication().isDone()) {
            this.onMicrosoftLoginComplete(task);
        } else {
            task.resetRunningFlow();
            task.performLoginAttempt(offlineAccess, code -> {
                if (!this.closed) {
                    LoginEncryptionUtils.buildAndShowMicrosoftCodeWindow(this, code);
                }
            }).handle((r, e) -> this.onMicrosoftLoginComplete(task));
        }
    }

    public boolean onMicrosoftLoginComplete(PendingMicrosoftAuthentication.AuthenticationTask task) {
        if (this.closed) {
            return false;
        }
        task.cleanup();
        return ((CompletableFuture)task.getAuthentication().handle((result, ex) -> {
            if (ex != null) {
                CompletionException ce;
                this.geyser.getLogger().error("Failed to log in with Microsoft code!", (Throwable)ex);
                if (ex instanceof CompletionException && (ce = (CompletionException)ex).getCause() instanceof MinecraftProfileNotFoundException) {
                    this.disconnect(GeyserLocale.getPlayerLocaleString("geyser.network.remote.invalid_account", this.locale()));
                } else {
                    this.disconnect(ex.toString());
                }
                return false;
            }
            MinecraftProfile mcProfile = result.getMinecraftProfile().getCached();
            MinecraftToken mcToken = result.getMinecraftToken().getCached();
            this.protocol = new MinecraftProtocol(new GameProfile(mcProfile.getId(), mcProfile.getName()), mcToken.getToken());
            try {
                this.connectDownstream();
            }
            catch (Throwable t) {
                t.printStackTrace();
                return false;
            }
            this.geyser.saveAuthChain(this.bedrockUsername(), GeyserImpl.GSON.toJson((JsonElement)JavaAuthManager.toJson(result)));
            return true;
        })).getNow(false);
    }

    private void connectDownstream() {
        ClientNetworkSession downstream;
        SessionLoginEvent loginEvent = new SessionLoginEvent(this, this.remoteServer, new Object2ObjectOpenHashMap<String, byte[]>());
        GeyserImpl.getInstance().eventBus().fire(loginEvent);
        if (loginEvent.isCancelled()) {
            String disconnectReason = loginEvent.disconnectReason() == null ? "disconnect.disconnected" : loginEvent.disconnectReason();
            this.disconnect(disconnectReason);
            return;
        }
        this.cookies = loginEvent.cookies();
        this.remoteServer = this.geyser.platformType() == PlatformType.STANDALONE ? loginEvent.remoteServer() : this.remoteServer;
        this.tickThread = this.tickEventLoop.scheduleAtFixedRate(this::tick, (long)this.nanosecondsPerTick, (long)this.nanosecondsPerTick, TimeUnit.NANOSECONDS);
        if (this.geyser.getBootstrap().getSocketAddress() != null) {
            downstream = new LocalSession(this.geyser.getBootstrap().getSocketAddress(), this.upstream.getAddress().getAddress().getHostAddress(), this.protocol, (Executor)this.tickEventLoop);
            downstream.setFlag(MinecraftConstants.CLIENT_HOST, this.remoteServer.address());
            downstream.setFlag(MinecraftConstants.CLIENT_PORT, this.remoteServer.port());
            this.downstream = new DownstreamSession(downstream);
        } else {
            downstream = new ClientNetworkSession(new InetSocketAddress(this.remoteServer.address(), this.remoteServer.port()), this.protocol, (Executor)this.tickEventLoop, null, null);
            this.downstream = new DownstreamSession(downstream);
            boolean resolveSrv = false;
            try {
                resolveSrv = this.remoteServer.resolveSrv();
            }
            catch (AbstractMethodError | NoSuchMethodError incompatibleClassChangeError) {
                // empty catch block
            }
            this.downstream.getSession().setFlag(BuiltinFlags.ATTEMPT_SRV_RESOLVE, resolveSrv);
        }
        this.downstream.getSession().setFlag(MinecraftConstants.FOLLOW_TRANSFERS, false);
        if (this.geyser.config().advanced().java().useHaproxyProtocol()) {
            downstream.setFlag(BuiltinFlags.CLIENT_PROXIED_ADDRESS, this.upstream.getAddress());
        }
        if (this.geyser.config().gameplay().forwardPlayerPing()) {
            downstream.setFlag(MinecraftConstants.AUTOMATIC_KEEP_ALIVE_MANAGEMENT, false);
        }
        downstream.setFlag(MinecraftConstants.SEND_BLANK_KNOWN_PACKS_RESPONSE, false);
        this.protocol.setUseDefaultListeners(false);
        downstream.addListener(new ClientListener(HandshakeIntent.LOGIN, Boolean.getBoolean("Geyser.ValidateDecompression")));
        downstream.addListener(new GeyserSessionAdapter(this));
        downstream.setFlag(BuiltinFlags.CLIENT_TRANSFERRING, loginEvent.transferring());
        downstream.connect(false);
    }

    public void disconnect(String reason) {
        this.disconnect(Component.text(reason));
    }

    public void disconnect(Component reason) {
        if (!this.closed) {
            this.loggedIn = false;
            SessionDisconnectEventImpl disconnectEvent = new SessionDisconnectEventImpl(this, reason);
            if (this.authData != null && this.clientData != null) {
                this.geyser.getEventBus().fire(disconnectEvent);
            }
            if (this.downstream != null) {
                if (!this.downstream.isClosed()) {
                    this.downstream.disconnect(reason);
                }
            } else {
                String address = this.geyser.config().logPlayerIpAddresses() ? this.upstream.getAddress().getAddress().toString() : "<IP address withheld>";
                this.geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.network.disconnect", address, MessageTranslator.convertMessage(reason)));
            }
            if (!this.upstream.isClosed()) {
                this.upstream.disconnect(disconnectEvent.disconnectReason());
            }
            this.geyser.getSessionManager().removeSession(this);
        }
        if (this.tickThread != null) {
            this.tickThread.cancel(false);
        }
        this.queuedImmediatelyPackets.clear();
        this.closed = true;
        this.erosionHandler.close();
    }

    public void forciblyCloseUpstream() {
        this.upstream.forciblyClose();
    }

    public void ensureInEventLoop(Runnable runnable) {
        if (this.tickEventLoop.inEventLoop()) {
            this.executeRunnable(runnable);
            return;
        }
        this.executeInEventLoop(runnable);
    }

    public void executeInEventLoop(Runnable runnable) {
        this.tickEventLoop.execute(() -> this.executeRunnable(runnable));
    }

    public ScheduledFuture<?> scheduleInEventLoop(Runnable runnable, long duration, TimeUnit timeUnit) {
        return this.tickEventLoop.schedule(() -> this.executeRunnable(() -> {
            if (!this.closed) {
                runnable.run();
            }
        }), duration, timeUnit);
    }

    public void updateTickingState(float tickRate, boolean frozen) {
        this.tickThread.cancel(false);
        this.tickingFrozen = frozen;
        tickRate = MathUtils.clamp(tickRate, 1.0f, 10000.0f);
        this.millisecondsPerTick = 1000.0f / tickRate;
        this.nanosecondsPerTick = MathUtils.ceil(1.0E9f / tickRate);
        this.tickThread = this.tickEventLoop.scheduleAtFixedRate(this::tick, (long)this.nanosecondsPerTick, (long)this.nanosecondsPerTick, TimeUnit.NANOSECONDS);
    }

    private void executeRunnable(Runnable runnable) {
        try {
            runnable.run();
        }
        catch (ErosionCancellationException e) {
            this.geyser.getLogger().debug("Caught ErosionCancellationException");
        }
        catch (Throwable e) {
            this.geyser.getLogger().error("Error thrown in " + this.bedrockUsername() + "'s event loop!", e);
        }
    }

    protected void tick() {
        try {
            int miningFatigueLevel;
            int hasteLevel;
            int swingTotalDuration;
            Entity vehicle;
            boolean gameShouldUpdate;
            boolean shouldShowFog;
            this.pistonCache.tick();
            this.worldBorder.tick();
            boolean bl = shouldShowFog = !this.worldBorder.isWithinWarningBoundaries();
            if (shouldShowFog || this.worldBorder.isCloseToBorderBoundaries()) {
                this.worldBorder.drawWall();
                if (shouldShowFog && !this.isInWorldBorderWarningArea) {
                    this.isInWorldBorderWarningArea = true;
                    this.camera().sendFog("minecraft:fog_crimson_forest");
                }
            }
            if (!shouldShowFog && this.isInWorldBorderWarningArea) {
                this.camera().removeFog("minecraft:fog_crimson_forest");
                this.isInWorldBorderWarningArea = false;
            }
            boolean bl2 = gameShouldUpdate = !this.tickingFrozen || this.stepTicks > 0;
            if (this.stepTicks > 0) {
                --this.stepTicks;
            }
            if ((vehicle = this.playerEntity.getVehicle()) instanceof ClientVehicle) {
                ClientVehicle clientVehicle = (ClientVehicle)((Object)vehicle);
                if (vehicle.isValid()) {
                    clientVehicle.getVehicleComponent().tickVehicle();
                }
            }
            Iterator<Object> it = this.entityCache.getDirtyEntities().iterator();
            while (it.hasNext()) {
                it.next().updateBedrockMetadata();
                it.remove();
            }
            for (Tickable entity : this.entityCache.getTickableEntities()) {
                entity.drawTick();
                if (!gameShouldUpdate) continue;
                entity.tick();
            }
            EntitySpectateHelper.tick(this);
            if (this.armAnimationTicks >= 0 && ++this.armAnimationTicks >= (swingTotalDuration = (hasteLevel = Math.max(this.effectCache.getHaste(), this.effectCache.getConduitPower())) > 0 ? 6 - hasteLevel : ((miningFatigueLevel = this.effectCache.getMiningFatigue()) > 0 ? 6 + miningFatigueLevel * 2 : 6))) {
                if (this.sneaking && this.attemptToBlock()) {
                    this.playerEntity.updateBedrockMetadata();
                }
                this.armAnimationTicks = -1;
            }
            this.bundleCache.tick();
            this.dialogManager.tick();
            this.waypointCache.tick();
            this.upstream.getSession().getPeer().sendPacketsImmediately(0, 0, this.queuedImmediatelyPackets.toArray(new BedrockPacket[0]));
            this.queuedImmediatelyPackets.clear();
            CooldownUtils.tickCooldown(this);
        }
        catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        ++this.ticks;
        this.partialTimeTick += (double)this.clockRate;
        if (this.partialTimeTick >= 1.0) {
            long flooredPartialTick = (long)Math.floor(this.partialTimeTick);
            this.dayTimeTicks += flooredPartialTick;
            this.partialTimeTick -= (double)flooredPartialTick;
            if (!this.isShouldClientTickClock()) {
                this.synchronizeTime();
            }
        }
    }

    public void synchronizeTime() {
        SetTimePacket setTimePacket = new SetTimePacket();
        setTimePacket.setTime((int)(Math.abs(this.dayTimeTicks) % 192000L));
        this.sendUpstreamPacket(setTimePacket);
    }

    public void sendJsonUIData(String key, String value) {
        String text = "geyseropt:" + key + ":" + value;
        TextPacket textPacket = new TextPacket();
        textPacket.setPlatformChatId("");
        textPacket.setSourceName("");
        textPacket.setXuid("");
        textPacket.setType(TextPacket.Type.TIP);
        textPacket.setNeedsTranslation(false);
        textPacket.setMessage(text);
        this.sendUpstreamPacket(textPacket);
    }

    public void startSneaking(boolean updateMetaData) {
        if (this.armAnimationTicks < 0) {
            this.attemptToBlock();
        }
        this.setSneaking(true, updateMetaData);
    }

    public void stopSneaking(boolean updateMetaData) {
        this.disableBlocking();
        this.setSneaking(false, updateMetaData);
    }

    private void setSneaking(boolean sneaking, boolean update) {
        this.sneaking = sneaking;
        this.playerEntity.setFlag(EntityFlag.SNEAKING, sneaking);
        this.collisionManager.updateScaffoldingFlags(false);
        if (update) {
            this.playerEntity.updateBedrockMetadata();
        }
        if (this.mouseoverEntity != null) {
            this.mouseoverEntity.updateInteractiveTag();
        }
    }

    public void setNoClip(boolean noClip) {
        if (this.noClip == noClip) {
            return;
        }
        this.noClip = noClip;
        this.sendAdventureSettings();
    }

    public void setGameMode(GameMode newGamemode) {
        boolean currentlySpectator = this.gameMode == GameMode.SPECTATOR;
        this.gameMode = newGamemode;
        this.cameraData.handleGameModeChange(currentlySpectator, newGamemode);
    }

    public void setClientData(BedrockClientData data) {
        this.clientData = data;
        this.inputCache.setInputMode(org.cloudburstmc.protocol.bedrock.data.InputMode.values()[data.getCurrentInputMode().ordinal()]);
    }

    public boolean hasFishingRodCast() {
        return this.hasFishingRodCast;
    }

    public void setFishingRodCast(boolean cast) {
        this.hasFishingRodCast = cast;
        int slot = this.getPlayerInventory().getOffsetForHotbar(this.getPlayerInventory().getHeldItemSlot());
        this.playerInventoryHolder.updateSlot(slot);
    }

    public void switchHeldSlot(int slot) {
        if (!this.spawned || slot < 0 || slot > 8 || this.playerInventoryHolder.inventory().getHeldItemSlot() == slot) {
            return;
        }
        this.bookEditCache.checkForSend();
        GeyserItemStack oldItem = this.playerInventoryHolder.inventory().getItemInHand();
        this.playerInventoryHolder.inventory().setHeldItemSlot(slot);
        ServerboundSetCarriedItemPacket setCarriedItemPacket = new ServerboundSetCarriedItemPacket(slot);
        this.sendDownstreamGamePacket(setCarriedItemPacket);
        GeyserItemStack newItem = this.playerInventoryHolder.inventory().getItemInHand();
        if (this.sneaking && newItem.is(Items.SHIELD)) {
            this.scheduleInEventLoop(() -> this.useItem(Hand.MAIN_HAND), this.nanosecondsPerTick, TimeUnit.NANOSECONDS);
        }
        if (!oldItem.isSameItem(newItem)) {
            CooldownUtils.setCooldownHitTime(this);
        }
        if (this.mouseoverEntity != null) {
            this.mouseoverEntity.updateInteractiveTag();
        }
    }

    public void useItem(Hand hand) {
        this.useItem(hand, false);
    }

    public void useItem(Hand hand, boolean useTouchRotation) {
        if (this.playerEntity.getFlag(EntityFlag.USING_ITEM)) {
            return;
        }
        if (this.playerEntity.getFlag(EntityFlag.BLOCKING)) {
            this.disableBlocking();
        }
        if (hand == Hand.MAIN_HAND && System.currentTimeMillis() - this.lastChargedProjectilesTime < 450L) {
            this.lastChargedProjectilesTime = 0L;
            this.playerInventoryHolder.updateSlot(this.playerInventoryHolder.inventory().getOffsetForHotbar(this.playerInventoryHolder.inventory().getHeldItemSlot()));
            return;
        }
        float yaw = this.playerEntity.getJavaYaw();
        float pitch = this.playerEntity.getPitch();
        if (useTouchRotation) {
            yaw = this.playerEntity.getBedrockInteractRotation().getY();
            pitch = this.playerEntity.getBedrockInteractRotation().getX();
        }
        this.sendDownstreamGamePacket(new ServerboundUseItemPacket(hand, this.worldCache.nextPredictionSequence(), yaw, pitch));
    }

    public void releaseItem() {
        ServerboundPlayerActionPacket releaseItemPacket = new ServerboundPlayerActionPacket(PlayerAction.RELEASE_USE_ITEM, Vector3i.ZERO, Direction.DOWN, 0);
        this.sendDownstreamGamePacket(releaseItemPacket);
    }

    public boolean attemptToBlock() {
        if (this.playerEntity.isInsideScaffolding()) {
            return false;
        }
        GeyserItemStack mainHandItem = this.playerInventoryHolder.inventory().getItemInHand();
        GeyserItemStack offhandItem = this.playerInventoryHolder.inventory().getOffhand();
        if (mainHandItem.is(Items.SHIELD) && !this.worldCache.hasCooldown(mainHandItem)) {
            this.useItem(Hand.MAIN_HAND);
            this.playerEntity.setFlag(EntityFlag.BLOCKING, true);
            return true;
        }
        if (offhandItem.is(Items.SHIELD) && !this.worldCache.hasCooldown(offhandItem)) {
            this.useItem(Hand.OFF_HAND);
            this.playerEntity.setFlag(EntityFlag.BLOCKING, true);
            return true;
        }
        return false;
    }

    public void activateArmAnimationTicking() {
        this.armAnimationTicks = 0;
        if (this.disableBlocking()) {
            this.playerEntity.updateBedrockMetadata();
        }
    }

    public boolean isHandsBusy() {
        return this.playerEntity.getVehicle() instanceof BoatEntity && (this.steeringRight || this.steeringLeft);
    }

    private boolean disableBlocking() {
        if (this.playerEntity.getFlag(EntityFlag.BLOCKING)) {
            ServerboundPlayerActionPacket releaseItemPacket = new ServerboundPlayerActionPacket(PlayerAction.RELEASE_USE_ITEM, Vector3i.ZERO, Direction.DOWN, 0);
            this.sendDownstreamGamePacket(releaseItemPacket);
            this.playerEntity.setFlag(EntityFlag.BLOCKING, false);
            return true;
        }
        return false;
    }

    @Override
    public void requestOffhandSwap() {
        ServerboundPlayerActionPacket swapHandsPacket = new ServerboundPlayerActionPacket(PlayerAction.SWAP_HANDS, Vector3i.ZERO, Direction.DOWN, 0);
        this.sendDownstreamGamePacket(swapHandsPacket);
    }

    @Override
    public String name() {
        return this.playerEntity != null ? this.javaUsername() : this.bedrockUsername();
    }

    @Override
    public void sendMessage(@NonNull String message) {
        TextPacket textPacket = new TextPacket();
        textPacket.setPlatformChatId("");
        textPacket.setSourceName("");
        textPacket.setXuid("");
        textPacket.setType(TextPacket.Type.CHAT);
        textPacket.setNeedsTranslation(false);
        textPacket.setMessage(message);
        this.upstream.sendPacket(textPacket);
    }

    @Override
    public boolean isConsole() {
        return false;
    }

    @Override
    public UUID playerUuid() {
        return this.javaUuid();
    }

    @Override
    public GeyserSession connection() {
        return this;
    }

    @Override
    public String locale() {
        return this.clientData != null ? this.clientData.getLanguageCode() : GeyserLocale.getDefaultLocale();
    }

    @Override
    public boolean hasPermission(String permission) {
        return this.geyser.commandRegistry().hasPermission(this, permission);
    }

    public void sendChat(String message) {
        this.sendDownstreamGamePacket(new ServerboundChatPacket(message, Instant.now().toEpochMilli(), 0L, null, 0, new BitSet(), 0));
    }

    public void sendCommandPacket(String command) {
        this.sendDownstreamGamePacket(new ServerboundChatCommandSignedPacket(command, Instant.now().toEpochMilli(), 0L, Collections.emptyList(), 0, new BitSet(), 0));
    }

    @Override
    public void sendCommand(String command) {
        String[] args;
        if (MessageTranslator.isTooLong(command, this)) {
            return;
        }
        if (CommandRegistry.STANDALONE_COMMAND_MANAGER && (args = command.split(" ")).length > 0) {
            String root = args[0];
            CommandRegistry registry = GeyserImpl.getInstance().commandRegistry();
            if (registry.rootCommands().contains(root)) {
                registry.runCommand(this, command);
                return;
            }
        }
        this.sendCommandPacket(command);
    }

    @Override
    public @NonNull String joinAddress() {
        String combined = Optional.ofNullable(this.clientData).orElseThrow().getServerAddress();
        int index = combined.lastIndexOf(":");
        return combined.substring(0, index);
    }

    @Override
    public @Positive int joinPort() {
        String combined = Optional.ofNullable(this.clientData).orElseThrow().getServerAddress();
        int index = combined.lastIndexOf(":");
        return Integer.parseInt(combined.substring(index + 1));
    }

    @Override
    public void sendSkin(@NonNull UUID player, @NonNull SkinData skinData) {
        Objects.requireNonNull(player, "player uuid must not be null!");
        Objects.requireNonNull(skinData, "skinData must not be null!");
        PlayerEntity entity = this.entityCache.getPlayerEntity(player);
        if (entity == null) {
            return;
        }
        SkinManager.sendSkinPacket(this, entity, skinData);
    }

    @Override
    public void openPauseScreenAdditions() {
        List<Dialog> additions = this.tagCache.get(DialogTag.PAUSE_SCREEN_ADDITIONS);
        if (additions.isEmpty()) {
            if (!this.serverLinks.isEmpty()) {
                this.dialogManager.openDialog(BuiltInDialog.SERVER_LINKS);
            }
        } else if (additions.size() == 1) {
            this.dialogManager.openDialog(additions.getFirst());
        } else {
            this.dialogManager.openDialog(BuiltInDialog.CUSTOM_OPTIONS);
        }
    }

    @Override
    public void openQuickActions() {
        List<Dialog> quickActions = this.tagCache.get(DialogTag.QUICK_ACTIONS);
        if (!quickActions.isEmpty()) {
            if (quickActions.size() == 1) {
                this.dialogManager.openDialog(quickActions.getFirst());
            } else {
                this.dialogManager.openDialog(BuiltInDialog.QUICK_ACTIONS);
            }
        }
    }

    public void setClientRenderDistance(int clientRenderDistance) {
        boolean newSquareToCircle;
        boolean oldSquareToCircle = this.clientRenderDistance < this.serverRenderDistance;
        this.clientRenderDistance = clientRenderDistance;
        boolean bl = newSquareToCircle = this.clientRenderDistance < this.serverRenderDistance;
        if (this.serverRenderDistance != -1 && oldSquareToCircle != newSquareToCircle) {
            this.recalculateBedrockRenderDistance();
        }
    }

    public void setServerRenderDistance(int renderDistance) {
        this.serverRenderDistance = renderDistance = Math.min(renderDistance, 96);
        this.recalculateBedrockRenderDistance();
    }

    private void recalculateBedrockRenderDistance() {
        int renderDistance = ChunkUtils.squareToCircle(this.serverRenderDistance);
        ChunkRadiusUpdatedPacket chunkRadiusUpdatedPacket = new ChunkRadiusUpdatedPacket();
        chunkRadiusUpdatedPacket.setRadius(renderDistance);
        this.upstream.sendPacket(chunkRadiusUpdatedPacket);
    }

    public InetSocketAddress getSocketAddress() {
        return this.upstream.getAddress();
    }

    public void setOpPermissionLevel(int opPermissionLevel) {
        this.opPermissionLevel = opPermissionLevel;
        if (this.gameRuleHandler.getState() == GameRuleHandler.State.SHOWN || this.gameRuleHandler.getState() == GameRuleHandler.State.WAITING) {
            this.closeForm();
        }
    }

    @Override
    public boolean sendForm(@NonNull Form form) {
        if (this.downstream != null && this.downstream.getSession().getPacketProtocol().getInboundState().equals((Object)ProtocolState.CONFIGURATION)) {
            this.prepareForConfigurationForm();
        }
        this.dialogManager.close();
        return this.doSendForm(form);
    }

    public void sendDialogForm(@NonNull Form form) {
        this.doSendForm(form);
    }

    private boolean doSendForm(@NonNull Form form) {
        if (this.formCache.hasFormOpen()) {
            this.closeForm();
        }
        this.formCache.addForm(form);
        if (this.inventoryHolder != null) {
            InventoryUtils.sendJavaContainerClose(this.inventoryHolder);
            InventoryUtils.closeInventory(this, this.inventoryHolder, true);
        }
        if (!this.isClosingInventory() && this.upstream.isInitialized()) {
            this.formCache.resendAllForms();
        }
        return true;
    }

    public void acceptCodeOfConduct() {
        if (this.hasAcceptedCodeOfConduct) {
            return;
        }
        this.hasAcceptedCodeOfConduct = true;
        this.sendDownstreamConfigurationPacket(ServerboundAcceptCodeOfConductPacket.INSTANCE);
    }

    public void prepareForConfigurationForm() {
        if (!this.sentSpawnPacket) {
            this.connect();
        }
        this.resetTimeParameters();
    }

    public @NonNull PlayerInventory getPlayerInventory() {
        return this.playerInventoryHolder.inventory();
    }

    public @Nullable Inventory getOpenInventory() {
        if (this.inventoryHolder == null) {
            return null;
        }
        return this.inventoryHolder.inventory();
    }

    @Override
    public boolean sendForm(@NonNull org.geysermc.cumulus.form.util.FormBuilder<?, ?, ?> formBuilder) {
        this.sendForm((Form)formBuilder.build());
        return true;
    }

    @Deprecated
    public void sendForm(org.geysermc.cumulus.Form<?> form) {
        this.sendForm((Form)form.newForm());
    }

    @Deprecated
    public void sendForm(FormBuilder<?, ?> formBuilder) {
        this.sendForm((org.geysermc.cumulus.Form<?>)formBuilder.build());
    }

    private void startGame() {
        this.upstream.getCodecHelper().setItemDefinitions(this.itemMappings);
        this.upstream.getCodecHelper().setBlockDefinitions(this.blockMappings);
        this.upstream.getCodecHelper().setCameraPresetDefinitions(CameraDefinitions.CAMERA_DEFINITIONS);
        if (GameProtocol.is26_20orHigher(this.protocolVersion())) {
            VoxelShapesPacket voxelShapesPacket = new VoxelShapesPacket();
            voxelShapesPacket.setNameMap(new HashMap<String, Integer>());
            voxelShapesPacket.setShapes(new ArrayList<SerializableVoxelShape>());
            this.upstream.sendPacket(voxelShapesPacket);
        }
        StartGamePacket startGamePacket = this.buildStartGamePacket();
        this.configureExperiments(startGamePacket);
        startGamePacket.setServerConfigurationJoinInfo(this.serverJoinInfo);
        if (this.playerEntity.getPropertyManager() != null) {
            startGamePacket.setPlayerPropertyData(this.playerEntity.getPropertyManager().toNbtMap("minecraft:player"));
        }
        startGamePacket.setServerId("");
        startGamePacket.setWorldId("");
        startGamePacket.setScenarioId("");
        startGamePacket.setOwnerId("");
        this.upstream.sendPacket(startGamePacket);
    }

    private StartGamePacket buildStartGamePacket() {
        StartGamePacket startGamePacket = new StartGamePacket();
        startGamePacket.setUniqueEntityId(this.playerEntity.geyserId());
        startGamePacket.setRuntimeEntityId(this.playerEntity.geyserId());
        GameType gameType = EntityUtils.toBedrockGamemode(this.gameMode);
        if (gameType == GameType.SURVIVAL_VIEWER && GameProtocol.is26_40orHigher(this.protocolVersion())) {
            gameType = GameType.SURVIVAL;
            this.pendingSpectator = true;
        }
        startGamePacket.setPlayerGameType(gameType);
        startGamePacket.setPlayerPosition(Vector3f.from(0.0f, 69.0f, 0.0f));
        startGamePacket.setRotation(Vector2f.from(1.0f, 1.0f));
        startGamePacket.setSeed(-1L);
        startGamePacket.setDimensionId(this.bedrockDimension.bedrockId());
        startGamePacket.setGeneratorId(1);
        startGamePacket.setLevelGameType(GameType.SURVIVAL);
        startGamePacket.setDifficulty(1);
        startGamePacket.setDefaultSpawn(Vector3i.ZERO);
        startGamePacket.setAchievementsDisabled(!this.geyser.config().gameplay().xboxAchievementsEnabled());
        startGamePacket.setCurrentTick(-1L);
        startGamePacket.setEduEditionOffers(0);
        startGamePacket.setEduFeaturesEnabled(false);
        startGamePacket.setRainLevel(0.0f);
        startGamePacket.setLightningLevel(0.0f);
        startGamePacket.setMultiplayerGame(true);
        startGamePacket.setBroadcastingToLan(true);
        startGamePacket.setPlatformBroadcastMode(GamePublishSetting.PUBLIC);
        startGamePacket.setXblBroadcastMode(GamePublishSetting.PUBLIC);
        startGamePacket.setCommandsEnabled(!this.geyser.config().gameplay().xboxAchievementsEnabled());
        startGamePacket.setTexturePacksRequired(false);
        startGamePacket.setBonusChestEnabled(false);
        startGamePacket.setStartingWithMap(false);
        startGamePacket.setTrustingPlayers(true);
        startGamePacket.setDefaultPlayerPermission(PlayerPermission.MEMBER);
        startGamePacket.setServerChunkTickRange(4);
        startGamePacket.setBehaviorPackLocked(false);
        startGamePacket.setResourcePackLocked(false);
        startGamePacket.setFromLockedWorldTemplate(false);
        startGamePacket.setUsingMsaGamertagsOnly(false);
        startGamePacket.setFromWorldTemplate(false);
        startGamePacket.setWorldTemplateOptionLocked(false);
        startGamePacket.setSpawnBiomeType(SpawnBiomeType.DEFAULT);
        startGamePacket.setCustomBiomeName("");
        startGamePacket.setEducationProductionId("");
        startGamePacket.setForceExperimentalGameplay(OptionalBoolean.empty());
        String serverName = this.geyser.config().gameplay().serverName();
        startGamePacket.setLevelId(serverName);
        startGamePacket.setLevelName(serverName);
        startGamePacket.setPremiumWorldTemplateId("00000000-0000-0000-0000-000000000000");
        startGamePacket.setEnchantmentSeed(0);
        startGamePacket.setMultiplayerCorrelationId("");
        startGamePacket.getBlockProperties().addAll(this.blockMappings.getBlockProperties());
        startGamePacket.setVanillaVersion("*");
        startGamePacket.setInventoriesServerAuthoritative(true);
        startGamePacket.setServerEngine("");
        startGamePacket.setPlayerPropertyData(NbtMap.EMPTY);
        startGamePacket.setWorldTemplateId(UUID.randomUUID());
        startGamePacket.setChatRestrictionLevel(ChatRestrictionLevel.NONE);
        startGamePacket.setRewindHistorySize(0);
        startGamePacket.setServerAuthoritativeBlockBreaking(true);
        return startGamePacket;
    }

    private void configureExperiments(StartGamePacket startGamePacket) {
        startGamePacket.getExperiments().add(new ExperimentData("data_driven_items", true));
        startGamePacket.getExperiments().add(new ExperimentData("upcoming_creator_features", true));
        startGamePacket.getExperiments().add(new ExperimentData("experimental_molang_features", true));
    }

    private void syncEntityProperties() {
        for (NbtMap nbtMap : (Set)Registries.BEDROCK_ENTITY_PROPERTIES.get()) {
            SyncEntityPropertyPacket syncEntityPropertyPacket = new SyncEntityPropertyPacket();
            syncEntityPropertyPacket.setData(nbtMap);
            this.upstream.sendPacket(syncEntityPropertyPacket);
        }
    }

    public int getNextItemNetId() {
        return this.itemNetId.getAndIncrement();
    }

    public void confirmTeleport(Vector3f position) {
        if (this.unconfirmedTeleport == null) {
            return;
        }
        if (this.unconfirmedTeleport.canConfirm(position)) {
            this.unconfirmedTeleport = null;
            return;
        }
        this.unconfirmedTeleport.incrementUnconfirmedFor();
        if (this.unconfirmedTeleport.shouldResend()) {
            this.unconfirmedTeleport.resetUnconfirmedFor();
            this.geyser.getLogger().debug("Resending teleport " + this.unconfirmedTeleport.getTeleportConfirmId());
            this.getPlayerEntity().moveAbsolute(this.unconfirmedTeleport.getPosition(), this.unconfirmedTeleport.getYaw(), this.unconfirmedTeleport.getPitch(), this.playerEntity.isOnGround(), true);
            if (this.unconfirmedTeleport.getTeleportType() == TeleportCache.TeleportType.KEEP_VELOCITY) {
                SetEntityMotionPacket entityMotionPacket = new SetEntityMotionPacket();
                entityMotionPacket.setRuntimeEntityId(this.playerEntity.geyserId());
                entityMotionPacket.setMotion(this.unconfirmedTeleport.getVelocity());
                this.sendUpstreamPacket(entityMotionPacket);
            }
        }
    }

    public void sendUpstreamPacket(BedrockPacket packet) {
        this.upstream.sendPacket(packet);
    }

    public void sendUpstreamPacketImmediately(BedrockPacket packet) {
        this.upstream.sendPacketImmediately(packet);
    }

    public void sendDownstreamGamePacket(Packet packet) {
        this.sendDownstreamPacket(packet, ProtocolState.GAME);
    }

    public void sendDownstreamConfigurationPacket(Packet packet) {
        this.sendDownstreamPacket(packet, ProtocolState.CONFIGURATION);
    }

    public void sendDownstreamLoginPacket(Packet packet) {
        this.sendDownstreamPacket(packet, ProtocolState.LOGIN);
    }

    public void sendDownstreamPacket(Packet packet, ProtocolState intendedState) {
        if (this.protocol == null) {
            if (this.geyser.config().debugMode()) {
                this.geyser.getLogger().debug("Tried to send downstream packet with no downstream session!");
                Thread.dumpStack();
            }
            return;
        }
        if (this.protocol.getOutboundState() != intendedState) {
            this.geyser.getLogger().debug("Tried to send " + packet.getClass().getSimpleName() + " packet while not in " + intendedState.name() + " outbound state. Current state: " + this.protocol.getOutboundState().name());
            return;
        }
        this.sendDownstreamPacket(packet);
    }

    public void sendDownstreamPacket(Packet packet) {
        if (!this.closed && this.downstream != null) {
            Channel channel = this.downstream.getSession().getChannel();
            if (channel == null) {
                this.geyser.getLogger().warning("Tried to send a packet to the Java server too early!");
                if (this.geyser.config().debugMode()) {
                    Thread.dumpStack();
                }
                return;
            }
            EventLoop eventLoop = channel.eventLoop();
            if (eventLoop.inEventLoop()) {
                this.sendDownstreamPacket0(packet);
            } else {
                eventLoop.execute(() -> this.sendDownstreamPacket0(packet));
            }
        }
    }

    private void sendDownstreamPacket0(Packet packet) {
        ProtocolState state = this.protocol.getOutboundState();
        if (state == ProtocolState.GAME || state == ProtocolState.CONFIGURATION || packet.getClass() == ServerboundCustomQueryAnswerPacket.class || packet.getClass() == ServerboundCookieResponsePacket.class) {
            this.downstream.sendPacket(packet);
        } else {
            this.geyser.getLogger().debug("Tried to send downstream packet " + packet.getClass().getSimpleName() + " before connected to the server");
        }
    }

    public void setReducedDebugInfo(boolean value) {
        this.reducedDebugInfo = value;
        this.preferencesCache.updateShowCoordinates();
    }

    public void setShouldClientTickClock(boolean shouldTick) {
        if (this.shouldClientTickClock == shouldTick) {
            return;
        }
        this.sendGameRule("dodaylightcycle", shouldTick);
        this.shouldClientTickClock = shouldTick;
    }

    public void setTimeTicks(long timeTicks, float partialTick) {
        this.dayTimeTicks = timeTicks;
        this.partialTimeTick = partialTick;
        this.synchronizeTime();
    }

    public void setClockRate(float rate) {
        this.clockRate = rate;
        this.setShouldClientTickClock(this.clockRate == 1.0f);
    }

    public void resetTimeParameters() {
        this.setGameTicks(0L);
        this.setTimeTicks(0L, 0.0f);
        this.setClockRate(0.0f);
    }

    public void sendGameRule(String gameRule, Object value) {
        GameRulesChangedPacket gameRulesChangedPacket = new GameRulesChangedPacket();
        gameRulesChangedPacket.getGameRules().add(new GameRuleData<Object>(gameRule, value));
        this.upstream.sendPacket(gameRulesChangedPacket);
    }

    public void sendAdventureSettings() {
        long bedrockId = this.playerEntity.geyserId();
        CommandPermission commandPermission = this.opPermissionLevel >= 2 ? CommandPermission.GAME_DIRECTORS : CommandPermission.ANY;
        PlayerPermission playerPermission = this.opPermissionLevel >= 2 ? PlayerPermission.OPERATOR : PlayerPermission.MEMBER;
        boolean spectator = this.gameMode == GameMode.SPECTATOR;
        boolean worldImmutable = this.gameMode == GameMode.ADVENTURE || spectator;
        UpdateAdventureSettingsPacket adventureSettingsPacket = new UpdateAdventureSettingsPacket();
        adventureSettingsPacket.setNoMvP(false);
        adventureSettingsPacket.setNoPvM(false);
        adventureSettingsPacket.setImmutableWorld(worldImmutable);
        adventureSettingsPacket.setShowNameTags(false);
        adventureSettingsPacket.setAutoJump(true);
        this.sendUpstreamPacket(adventureSettingsPacket);
        UpdateAbilitiesPacket updateAbilitiesPacket = new UpdateAbilitiesPacket();
        updateAbilitiesPacket.setUniqueEntityId(bedrockId);
        updateAbilitiesPacket.setCommandPermission(commandPermission);
        updateAbilitiesPacket.setPlayerPermission(playerPermission);
        AbilityLayer abilityLayer = new AbilityLayer();
        Set<Ability> abilities = abilityLayer.getAbilityValues();
        if (this.canFly) {
            abilities.add(Ability.MAY_FLY);
        }
        abilities.add(Ability.BUILD);
        abilities.add(Ability.MINE);
        abilities.add(Ability.DOORS_AND_SWITCHES);
        abilities.add(Ability.OPEN_CONTAINERS);
        if (this.gameMode == GameMode.CREATIVE) {
            abilities.add(Ability.INSTABUILD);
        }
        if (this.noClip && !spectator) {
            abilities.add(Ability.NO_CLIP);
        }
        if (commandPermission == CommandPermission.GAME_DIRECTORS) {
            abilities.add(Ability.OPERATOR_COMMANDS);
        }
        if (this.flying || spectator) {
            if (spectator && !this.flying) {
                this.flying = true;
                ServerboundPlayerAbilitiesPacket abilitiesPacket = new ServerboundPlayerAbilitiesPacket(true);
                this.sendDownstreamGamePacket(abilitiesPacket);
            }
            abilities.add(Ability.FLYING);
        }
        if (spectator) {
            updateAbilitiesPacket.getAbilityLayers().add(this.buildSpectatorAbilityLayer());
        }
        abilityLayer.setLayerType(AbilityLayer.Type.BASE);
        abilityLayer.setFlySpeed(this.flySpeed);
        abilityLayer.setWalkSpeed(this.walkSpeed == 0.0f ? 0.01f : this.walkSpeed);
        abilityLayer.setVerticalFlySpeed(1.0f);
        Collections.addAll(abilityLayer.getAbilitiesSet(), USED_ABILITIES);
        updateAbilitiesPacket.getAbilityLayers().add(abilityLayer);
        this.sendUpstreamPacket(updateAbilitiesPacket);
    }

    private AbilityLayer buildSpectatorAbilityLayer() {
        AbilityLayer spectatorLayer = new AbilityLayer();
        spectatorLayer.setLayerType(AbilityLayer.Type.SPECTATOR);
        Set<Ability> abilitySet = spectatorLayer.getAbilitiesSet();
        abilitySet.add(Ability.BUILD);
        abilitySet.add(Ability.MINE);
        abilitySet.add(Ability.DOORS_AND_SWITCHES);
        abilitySet.add(Ability.OPEN_CONTAINERS);
        abilitySet.add(Ability.ATTACK_PLAYERS);
        abilitySet.add(Ability.ATTACK_MOBS);
        abilitySet.add(Ability.INVULNERABLE);
        abilitySet.add(Ability.FLYING);
        abilitySet.add(Ability.MAY_FLY);
        abilitySet.add(Ability.INSTABUILD);
        abilitySet.add(Ability.NO_CLIP);
        Set<Ability> abilityValues = spectatorLayer.getAbilityValues();
        abilityValues.add(Ability.INVULNERABLE);
        abilityValues.add(Ability.FLYING);
        abilityValues.add(Ability.NO_CLIP);
        return spectatorLayer;
    }

    private int getRenderDistance() {
        if (this.clientRenderDistance != -1) {
            return this.clientRenderDistance;
        }
        if (this.serverRenderDistance != -1) {
            return this.serverRenderDistance;
        }
        return 2;
    }

    public void sendJavaClientSettings() {
        ServerboundClientInformationPacket clientSettingsPacket = new ServerboundClientInformationPacket(this.locale().toLowerCase(Locale.ROOT), this.getRenderDistance(), ChatVisibility.FULL, true, SKIN_PARTS, HandPreference.RIGHT_HAND, false, true, ParticleStatus.ALL);
        this.sendDownstreamPacket(clientSettingsPacket);
    }

    public void updateStatistics(@NonNull Object2IntMap<Statistic> statistics) {
        if (this.statistics.isEmpty()) {
            for (CustomStatistic customStatistic : CustomStatistic.values()) {
                this.statistics.put((Statistic)customStatistic, 0);
            }
        }
        this.statistics.putAll(statistics);
    }

    public boolean canUseCommandBlocks() {
        return this.instabuild && this.opPermissionLevel >= 2;
    }

    public void playSoundEvent(SoundEvent sound, Vector3f position) {
        LevelSoundEventPacket packet = new LevelSoundEventPacket();
        packet.setPosition(position);
        packet.setSound(sound);
        packet.setIdentifier(":");
        packet.setExtraData(-1);
        this.sendUpstreamPacket(packet);
    }

    public float getEyeHeight() {
        return switch (this.pose) {
            case Pose.SNEAKING -> 1.27f;
            case Pose.SWIMMING, Pose.FALL_FLYING, Pose.SPIN_ATTACK -> 0.4f;
            case Pose.SLEEPING -> 0.2f;
            default -> VanillaEntities.PLAYER_ENTITY_OFFSET;
        };
    }

    public void updateRain(float strength) {
        boolean wasRaining = this.isRaining();
        this.rainStrength = strength;
        LevelEventPacket rainPacket = new LevelEventPacket();
        rainPacket.setType(this.isRaining() ? LevelEvent.START_RAINING : LevelEvent.STOP_RAINING);
        rainPacket.setData((int)(strength * 65535.0f));
        rainPacket.setPosition(Vector3f.ZERO);
        this.sendUpstreamPacket(rainPacket);
        if (wasRaining != this.isRaining() && this.isThunder()) {
            if (this.isRaining()) {
                LevelEventPacket thunderPacket = new LevelEventPacket();
                thunderPacket.setType(LevelEvent.START_THUNDERSTORM);
                thunderPacket.setData((int)(this.thunderStrength * 65535.0f));
                thunderPacket.setPosition(Vector3f.ZERO);
                this.sendUpstreamPacket(thunderPacket);
            } else {
                LevelEventPacket thunderPacket = new LevelEventPacket();
                thunderPacket.setType(LevelEvent.STOP_THUNDERSTORM);
                thunderPacket.setData(0);
                thunderPacket.setPosition(Vector3f.ZERO);
                this.sendUpstreamPacket(thunderPacket);
            }
        }
    }

    public void updateThunder(float strength) {
        this.thunderStrength = strength;
        if (!this.isRaining()) {
            return;
        }
        LevelEventPacket thunderPacket = new LevelEventPacket();
        thunderPacket.setType(this.isThunder() ? LevelEvent.START_THUNDERSTORM : LevelEvent.STOP_THUNDERSTORM);
        thunderPacket.setData((int)(strength * 65535.0f));
        thunderPacket.setPosition(Vector3f.ZERO);
        this.sendUpstreamPacket(thunderPacket);
    }

    public boolean isRaining() {
        return this.rainStrength > 0.0f;
    }

    public boolean isThunder() {
        return this.thunderStrength > 0.0f;
    }

    @Override
    public @NonNull String bedrockUsername() {
        return this.authData != null ? this.authData.name() : "unknown (pre-login)";
    }

    @Override
    public @MonotonicNonNull String javaUsername() {
        return this.playerEntity != null ? this.playerEntity.getUsername() : null;
    }

    @Override
    public UUID javaUuid() {
        return this.playerEntity != null ? this.playerEntity.uuid() : null;
    }

    @Override
    public @NonNull String xuid() {
        return this.authData.xuid();
    }

    @Override
    public @NonNull String playFabId() {
        return this.authData.playFabId() == null ? "" : this.authData.playFabId();
    }

    @Override
    public @NonNull String version() {
        if (this.clientData == null) {
            return "unknown";
        }
        return this.clientData.getGameVersion();
    }

    @Override
    public @NonNull BedrockPlatform platform() {
        if (this.clientData == null) {
            return BedrockPlatform.UNKNOWN;
        }
        return BedrockPlatform.values()[this.clientData.getDeviceOs().ordinal()];
    }

    @Override
    public @NonNull String languageCode() {
        return this.locale();
    }

    @Override
    public @NonNull UiProfile uiProfile() {
        return UiProfile.values()[this.clientData.getUiProfile().ordinal()];
    }

    @Override
    public @NonNull InputMode inputMode() {
        return InputMode.values()[this.inputCache.getInputMode().ordinal()];
    }

    @Override
    public boolean isLinked() {
        return false;
    }

    @Override
    public boolean transfer(@NonNull String address, @IntRange(from=0L, to=65535L) int port) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Server address cannot be null or blank");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Server port must be between 0 and 65535, was " + port);
        }
        TransferPacket transferPacket = new TransferPacket();
        transferPacket.setAddress(address);
        transferPacket.setPort(port);
        this.sendUpstreamPacket(transferPacket);
        return true;
    }

    @Override
    public void showEmote(@NonNull GeyserPlayerEntity emoter, @NonNull String emoteId) {
        this.entities().showEmote(emoter, emoteId);
    }

    @Override
    public @NonNull GeyserPlayerEntity playerEntity() {
        return this.playerEntity;
    }

    public void setLockInput(InputLocksFlag flag, boolean value) {
        this.inputLockDirty |= value ? this.inputLocksSet.add(flag) : this.inputLocksSet.remove((Object)flag);
    }

    public void updateInputLocks() {
        if (!this.inputLockDirty) {
            return;
        }
        this.inputLockDirty = false;
        UpdateClientInputLocksPacket packet = new UpdateClientInputLocksPacket();
        int result = 0;
        for (InputLocksFlag other : this.inputLocksSet) {
            result |= other.getOffset();
        }
        packet.setLockComponentData(result);
        packet.setServerPosition(this.playerEntity.bedrockPosition());
        this.sendUpstreamPacket(packet);
    }

    public boolean getLockedInput(InputLocksFlag flag) {
        return this.inputLocksSet.contains((Object)flag);
    }

    @Override
    public @NonNull CameraData camera() {
        return this.cameraData;
    }

    @Override
    public @NonNull EntityData entities() {
        return this.entityData;
    }

    @Override
    public void shakeCamera(float intensity, float duration, @NonNull CameraShake type) {
        this.cameraData.shakeCamera(intensity, duration, type);
    }

    @Override
    public void stopCameraShake() {
        this.cameraData.stopCameraShake();
    }

    @Override
    public void sendFog(String ... fogNameSpaces) {
        this.cameraData.sendFog(fogNameSpaces);
    }

    @Override
    public void removeFog(String ... fogNameSpaces) {
        this.cameraData.removeFog(fogNameSpaces);
    }

    @Override
    public @NonNull Set<String> fogEffects() {
        return this.cameraData.fogEffects();
    }

    @Override
    public int ping() {
        if (!this.getUpstream().isInitialized() || this.getUpstream().isClosed()) {
            return 0;
        }
        RakSessionCodec rakSessionCodec = (RakSessionCodec)((RakChildChannel)this.getUpstream().getSession().getPeer().getChannel()).rakPipeline().get(RakSessionCodec.class);
        return (int)Math.floor(rakSessionCodec.getPing());
    }

    @Override
    public int protocolVersion() {
        return this.upstream.getProtocolVersion();
    }

    @Override
    public boolean hasFormOpen() {
        return this.formCache.hasFormOpen();
    }

    @Override
    public void closeForm() {
        this.formCache.closeForms();
    }

    public void addCommandEnum(String name, String enums) {
        this.softEnumPacket(name, SoftEnumUpdateType.ADD, enums);
    }

    public void removeCommandEnum(String name, String enums) {
        this.softEnumPacket(name, SoftEnumUpdateType.REMOVE, enums);
    }

    private void softEnumPacket(String name, SoftEnumUpdateType type, String enums) {
        if (!this.geyser.config().gameplay().commandSuggestions()) {
            return;
        }
        UpdateSoftEnumPacket packet = new UpdateSoftEnumPacket();
        packet.setType(type);
        packet.setSoftEnum(new CommandEnumData(name, Collections.singletonMap(enums, Collections.emptySet()), true));
        this.sendUpstreamPacket(packet);
    }

    public void sendNetworkLatencyStackPacket(long timestamp, boolean ensureEventLoop, Runnable runnable) {
        NetworkStackLatencyPacket latencyPacket = new NetworkStackLatencyPacket();
        latencyPacket.setFromServer(true);
        latencyPacket.setTimestamp(timestamp);
        if (ensureEventLoop) {
            this.latencyPingCache.add(() -> this.ensureInEventLoop(runnable));
        } else {
            this.latencyPingCache.add(runnable);
        }
        this.sendUpstreamPacket(latencyPacket);
    }

    public String getDebugInfo() {
        return "Username: %s, DeviceOs: %s, Version: %s".formatted(new Object[]{this.bedrockUsername(), this.platform(), this.version()});
    }

    @Generated
    public GeyserImpl getGeyser() {
        return this.geyser;
    }

    @Generated
    public UpstreamSession getUpstream() {
        return this.upstream;
    }

    @Generated
    public DownstreamSession getDownstream() {
        return this.downstream;
    }

    @Generated
    public EventLoop getTickEventLoop() {
        return this.tickEventLoop;
    }

    @Generated
    public AuthData getAuthData() {
        return this.authData;
    }

    @Generated
    public BedrockClientData getClientData() {
        return this.clientData;
    }

    @Generated
    public List<String> getCertChainData() {
        return this.certChainData;
    }

    @Generated
    public String getToken() {
        return this.token;
    }

    @Generated
    public @NonNull AbstractGeyserboundPacketHandler getErosionHandler() {
        return this.erosionHandler;
    }

    @Generated
    public RemoteServer remoteServer() {
        return this.remoteServer;
    }

    @Generated
    public SessionPlayerEntity getPlayerEntity() {
        return this.playerEntity;
    }

    @Generated
    public AdvancementsCache getAdvancementsCache() {
        return this.advancementsCache;
    }

    @Generated
    public BookEditCache getBookEditCache() {
        return this.bookEditCache;
    }

    @Generated
    public BundleCache getBundleCache() {
        return this.bundleCache;
    }

    @Generated
    public ChunkCache getChunkCache() {
        return this.chunkCache;
    }

    @Generated
    public ComponentCache getComponentCache() {
        return this.componentCache;
    }

    @Generated
    public EntityCache getEntityCache() {
        return this.entityCache;
    }

    @Generated
    public EntityEffectCache getEffectCache() {
        return this.effectCache;
    }

    @Generated
    public FormCache getFormCache() {
        return this.formCache;
    }

    @Generated
    public GameRuleHandler getGameRuleHandler() {
        return this.gameRuleHandler;
    }

    @Generated
    public InputCache getInputCache() {
        return this.inputCache;
    }

    @Generated
    public LodestoneCache getLodestoneCache() {
        return this.lodestoneCache;
    }

    @Generated
    public PistonCache getPistonCache() {
        return this.pistonCache;
    }

    @Generated
    public PreferencesCache getPreferencesCache() {
        return this.preferencesCache;
    }

    @Generated
    public RegistryCache getRegistryCache() {
        return this.registryCache;
    }

    @Generated
    public SkullCache getSkullCache() {
        return this.skullCache;
    }

    @Generated
    public StructureBlockCache getStructureBlockCache() {
        return this.structureBlockCache;
    }

    @Generated
    public TagCache getTagCache() {
        return this.tagCache;
    }

    @Generated
    public WaypointCache getWaypointCache() {
        return this.waypointCache;
    }

    @Generated
    public WorldCache getWorldCache() {
        return this.worldCache;
    }

    @Generated
    public BlockBreakHandler getBlockBreakHandler() {
        return this.blockBreakHandler;
    }

    @Generated
    public TeleportCache getUnconfirmedTeleport() {
        return this.unconfirmedTeleport;
    }

    @Generated
    public @Nullable Entity getSpectatedEntity() {
        return this.spectatedEntity;
    }

    @Generated
    public EntitySpectateHelper.SpectateMode getSpectateMode() {
        return this.spectateMode;
    }

    @Generated
    public WorldBorder getWorldBorder() {
        return this.worldBorder;
    }

    @Generated
    public boolean isInWorldBorderWarningArea() {
        return this.isInWorldBorderWarningArea;
    }

    @Generated
    public InventoryHolder<PlayerInventory> getPlayerInventoryHolder() {
        return this.playerInventoryHolder;
    }

    @Generated
    public @Nullable InventoryHolder<? extends Inventory> getInventoryHolder() {
        return this.inventoryHolder;
    }

    @Generated
    public DialogManager getDialogManager() {
        return this.dialogManager;
    }

    @Generated
    public List<ServerLink> getServerLinks() {
        return this.serverLinks;
    }

    @Generated
    public List<String> getKnownCommands() {
        return this.knownCommands;
    }

    @Generated
    public List<String> getRestrictedCommands() {
        return this.restrictedCommands;
    }

    @Generated
    public boolean isClosingInventory() {
        return this.closingInventory;
    }

    @Generated
    public int getPendingOrCurrentBedrockInventoryId() {
        return this.pendingOrCurrentBedrockInventoryId;
    }

    @Generated
    public boolean isNeedAttackCooldownClear() {
        return this.needAttackCooldownClear;
    }

    @Generated
    public ScheduledFuture<?> getContainerOutputFuture() {
        return this.containerOutputFuture;
    }

    @Generated
    public CollisionManager getCollisionManager() {
        return this.collisionManager;
    }

    @Generated
    public BlockMappings getBlockMappings() {
        return this.blockMappings;
    }

    @Generated
    public ItemMappings getItemMappings() {
        return this.itemMappings;
    }

    @Generated
    public Map<Vector3i, ItemFrameEntity> getItemFrameCache() {
        return this.itemFrameCache;
    }

    @Generated
    public Map<UUID, ResolvableProfile> getPlayerWithCustomHeads() {
        return this.playerWithCustomHeads;
    }

    @Generated
    public boolean isDroppingLecternBook() {
        return this.droppingLecternBook;
    }

    @Generated
    public Vector2i getLastChunkPosition() {
        return this.lastChunkPosition;
    }

    @Generated
    public int getClientRenderDistance() {
        return this.clientRenderDistance;
    }

    @Generated
    public int getServerRenderDistance() {
        return this.serverRenderDistance;
    }

    @Generated
    public boolean isSentSpawnPacket() {
        return this.sentSpawnPacket;
    }

    @Generated
    public @Nullable ServerConfigurationJoinInfo getServerJoinInfo() {
        return this.serverJoinInfo;
    }

    @Generated
    public boolean isLoggedIn() {
        return this.loggedIn;
    }

    @Generated
    public boolean isLoggingIn() {
        return this.loggingIn;
    }

    @Generated
    public boolean isSpawned() {
        return this.spawned;
    }

    @Generated
    public boolean isClosed() {
        return this.closed;
    }

    @Generated
    public GameMode getGameMode() {
        return this.gameMode;
    }

    @Generated
    public Key getWorldName() {
        return this.worldName;
    }

    @Generated
    public String[] getLevels() {
        return this.levels;
    }

    @Generated
    public boolean isSneaking() {
        return this.sneaking;
    }

    @Generated
    public boolean isShouldSendSneak() {
        return this.shouldSendSneak;
    }

    @Generated
    public Pose getPose() {
        return this.pose;
    }

    @Generated
    public boolean isSprinting() {
        return this.sprinting;
    }

    @Generated
    public BedrockDimension getBedrockOverworldDimension() {
        return this.bedrockOverworldDimension;
    }

    @Generated
    public @MonotonicNonNull JavaDimension getDimensionType() {
        return this.dimensionType;
    }

    @Generated
    public BedrockDimension getBedrockDimension() {
        return this.bedrockDimension;
    }

    @Generated
    public Vector3i getLastBlockPlacePosition() {
        return this.lastBlockPlacePosition;
    }

    @Generated
    public BlockItem getLastBlockPlaced() {
        return this.lastBlockPlaced;
    }

    @Generated
    public boolean isInteracting() {
        return this.interacting;
    }

    @Generated
    public Vector3i getLastInteractionBlockPosition() {
        return this.lastInteractionBlockPosition;
    }

    @Generated
    public int getLastInteractionBlockFace() {
        return this.lastInteractionBlockFace;
    }

    @Generated
    public Vector3f getLastInteractionPlayerPosition() {
        return this.lastInteractionPlayerPosition;
    }

    @Generated
    public Entity getMouseoverEntity() {
        return this.mouseoverEntity;
    }

    @Generated
    public Int2ObjectMap<List<String>> getJavaToBedrockRecipeIds() {
        return this.javaToBedrockRecipeIds;
    }

    @Generated
    public Int2ObjectMap<GeyserRecipe> getCraftingRecipes() {
        return this.craftingRecipes;
    }

    @Generated
    public Pair<CraftingRecipeData, GeyserRecipe> getLastCreatedRecipe() {
        return this.lastCreatedRecipe;
    }

    @Generated
    public AtomicInteger getLastRecipeNetId() {
        return this.lastRecipeNetId;
    }

    @Generated
    public boolean isCleanRecipesRequired() {
        return this.cleanRecipesRequired;
    }

    @Generated
    public Int2ObjectMap<GeyserStonecutterData> getStonecutterRecipes() {
        return this.stonecutterRecipes;
    }

    @Generated
    public List<GeyserSmithingRecipe> getSmithingRecipes() {
        return this.smithingRecipes;
    }

    @Generated
    public TrimRecipes getTrimRecipes() {
        return this.trimRecipes;
    }

    @Generated
    public boolean isEmulatePost1_13Logic() {
        return this.emulatePost1_13Logic;
    }

    @Generated
    public boolean isEmulatePost1_16Logic() {
        return this.emulatePost1_16Logic;
    }

    @Generated
    public boolean isEmulatePost1_18Logic() {
        return this.emulatePost1_18Logic;
    }

    @Generated
    public boolean isOldSmithingTable() {
        return this.oldSmithingTable;
    }

    @Generated
    public boolean isUsingExperimentalMinecartLogic() {
        return this.isUsingExperimentalMinecartLogic;
    }

    @Generated
    public boolean isHasFishingRodCast() {
        return this.hasFishingRodCast;
    }

    @Generated
    public double getAttackSpeed() {
        return this.attackSpeed;
    }

    @Generated
    public long getLastHitTime() {
        return this.lastHitTime;
    }

    @Generated
    public boolean isSteeringLeft() {
        return this.steeringLeft;
    }

    @Generated
    public boolean isSteeringRight() {
        return this.steeringRight;
    }

    @Generated
    public long getLastChargedProjectilesTime() {
        return this.lastChargedProjectilesTime;
    }

    @Generated
    public long getLastInteractionTime() {
        return this.lastInteractionTime;
    }

    @Generated
    public boolean isPlacedBucket() {
        return this.placedBucket;
    }

    @Generated
    public @Nullable Vector3i getLastLowerDoorPosition() {
        return this.lastLowerDoorPosition;
    }

    @Generated
    public int getArmAnimationTicks() {
        return this.armAnimationTicks;
    }

    @Generated
    public int getLastAirHitTick() {
        return this.lastAirHitTick;
    }

    @Generated
    public List<Long> getAttachedFireworkRockets() {
        return this.attachedFireworkRockets;
    }

    @Generated
    public boolean isShouldClientTickClock() {
        return this.shouldClientTickClock;
    }

    @Generated
    public boolean isReducedDebugInfo() {
        return this.reducedDebugInfo;
    }

    @Generated
    public int getOpPermissionLevel() {
        return this.opPermissionLevel;
    }

    @Generated
    public boolean isCanFly() {
        return this.canFly;
    }

    @Generated
    public boolean isFlying() {
        return this.flying;
    }

    @Generated
    public boolean isNoClip() {
        return this.noClip;
    }

    @Generated
    public boolean isInstabuild() {
        return this.instabuild;
    }

    @Generated
    public float getFlySpeed() {
        return this.flySpeed;
    }

    @Generated
    public float getWalkSpeed() {
        return this.walkSpeed;
    }

    @Generated
    public float getRainStrength() {
        return this.rainStrength;
    }

    @Generated
    public float getThunderStrength() {
        return this.thunderStrength;
    }

    @Generated
    public Object2IntMap<Statistic> getStatistics() {
        return this.statistics;
    }

    @Generated
    public boolean isWaitingForStatistics() {
        return this.waitingForStatistics;
    }

    @Generated
    public boolean isAdvancedTooltips() {
        return this.advancedTooltips;
    }

    @Generated
    public ScheduledFuture<?> getTickThread() {
        return this.tickThread;
    }

    @Generated
    public int getTicks() {
        return this.ticks;
    }

    @Generated
    public long getClientTicks() {
        return this.clientTicks;
    }

    @Generated
    public long getGameTicks() {
        return this.gameTicks;
    }

    @Generated
    public long getDayTimeTicks() {
        return this.dayTimeTicks;
    }

    @Generated
    public double getPartialTimeTick() {
        return this.partialTimeTick;
    }

    @Generated
    public float getClockRate() {
        return this.clockRate;
    }

    @Generated
    public ScheduledFuture<?> getMountVehicleScheduledFuture() {
        return this.mountVehicleScheduledFuture;
    }

    @Generated
    public Queue<Runnable> getLatencyPingCache() {
        return this.latencyPingCache;
    }

    @Generated
    public List<BedrockPacket> getQueuedImmediatelyPackets() {
        return this.queuedImmediatelyPackets;
    }

    @Generated
    public @Nullable ItemData getCurrentBook() {
        return this.currentBook;
    }

    @Generated
    public Map<String, byte[]> getCookies() {
        return this.cookies;
    }

    @Generated
    public GeyserCameraData getCameraData() {
        return this.cameraData;
    }

    @Generated
    public GeyserEntityData getEntityData() {
        return this.entityData;
    }

    @Generated
    public int getNanosecondsPerTick() {
        return this.nanosecondsPerTick;
    }

    @Generated
    public float getMillisecondsPerTick() {
        return this.millisecondsPerTick;
    }

    @Generated
    public boolean isTickingFrozen() {
        return this.tickingFrozen;
    }

    @Generated
    public int getStepTicks() {
        return this.stepTicks;
    }

    @Generated
    public boolean isAllowVibrantVisuals() {
        return this.allowVibrantVisuals;
    }

    @Generated
    public boolean hasAcceptedCodeOfConduct() {
        return this.hasAcceptedCodeOfConduct;
    }

    @Generated
    public boolean integratedPackActive() {
        return this.integratedPackActive;
    }

    @Generated
    public Set<InputLocksFlag> getInputLocksSet() {
        return this.inputLocksSet;
    }

    @Generated
    public boolean isInputLockDirty() {
        return this.inputLockDirty;
    }

    @Generated
    public boolean isPendingSpectator() {
        return this.pendingSpectator;
    }

    @Generated
    public void setAuthData(AuthData authData) {
        this.authData = authData;
    }

    @Generated
    public void setCertChainData(List<String> certChainData) {
        this.certChainData = certChainData;
    }

    @Generated
    public void setToken(String token) {
        this.token = token;
    }

    @Generated
    public void setErosionHandler(@NonNull AbstractGeyserboundPacketHandler erosionHandler) {
        if (erosionHandler == null) {
            throw new NullPointerException("erosionHandler is marked non-null but is null");
        }
        this.erosionHandler = erosionHandler;
    }

    @Generated
    public GeyserSession remoteServer(RemoteServer remoteServer) {
        this.remoteServer = remoteServer;
        return this;
    }

    @Generated
    public void setBlockBreakHandler(BlockBreakHandler blockBreakHandler) {
        this.blockBreakHandler = blockBreakHandler;
    }

    @Generated
    public void setUnconfirmedTeleport(TeleportCache unconfirmedTeleport) {
        this.unconfirmedTeleport = unconfirmedTeleport;
    }

    @Generated
    public void setSpectatedEntity(@Nullable Entity spectatedEntity) {
        this.spectatedEntity = spectatedEntity;
    }

    @Generated
    public void setSpectateMode(EntitySpectateHelper.SpectateMode spectateMode) {
        this.spectateMode = spectateMode;
    }

    @Generated
    public void setInventoryHolder(@Nullable InventoryHolder<? extends Inventory> inventoryHolder) {
        this.inventoryHolder = inventoryHolder;
    }

    @Generated
    public void setServerLinks(List<ServerLink> serverLinks) {
        this.serverLinks = serverLinks;
    }

    @Generated
    public void setKnownCommands(List<String> knownCommands) {
        this.knownCommands = knownCommands;
    }

    @Generated
    public void setRestrictedCommands(List<String> restrictedCommands) {
        this.restrictedCommands = restrictedCommands;
    }

    @Generated
    public void setClosingInventory(boolean closingInventory) {
        this.closingInventory = closingInventory;
    }

    @Generated
    public void setPendingOrCurrentBedrockInventoryId(int pendingOrCurrentBedrockInventoryId) {
        this.pendingOrCurrentBedrockInventoryId = pendingOrCurrentBedrockInventoryId;
    }

    @Generated
    public void setNeedAttackCooldownClear(boolean needAttackCooldownClear) {
        this.needAttackCooldownClear = needAttackCooldownClear;
    }

    @Generated
    public void setContainerOutputFuture(ScheduledFuture<?> containerOutputFuture) {
        this.containerOutputFuture = containerOutputFuture;
    }

    @Generated
    public void setBlockMappings(BlockMappings blockMappings) {
        this.blockMappings = blockMappings;
    }

    @Generated
    public void setItemMappings(ItemMappings itemMappings) {
        this.itemMappings = itemMappings;
    }

    @Generated
    public void setDroppingLecternBook(boolean droppingLecternBook) {
        this.droppingLecternBook = droppingLecternBook;
    }

    @Generated
    public void setLastChunkPosition(Vector2i lastChunkPosition) {
        this.lastChunkPosition = lastChunkPosition;
    }

    @Generated
    public void setServerJoinInfo(@Nullable ServerConfigurationJoinInfo serverJoinInfo) {
        this.serverJoinInfo = serverJoinInfo;
    }

    @Generated
    public void setSpawned(boolean spawned) {
        this.spawned = spawned;
    }

    @Generated
    public void setWorldName(Key worldName) {
        this.worldName = worldName;
    }

    @Generated
    public void setLevels(String[] levels) {
        this.levels = levels;
    }

    @Generated
    public void setShouldSendSneak(boolean shouldSendSneak) {
        this.shouldSendSneak = shouldSendSneak;
    }

    @Generated
    public void setPose(Pose pose) {
        this.pose = pose;
    }

    @Generated
    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
    }

    @Generated
    public void setDimensionType(@MonotonicNonNull JavaDimension dimensionType) {
        this.dimensionType = dimensionType;
    }

    @Generated
    public void setBedrockDimension(BedrockDimension bedrockDimension) {
        this.bedrockDimension = bedrockDimension;
    }

    @Generated
    public void setLastBlockPlacePosition(Vector3i lastBlockPlacePosition) {
        this.lastBlockPlacePosition = lastBlockPlacePosition;
    }

    @Generated
    public void setLastBlockPlaced(BlockItem lastBlockPlaced) {
        this.lastBlockPlaced = lastBlockPlaced;
    }

    @Generated
    public void setInteracting(boolean interacting) {
        this.interacting = interacting;
    }

    @Generated
    public void setLastInteractionBlockPosition(Vector3i lastInteractionBlockPosition) {
        this.lastInteractionBlockPosition = lastInteractionBlockPosition;
    }

    @Generated
    public void setLastInteractionBlockFace(int lastInteractionBlockFace) {
        this.lastInteractionBlockFace = lastInteractionBlockFace;
    }

    @Generated
    public void setLastInteractionPlayerPosition(Vector3f lastInteractionPlayerPosition) {
        this.lastInteractionPlayerPosition = lastInteractionPlayerPosition;
    }

    @Generated
    public void setMouseoverEntity(Entity mouseoverEntity) {
        this.mouseoverEntity = mouseoverEntity;
    }

    @Generated
    public void setLastCreatedRecipe(Pair<CraftingRecipeData, GeyserRecipe> lastCreatedRecipe) {
        this.lastCreatedRecipe = lastCreatedRecipe;
    }

    @Generated
    public void setCleanRecipesRequired(boolean cleanRecipesRequired) {
        this.cleanRecipesRequired = cleanRecipesRequired;
    }

    @Generated
    public void setStonecutterRecipes(Int2ObjectMap<GeyserStonecutterData> stonecutterRecipes) {
        this.stonecutterRecipes = stonecutterRecipes;
    }

    @Generated
    public void setEmulatePost1_13Logic(boolean emulatePost1_13Logic) {
        this.emulatePost1_13Logic = emulatePost1_13Logic;
    }

    @Generated
    public void setEmulatePost1_16Logic(boolean emulatePost1_16Logic) {
        this.emulatePost1_16Logic = emulatePost1_16Logic;
    }

    @Generated
    public void setEmulatePost1_18Logic(boolean emulatePost1_18Logic) {
        this.emulatePost1_18Logic = emulatePost1_18Logic;
    }

    @Generated
    public void setOldSmithingTable(boolean oldSmithingTable) {
        this.oldSmithingTable = oldSmithingTable;
    }

    @Generated
    public void setUsingExperimentalMinecartLogic(boolean isUsingExperimentalMinecartLogic) {
        this.isUsingExperimentalMinecartLogic = isUsingExperimentalMinecartLogic;
    }

    @Generated
    public void setAttackSpeed(double attackSpeed) {
        this.attackSpeed = attackSpeed;
    }

    @Generated
    public void setLastHitTime(long lastHitTime) {
        this.lastHitTime = lastHitTime;
    }

    @Generated
    public void setSteeringLeft(boolean steeringLeft) {
        this.steeringLeft = steeringLeft;
    }

    @Generated
    public void setSteeringRight(boolean steeringRight) {
        this.steeringRight = steeringRight;
    }

    @Generated
    public boolean isInClientPredictedVehicle() {
        return this.inClientPredictedVehicle;
    }

    @Generated
    public void setInClientPredictedVehicle(boolean inClientPredictedVehicle) {
        this.inClientPredictedVehicle = inClientPredictedVehicle;
    }

    @Generated
    public void setLastChargedProjectilesTime(long lastChargedProjectilesTime) {
        this.lastChargedProjectilesTime = lastChargedProjectilesTime;
    }

    @Generated
    public void setLastInteractionTime(long lastInteractionTime) {
        this.lastInteractionTime = lastInteractionTime;
    }

    @Generated
    public void setPlacedBucket(boolean placedBucket) {
        this.placedBucket = placedBucket;
    }

    @Generated
    public void setLastLowerDoorPosition(@Nullable Vector3i lastLowerDoorPosition) {
        this.lastLowerDoorPosition = lastLowerDoorPosition;
    }

    @Generated
    public void setLastAirHitTick(int lastAirHitTick) {
        this.lastAirHitTick = lastAirHitTick;
    }

    @Generated
    public void setCanFly(boolean canFly) {
        this.canFly = canFly;
    }

    @Generated
    public void setFlying(boolean flying) {
        this.flying = flying;
    }

    @Generated
    public void setInstabuild(boolean instabuild) {
        this.instabuild = instabuild;
    }

    @Generated
    public void setFlySpeed(float flySpeed) {
        this.flySpeed = flySpeed;
    }

    @Generated
    public void setWalkSpeed(float walkSpeed) {
        this.walkSpeed = walkSpeed;
    }

    @Generated
    public void setWaitingForStatistics(boolean waitingForStatistics) {
        this.waitingForStatistics = waitingForStatistics;
    }

    @Generated
    public void setAdvancedTooltips(boolean advancedTooltips) {
        this.advancedTooltips = advancedTooltips;
    }

    @Generated
    public void setClientTicks(long clientTicks) {
        this.clientTicks = clientTicks;
    }

    @Generated
    public void setGameTicks(long gameTicks) {
        this.gameTicks = gameTicks;
    }

    @Generated
    public void setMountVehicleScheduledFuture(ScheduledFuture<?> mountVehicleScheduledFuture) {
        this.mountVehicleScheduledFuture = mountVehicleScheduledFuture;
    }

    @Generated
    public void setCurrentBook(@Nullable ItemData currentBook) {
        this.currentBook = currentBook;
    }

    @Generated
    public void setCookies(Map<String, byte[]> cookies) {
        this.cookies = cookies;
    }

    @Generated
    MinecraftProtocol getProtocol() {
        return this.protocol;
    }

    @Generated
    public void setStepTicks(int stepTicks) {
        this.stepTicks = stepTicks;
    }

    @Generated
    public void setAllowVibrantVisuals(boolean allowVibrantVisuals) {
        this.allowVibrantVisuals = allowVibrantVisuals;
    }

    @Generated
    public GeyserSession hasAcceptedCodeOfConduct(boolean hasAcceptedCodeOfConduct) {
        this.hasAcceptedCodeOfConduct = hasAcceptedCodeOfConduct;
        return this;
    }

    @Generated
    public GeyserSession integratedPackActive(boolean integratedPackActive) {
        this.integratedPackActive = integratedPackActive;
        return this;
    }
}
