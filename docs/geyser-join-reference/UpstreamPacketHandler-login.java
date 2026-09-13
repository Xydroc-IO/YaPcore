    public PacketSignal handle(LoginPacket loginPacket) {
        if (this.geyser.isShuttingDown() || this.geyser.isReloading()) {
            this.session.disconnect(GeyserLocale.getLocaleStringLog("geyser.core.shutdown.kick.message"));
            return PacketSignal.HANDLED;
        }
        if (!this.networkSettingsRequested) {
            this.session.disconnect(GeyserLocale.getLocaleStringLog("geyser.network.outdated.client", GameProtocol.getAllSupportedBedrockVersions()));
            return PacketSignal.HANDLED;
        }
        if (this.receivedLoginPacket) {
            this.session.disconnect("Received duplicate login packet!");
            this.session.forciblyCloseUpstream();
            return PacketSignal.HANDLED;
        }
        this.receivedLoginPacket = true;
        LoginEncryptionUtils.encryptPlayerConnection(this.session, loginPacket);
        if (this.session.isClosed()) {
            this.session.forciblyCloseUpstream();
            return PacketSignal.HANDLED;
        }
        if (this.geyser.getSessionManager().isXuidAlreadyPending(this.session.xuid()) || this.geyser.getSessionManager().sessionByXuid(this.session.xuid()) != null) {
            this.session.disconnect(GeyserLocale.getLocaleStringLog("geyser.auth.already_loggedin", this.session.bedrockUsername()));
            return PacketSignal.HANDLED;
        }
        this.session.setBlockMappings(BlockRegistries.BLOCKS.forVersion(loginPacket.getProtocolVersion()));
        this.session.setItemMappings(Registries.ITEMS.forVersion(loginPacket.getProtocolVersion()));
        this.geyser.getSessionManager().addPendingSession(this.session);
        this.geyser.eventBus().fire(new SessionInitializeEvent(this.session));
        PlayStatusPacket playStatus = new PlayStatusPacket();
        playStatus.setStatus(PlayStatusPacket.Status.LOGIN_SUCCESS);
        this.session.sendUpstreamPacket(playStatus);
        this.resourcePackLoadEvent = new SessionLoadResourcePacksEventImpl(this.session);
        this.geyser.eventBus().fireEventElseKick(this.resourcePackLoadEvent, this.session);
        if (this.session.isClosed()) {
            return PacketSignal.HANDLED;
        }
        this.session.integratedPackActive(this.resourcePackLoadEvent.isIntegratedPackActive());
        ResourcePacksInfoPacket resourcePacksInfo = new ResourcePacksInfoPacket();
        resourcePacksInfo.getResourcePackInfos().addAll(this.resourcePackLoadEvent.infoPacketEntries());
        resourcePacksInfo.setVibrantVisualsForceDisabled(!this.session.isAllowVibrantVisuals());
        resourcePacksInfo.setForcedToAccept(GeyserImpl.getInstance().config().gameplay().forceResourcePacks() || this.resourcePackLoadEvent.isIntegratedPackActive());
        resourcePacksInfo.setWorldTemplateId(new UUID(0L, 0L));
        resourcePacksInfo.setWorldTemplateVersion("");
        this.session.sendUpstreamPacket(resourcePacksInfo);
        GeyserLocale.loadGeyserLocale(this.session.locale());
        return PacketSignal.HANDLED;
    }
