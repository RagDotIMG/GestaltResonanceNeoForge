package net.ragdot.gestaltresonance;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.WritableBookContent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ragdot.gestaltresonance.common.FloatPlayPassiveEvents;
import net.ragdot.gestaltresonance.common.GestaltDelayedPlacer;
import net.ragdot.gestaltresonance.common.GestaltParticles;
import net.ragdot.gestaltresonance.common.GhostPlayerHandler;
import net.ragdot.gestaltresonance.common.GestaltAcquisitionEvents;
import net.ragdot.gestaltresonance.common.GestaltEntities;
import net.ragdot.gestaltresonance.common.GestaltSoulProjectionEvents;
import net.ragdot.gestaltresonance.common.SoulProjectionExitType;
import net.ragdot.gestaltresonance.common.entity.BodyDoubleEntity;
import net.ragdot.gestaltresonance.common.entity.SpawnIllusionEntity;
import net.ragdot.gestaltresonance.common.entity.TearProjectileEntity;
import net.ragdot.gestaltresonance.common.entity.TimePhaseBodyDoubleEntity;
import net.ragdot.gestaltresonance.common.GestaltBlockEntities;
import net.ragdot.gestaltresonance.common.GestaltBlocks;
import net.ragdot.gestaltresonance.common.GestaltIllusionEvents;
import net.ragdot.gestaltresonance.common.power.amen_break.AmenBreakPower1G;
import net.ragdot.gestaltresonance.common.power.amen_break.AmenBreakPower1S;
import net.ragdot.gestaltresonance.common.power.amen_break.AmenBreakPower2B;
import net.ragdot.gestaltresonance.common.power.amen_break.AmenBreakPower2G;
import net.ragdot.gestaltresonance.common.power.amen_break.AmenBreakPower2S;
import net.ragdot.gestaltresonance.common.power.amen_break.AmenBreakPower1B;
import net.ragdot.gestaltresonance.common.power.amen_break.AmenBreakPower3B;
import net.ragdot.gestaltresonance.common.power.amen_break.AmenBreakPower3G;
import net.ragdot.gestaltresonance.common.power.amen_break.AmenBreakPower3S;
import net.ragdot.gestaltresonance.common.entity.PhaseBlossomEntity;
import net.ragdot.gestaltresonance.common.entity.DrowningDamageTracker;
import net.ragdot.gestaltresonance.common.SpillwaysLightManager;
import net.ragdot.gestaltresonance.common.power.spillways.SpillwaysPower1B;
import net.ragdot.gestaltresonance.common.power.spillways.SpillwaysPower1G;
import net.ragdot.gestaltresonance.common.power.spillways.SpillwaysPower2B;
import net.ragdot.gestaltresonance.common.power.spillways.SpillwaysPower2G;
import net.ragdot.gestaltresonance.common.GestaltAttackEvents;
import net.ragdot.gestaltresonance.common.GestaltResonanceEvents;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltGuardEvents;
import net.ragdot.gestaltresonance.common.GestaltLevelingEvents;
import net.ragdot.gestaltresonance.common.GestaltMiningEvents;
import net.ragdot.gestaltresonance.common.GestaltChargedStrikeEvents;
import net.ragdot.gestaltresonance.common.GestaltThrowEvents;
import net.ragdot.gestaltresonance.common.GestaltXpChannelEvents;
import net.ragdot.gestaltresonance.common.skin.GestaltSkinUnlockEvents;
import net.ragdot.gestaltresonance.common.GestaltDataComponents;
import net.ragdot.gestaltresonance.common.GestaltMobEffects;
import net.ragdot.gestaltresonance.common.GestaltSounds;
import net.ragdot.gestaltresonance.common.GestaltDamageBankingEvents;
import net.ragdot.gestaltresonance.common.GestaltCommand;
import net.ragdot.gestaltresonance.common.LedgeGrabLogic;
import net.ragdot.gestaltresonance.common.loot.GestaltLootModifiers;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;
import net.ragdot.gestaltresonance.common.passive.GestaltPassive;
import net.ragdot.gestaltresonance.common.passive.GestaltPassiveRegistry;
import net.ragdot.gestaltresonance.common.WallSlideLogic;
import net.ragdot.gestaltresonance.common.item.DustyDocumentContents;
import net.ragdot.gestaltresonance.common.item.DustyDocumentsItem;
import net.ragdot.gestaltresonance.common.item.DustyDocumentsWritableItem;
import net.ragdot.gestaltresonance.common.item.NetherTearItem;
import net.ragdot.gestaltresonance.common.item.ResonantPowderItem;
import net.ragdot.gestaltresonance.common.item.SoulVesselEmptyItem;
import net.ragdot.gestaltresonance.common.item.SoulVesselFragileItem;

