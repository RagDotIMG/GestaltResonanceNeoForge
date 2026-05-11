package net.ragdot.gestaltresonance;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
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
import net.ragdot.gestaltresonance.common.GhostPlayerHandler;
import net.ragdot.gestaltresonance.common.GestaltAcquisitionEvents;
import net.ragdot.gestaltresonance.common.GestaltEntities;
import net.ragdot.gestaltresonance.common.GestaltSoulProjectionEvents;
import net.ragdot.gestaltresonance.common.SoulProjectionExitType;
import net.ragdot.gestaltresonance.common.entity.BodyDoubleEntity;
import net.ragdot.gestaltresonance.common.GestaltBlockEntities;
import net.ragdot.gestaltresonance.common.GestaltBlocks;
import net.ragdot.gestaltresonance.common.power.amen_break.AmenBreakPower1G;
import net.ragdot.gestaltresonance.common.power.amen_break.AmenBreakPower1S;
import net.ragdot.gestaltresonance.common.power.pop_pod.PopPodPower1B;
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
import net.ragdot.gestaltresonance.common.LedgeGrabLogic;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;
import net.ragdot.gestaltresonance.common.passive.GestaltPassive;
import net.ragdot.gestaltresonance.common.passive.GestaltPassiveRegistry;
import net.ragdot.gestaltresonance.common.WallSlideLogic;
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

    // Placeholder item so the creative tab has something to show
    public static final DeferredItem<Item> GESTALT_SHARD = ITEMS.registerSimpleItem("gestalt_shard", new Item.Properties());

    // --- Gestalt acquisition items ---
    public static final DeferredItem<Item> NETHER_TEAR = ITEMS.registerItem("nether_tear", NetherTearItem::new, new Item.Properties());
    public static final DeferredItem<Item> RESONANT_POWDER = ITEMS.registerItem("resonant_powder", ResonantPowderItem::new, new Item.Properties());
    public static final DeferredItem<Item> SOUL_VESSEL_EMPTY = ITEMS.registerItem("soul_vessel_empty", SoulVesselEmptyItem::new, new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> SOUL_VESSEL_FRAGILE = ITEMS.registerItem("soul_vessel_fragile", SoulVesselFragileItem::new, new Item.Properties().stacksTo(1));

    // --- Creative tab ---
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GESTALT_TAB =
            CREATIVE_MODE_TABS.register("gestalt_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.gestaltresonance"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> GESTALT_SHARD.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(GESTALT_SHARD.get());
                        output.accept(NETHER_TEAR.get());
                        output.accept(RESONANT_POWDER.get());
                        output.accept(SOUL_VESSEL_EMPTY.get());
                        output.accept(SOUL_VESSEL_FRAGILE.get());
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

        // Gestalt powers — each power's static block / register() call adds itself to the
        // GestaltPowerRegistry. Per-power event listeners (e.g. abort handlers) also subscribe here.
        AmenBreakPower1G.register();
        AmenBreakPower1S.register();
        PopPodPower1B.register();
        NeoForge.EVENT_BUS.register(AmenBreakPower1G.EVENT_LISTENER);

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

            // Discard any body doubles this player owns in all loaded levels
            var server = player.getServer();
            if (server != null) {
                for (var level : server.getAllLevels()) {
                    BodyDoubleEntity.dismissExistingDoubles(level, player.getUUID());
                }
            }
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
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            LedgeGrabLogic.tickPlayer(player);
            WallSlideLogic.tickPlayer(player);
            GestaltSoulProjectionEvents.tickSoulProjection(player);
            AmenBreakPower1G.tick(player);

            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (state.isSummoned()) {
                GestaltPassive passive = GestaltPassiveRegistry.getPassive(state.getGestaltId());
                if (passive != null) {
                    passive.tick(player);
                }
            }
        }
    }
}
