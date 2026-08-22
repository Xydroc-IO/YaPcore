package com.yapcore.crossplay.bedrock.bridge;

import com.yapcore.crossplay.bedrock.*;
import com.yapcore.crossplay.floodgate.FloodgateAuth;
import com.yapcore.crossplay.form.FormService;
import com.yapcore.crossplay.skin.SkinService;
import io.netty.buffer.ByteBuf;

import com.yapcore.resourcepack.ResourcePackOffer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** Shared mutable state for Bedrock gameplay bridge helpers. */
public final class BedrockBridgeContext {

    public static final Logger LOG = Logger.getLogger("YaPcore.BedrockBridge");

    public final BedrockSessionManager sessions;
    public final FloodgateAuth floodgate;
    public final SkinService skins;
    public final FormService forms;
    public final BedrockEntityTracker entities = new BedrockEntityTracker();
    public final BedrockInventoryAuthority inventory = new BedrockInventoryAuthority();
    public final BedrockContainerBridge containers = new BedrockContainerBridge();
    public final AtomicLong runtimeIds = new AtomicLong(1);
    public final Map<Long, Integer> chunkRadius = new ConcurrentHashMap<>();
    public final Map<Long, Long> runtimeByGuid = new ConcurrentHashMap<>();
    public final Map<Long, Integer> pendingProtocol = new ConcurrentHashMap<>();
    public final BedrockColumnStreamer columns = new BedrockColumnStreamer();
    public final ConcurrentHashMap<String, Long> inventoryFingerprint = new ConcurrentHashMap<>();

    public BiConsumer<Long, List<ByteBuf>> outbound = (guid, packets) -> {
    };
    public volatile BedrockPaperWorldSync paperWorld;
    public LongConsumer compressionArmed;
    /** When set, Bedrock login mirrors the active JE resource pack offer (G.34). */
    public volatile Supplier<Optional<ResourcePackOffer>> resourcePackOffer = Optional::empty;

    public BedrockBridgeContext(BedrockSessionManager sessions,
                                FloodgateAuth floodgate,
                                SkinService skins,
                                FormService forms) {
        this.sessions = sessions;
        this.floodgate = floodgate;
        this.skins = skins;
        this.forms = forms;
    }

    public void send(long guid, List<ByteBuf> packets) {
        outbound.accept(guid, packets);
    }

    public void send(long guid, ByteBuf packet) {
        outbound.accept(guid, List.of(packet));
    }
}