@Mod(GestaltResonance.MODID)
public class GestaltResonance {
    public static final String MODID = "gestaltresonance";
    public static final Logger LOGGER = LogUtils.getLogger();

    // --- Item registry (for future gestalt-related items) ---
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    // --- Gestalt acquisition items ---
    public static final DeferredItem<Item> NETHER_TEAR = ITEMS.registerItem("nether_tear", NetherTearItem::new, new Item.Properties());
    public static final DeferredItem<Item> RESONANT_POWDER = ITEMS.registerItem("resonant_powder", ResonantPowderItem::new, new Item.Properties());
    public static final DeferredItem<Item> SOUL_VESSEL_EMPTY = ITEMS.registerItem("soul_vessel_empty", SoulVesselEmptyItem::new, new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> SOUL_VESSEL_FRAGILE = ITEMS.registerItem("soul_vessel_fragile", SoulVesselFragileItem::new, new Item.Properties().stacksTo(1));

    // --- Dusty Documents ---
    public static final DeferredItem<Item> FILE_117_AMEN_BREAK = ITEMS.registerItem(
            "file_117_amen_break",
            props -> new DustyDocumentsItem(DustyDocumentContents.amenBreakFile117(), props),
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> FILE_42069_THE_GESTALT = ITEMS.registerItem(
            "file_42069_the_gestalt",
            props -> new DustyDocumentsItem(DustyDocumentContents.theGestaltFile42069(), props),
            new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> FILE_WRITABLE = ITEMS.registerItem(
            "file_writable",
            DustyDocumentsWritableItem::new,
            new Item.Properties().stacksTo(1).component(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY));

    // --- Creative tab ---
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GESTALT_TAB =
            CREATIVE_MODE_TABS.register("gestalt_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.gestaltresonance"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> {
                        net.minecraft.world.item.ItemStack icon = new net.minecraft.world.item.ItemStack(SOUL_VESSEL_EMPTY.get());
                        net.ragdot.gestaltresonance.common.item.SoulVesselEmptyItem.writeStoredGestalt(
                                icon, new net.ragdot.gestaltresonance.common.item.StoredGestaltData("gestaltresonance:amen_break", 1, 0));
                        return icon;
                    })
                    .displayItems((parameters, output) -> {
                        output.accept(NETHER_TEAR.get());
                        output.accept(RESONANT_POWDER.get());
                        output.accept(SOUL_VESSEL_EMPTY.get());
                        output.accept(SOUL_VESSEL_FRAGILE.get());
                        output.accept(FILE_117_AMEN_BREAK.get());
                        output.accept(FILE_42069_THE_GESTALT.get());
                        output.accept(FILE_WRITABLE.get());
                    }).build());

    public static net.minecraft.resources.ResourceLocation id(String path) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public GestaltResonance(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);

        // Register deferred registers
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        GestaltEntities.ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(GestaltEntities::onAttributeCreate);
        GestaltBlocks.BLOCKS.register(modEventBus);
        GestaltBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        GestaltAttachments.ATTACHMENT_TYPES.register(modEventBus);
        GestaltDataComponents.DATA_COMPONENTS.register(modEventBus);
        GestaltMobEffects.MOB_EFFECTS.register(modEventBus);
        GestaltSounds.SOUND_EVENTS.register(modEventBus);
        GestaltLootModifiers.register(modEventBus);
        GestaltParticles.PARTICLE_TYPES.register(modEventBus);

        // Game event bus listeners
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new GhostPlayerHandler());
        NeoForge.EVENT_BUS.register(new GestaltAcquisitionEvents());
        NeoForge.EVENT_BUS.register(new GestaltAttackEvents());
        NeoForge.EVENT_BUS.register(new GestaltGuardEvents());
        NeoForge.EVENT_BUS.register(new GestaltMiningEvents());
        NeoForge.EVENT_BUS.register(new GestaltLevelingEvents());
        NeoForge.EVENT_BUS.register(new GestaltThrowEvents());
        NeoForge.EVENT_BUS.register(new GestaltXpChannelEvents());
        NeoForge.EVENT_BUS.register(new GestaltChargedStrikeEvents());
        NeoForge.EVENT_BUS.register(new GestaltSkinUnlockEvents());
        NeoForge.EVENT_BUS.register(new GestaltResonanceEvents());
        NeoForge.EVENT_BUS.register(new GestaltSoulProjectionEvents());
        NeoForge.EVENT_BUS.register(new GestaltDamageBankingEvents());
        NeoForge.EVENT_BUS.register(new FloatPlayPassiveEvents());
        NeoForge.EVENT_BUS.register(GestaltIllusionEvents.INSTANCE);
        NeoForge.EVENT_BUS.addListener(GestaltCommand::register);

        // Gestalt powers — each power's static block / register() call adds itself to the
        // GestaltPowerRegistry. Per-power event listeners (e.g. abort handlers) also subscribe here.
        AmenBreakPower1G.register();
        AmenBreakPower1S.register();
        AmenBreakPower2B.register();
        AmenBreakPower2S.register();
        AmenBreakPower1B.register();
        AmenBreakPower3B.register();
        AmenBreakPower3G.register();
        AmenBreakPower3S.register();
        SpillwaysPower1B.register();
        SpillwaysPower1G.register();
        SpillwaysPower2B.register();
        NeoForge.EVENT_BUS.register(AmenBreakPower1G.EVENT_LISTENER);
        NeoForge.EVENT_BUS.register(AmenBreakPower2G.EVENT_LISTENER);
        NeoForge.EVENT_BUS.register(AmenBreakPower3G.EVENT_LISTENER);
        NeoForge.EVENT_BUS.register(AmenBreakPower3S.EVENT_LISTENER);
        NeoForge.EVENT_BUS.register(SpillwaysPower2G.EVENT_LISTENER);

        // Config
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("GestaltResonance common setup");
    }

    /** Sync gestalt state to the joining player for all online players (and their own state). */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer joiningPlayer) {
            // Sync the joining player's own state to themselves
            GestaltNetworking.syncToTracking(joiningPlayer);
            GestaltNetworking.syncGestaltXpToPlayer(joiningPlayer);
            GestaltNetworking.syncResonanceToPlayer(joiningPlayer);
            GestaltNetworking.syncSelectedSkinToTracking(joiningPlayer);
            GestaltNetworking.syncUnlockedSkinsToOwner(joiningPlayer);
            // Re-apply dormant effect if player is still dormant after relog
            GestaltMobEffects.syncDormantEffect(joiningPlayer);
            // Restore stored passive mob miniature display after relog
            var storedMobs = joiningPlayer.getData(GestaltAttachments.DOMINION_STORED_MOBS.get());
            if (!storedMobs.isEmpty()) {
                GestaltNetworking.syncStoredMobToPlayer(joiningPlayer, storedMobs.get(0));
            }

            // Sync all other players' states to the joining player
            var server = joiningPlayer.getServer();
            if (server != null) {
                for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                    if (other != joiningPlayer) {
                        GestaltNetworking.syncToTracking(other);
                        GestaltNetworking.syncSelectedSkinToTracking(other);
                    }
                }
            }
        }
    }

    /** Unsummon the gestalt when a player logs out so they start fresh on next login. */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            // Tear down soul projection cleanly first (no damage) so flight/ghost state
            // don't leak across the relog.
            if (state.isSoulProjecting()) {
                GestaltSoulProjectionEvents.teardown(player, SoulProjectionExitType.CLEAN, null, 0f);
                state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            }
            // Disarm Phase Out and Phase Court on logout — state should not survive reconnect.
            AmenBreakPower1G.disarm(player);
            AmenBreakPower2G.disarm(player);
            AmenBreakPower3G.disarm(player);
            AmenBreakPower3S.disarm(player);
            SpillwaysPower2G.disarm(player);
            SpillwaysPower1G.disarm(player);
            if (state.isSummoned()) {
                // Deactivate passive before clearing summon
                GestaltPassive passive = GestaltPassiveRegistry.getPassive(state.getGestaltId());
                if (passive != null) {
                    passive.onDeactivate(player);
                }
                state.setSummoned(false);
                state.clearLedgeGrab();
                state.clearWallSlide();
                player.setNoGravity(false);
                player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            }

            dismissAllOwnedBodyDoubles(player);
            var srv = player.getServer();
            if (srv != null) SpillwaysLightManager.clearAllForPlayer(srv, player.getUUID());
        }
    }

    /**
     * Discard every body double / illusion entity belonging to this player across all loaded
     * server levels. Used on logout, respawn, and dimension change so doubles don't outlive
     * their owner's session or get stranded in the old dimension.
     */
    private static void dismissAllOwnedBodyDoubles(ServerPlayer player) {
        var server = player.getServer();
        if (server == null) return;
        java.util.UUID uuid = player.getUUID();
        for (var level : server.getAllLevels()) {
            BodyDoubleEntity.dismissExistingDoubles(level, uuid);
            SpawnIllusionEntity.dismissExistingIllusions(level, uuid);
            TimePhaseBodyDoubleEntity.dismissExistingDoubles(level, uuid);
            TearProjectileEntity.dismissAllOwned(level, uuid);
        }
    }

    /** Reset transient gestalt state when the player respawns after death. */
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        AmenBreakPower1G.disarm(player);
        AmenBreakPower2G.disarm(player);
        AmenBreakPower3G.disarm(player);
        AmenBreakPower3S.disarm(player);
        SpillwaysPower2G.disarm(player);
        PhaseBlossomEntity.dismissBlossom(player.serverLevel(), player.getUUID());
        dismissAllOwnedBodyDoubles(player);

        // copyOnDeath() preserved summoned=true — dismiss cleanly so the first G press summons.
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (state.isSummoned()) {
            GestaltPassive passive = GestaltPassiveRegistry.getPassive(state.getGestaltId());
            if (passive != null) passive.onDeactivate(player);
            state.setSummoned(false);
            state.setAction(net.ragdot.gestaltresonance.common.GestaltAction.IDLE);
            state.clearLedgeGrab();
            state.clearWallSlide();
            player.setNoGravity(false);
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            GestaltNetworking.syncToTracking(player);
        }

        // copyOnDeath() preserves state server-side but the client attachment resets to
        // defaults — re-sync so the HUD and skin reflect the actual (preserved) state.
        GestaltNetworking.syncGestaltXpToPlayer(player);
        GestaltNetworking.syncSelectedSkinToTracking(player);
        GestaltNetworking.syncUnlockedSkinsToOwner(player);
    }

    /** Re-sync all client-side state after a dimension change — the client resets attachments on portal. */
    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Body doubles and illusions don't travel through portals — discard any that the player
        // owns in either the old or new dimension so they don't linger as invulnerable orphans.
        dismissAllOwnedBodyDoubles(player);
        GestaltNetworking.syncToTracking(player);
        GestaltNetworking.syncGestaltXpToPlayer(player);
        GestaltNetworking.syncResonanceToPlayer(player);
        GestaltNetworking.syncSelectedSkinToTracking(player);
        GestaltNetworking.syncUnlockedSkinsToOwner(player);
        GestaltNetworking.syncPhaseOutToPlayer(player);
        GestaltNetworking.syncMoistAirToPlayer(player);
        GestaltNetworking.syncPhaseCourtToPlayer(player);
        GestaltNetworking.syncTimePhaseToPlayer(player);
        // Restore stored passive mob miniature display after dimension change
        var storedMobs = player.getData(GestaltAttachments.DOMINION_STORED_MOBS.get());
        if (!storedMobs.isEmpty()) {
            GestaltNetworking.syncStoredMobToPlayer(player, storedMobs.get(0));
        }
    }

    /** When a player starts tracking another player, sync the tracked player's gestalt state. */
    @SubscribeEvent
    public void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer && event.getTarget() instanceof ServerPlayer trackedPlayer) {
            GestaltNetworking.syncToTracking(trackedPlayer);
            GestaltNetworking.syncSelectedSkinToTracking(trackedPlayer);
        }
    }

    /** Tick ledge grab logic, guard expiry, and passive abilities for all online players. */
    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        GestaltDelayedPlacer.tick(server);
        DrowningDamageTracker.tick(server);
        SpillwaysLightManager.tick(server);
        SpillwaysPower2G.flushRestorations();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            LedgeGrabLogic.tickPlayer(player);
            WallSlideLogic.tickPlayer(player);
            GestaltSoulProjectionEvents.tickSoulProjection(player);
            AmenBreakPower1G.tick(player);
            AmenBreakPower2G.tick(player);
            AmenBreakPower3G.tick(player);
            AmenBreakPower3S.tick(player);
            SpillwaysPower2G.tick(player);
            SpillwaysPower1G.tick(player);

            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (player.isCreative()) {
                long now = server.getTickCount();
                if (state.clampAllCooldownsForCreative(now)) {
                    player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
                }
            }
            if (state.isSummoned()) {
                GestaltPassive passive = GestaltPassiveRegistry.getPassive(state.getGestaltId());
                if (passive != null) {
                    passive.tick(player);
                }
            }
        }
    }
}
