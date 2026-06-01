package net.ragdot.gestaltresonance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.ragdot.gestaltresonance.common.item.SoulVesselEmptyItem;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.ragdot.gestaltresonance.client.entity.GestaltExplosionParticle;
import net.ragdot.gestaltresonance.client.entity.GestaltIllusionParticle;
import net.ragdot.gestaltresonance.client.entity.PhaseBlossomRenderer;
import net.ragdot.gestaltresonance.common.GestaltParticles;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.ragdot.gestaltresonance.client.GestaltCooldownHud;
import net.ragdot.gestaltresonance.client.GestaltStatusHud;
import net.ragdot.gestaltresonance.client.GestaltKeybinds;
import net.ragdot.gestaltresonance.client.PhaseCourtClientHandler;
import net.ragdot.gestaltresonance.client.SoulProjectionClientHandler;
import net.ragdot.gestaltresonance.client.SoulProjectionClientInput;
import net.ragdot.gestaltresonance.client.ClientAfterimageManager;
import net.ragdot.gestaltresonance.client.entity.BodyDoubleRenderer;
import net.ragdot.gestaltresonance.client.entity.PhaseAfterimageRenderer;
import net.ragdot.gestaltresonance.client.entity.PhaseMineModel;
import net.ragdot.gestaltresonance.client.entity.PhaseMineRenderer;
import net.ragdot.gestaltresonance.common.GestaltEntities;
import net.ragdot.gestaltresonance.client.GestaltResonanceHud;
import net.ragdot.gestaltresonance.client.GestaltFirstPersonRenderer;
import net.ragdot.gestaltresonance.client.GestaltXpOverlay;
import net.ragdot.gestaltresonance.client.entity.DripDropRenderer;
import net.ragdot.gestaltresonance.client.entity.PopPodRenderer;
import net.ragdot.gestaltresonance.client.entity.TearProjectileRenderer;
import net.ragdot.gestaltresonance.client.entity.PrimedBlockRenderer;
import net.ragdot.gestaltresonance.client.entity.SpawnIllusionRenderer;
import net.ragdot.gestaltresonance.client.entity.TimePhaseBodyDoubleRenderer;
import net.ragdot.gestaltresonance.client.gestalt.AmenBreakModel;
import net.ragdot.gestaltresonance.client.gestalt.GestaltModel;
import net.ragdot.gestaltresonance.client.gestalt.SpillwaysModel;
import net.ragdot.gestaltresonance.common.GestaltIds;
import net.ragdot.gestaltresonance.client.GestaltPlayerLayer;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;
import net.ragdot.gestaltresonance.common.network.PhaseOutStateSyncS2C;
import net.ragdot.gestaltresonance.common.network.SyncPhaseCourtS2C;

@Mod(value = GestaltResonance.MODID, dist = Dist.CLIENT)
public class GestaltResonanceClient {

    public GestaltResonanceClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(GestaltKeybinds::register);
        modEventBus.addListener(GestaltResonanceClient::registerLayerDefinitions);
        modEventBus.addListener(GestaltResonanceClient::addPlayerLayers);
        modEventBus.addListener(GestaltResonanceClient::registerEntityRenderers);
        modEventBus.addListener(GestaltResonanceClient::registerParticles);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        GestaltResonance.LOGGER.info("GestaltResonance client setup");
        event.enqueueWork(() -> ItemProperties.register(
                GestaltResonance.SOUL_VESSEL_EMPTY.get(),
                ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "filled"),
                (stack, level, entity, seed) -> SoulVesselEmptyItem.hasStoredGestalt(stack) ? 1.0f : 0.0f));
        NeoForge.EVENT_BUS.addListener(GestaltKeybinds::onClientTick);
        NeoForge.EVENT_BUS.addListener(GestaltKeybinds::onInteractionKeyMappingTriggered);
        NeoForge.EVENT_BUS.addListener(GestaltKeybinds::onLivingJump);
        NeoForge.EVENT_BUS.addListener(GestaltFirstPersonRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(GestaltResonanceClient::onClientLevelTick);
        NeoForge.EVENT_BUS.addListener(GestaltResonanceClient::onEntityLeaveLevel);
        NeoForge.EVENT_BUS.addListener(GestaltResonanceClient::onMovementInput);
        NeoForge.EVENT_BUS.addListener(GestaltXpOverlay::onRenderGuiLayer);
        NeoForge.EVENT_BUS.addListener(GestaltCooldownHud::onRenderGuiLayer);
        NeoForge.EVENT_BUS.addListener(GestaltResonanceHud::onRenderGui);
        NeoForge.EVENT_BUS.addListener(GestaltStatusHud::onRenderGui);
        NeoForge.EVENT_BUS.addListener(SoulProjectionClientHandler::onCameraAngles);
        NeoForge.EVENT_BUS.addListener(PhaseCourtClientHandler::onFogColor);
        NeoForge.EVENT_BUS.addListener(PhaseCourtClientHandler::onRenderFog);
        NeoForge.EVENT_BUS.addListener(PhaseCourtClientHandler::onRenderGui);
        NeoForge.EVENT_BUS.addListener(ClientAfterimageManager::onClientLevelTick);
        NeoForge.EVENT_BUS.addListener(ClientAfterimageManager::onRenderLevelStage);
        GestaltNetworking.onSpawnAfterimageCallback = packet -> ClientAfterimageManager.add(
                new net.ragdot.gestaltresonance.client.ClientAfterimage(
                        packet.id(), packet.x(), packet.y(), packet.z(),
                        packet.sourceEntityId(), packet.opacity(), packet.fadeRate(), packet.tint()));
        GestaltNetworking.onDiscardAfterimageCallback = packet -> ClientAfterimageManager.remove(packet.id());
        GestaltNetworking.onClearAfterimagesCallback = ClientAfterimageManager::clear;
        // Soul projection client-side input gating (block break/place/use) and movement prediction
        NeoForge.EVENT_BUS.addListener(SoulProjectionClientInput::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(SoulProjectionClientInput::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(SoulProjectionClientInput::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(SoulProjectionClientInput::onRightClickEmpty);
        NeoForge.EVENT_BUS.addListener(SoulProjectionClientInput::onRightClickEntity);
        NeoForge.EVENT_BUS.addListener(SoulProjectionClientInput::onMovementInput);

        // Bridge: when the network handler observes a chain transition, reset the
        // client-side animation state directly. Avoids the IDLE-skip race condition.
        GestaltNetworking.onChainTransitionCallback = GestaltModel::notifyChainEnd;

        // Bridge: when a gestalt transitions to unsummoned, reset animation state so
        // the next summon triggers a fresh intro (setupAnim isn't called while invisible).
        GestaltNetworking.onGestaltUnsummonCallback = GestaltModel::notifyUnsummon;

        // Bridge: fall break impact packet → gestalt shake VFX.
        GestaltNetworking.onFallBreakImpactCallback = uuid -> {
            Minecraft mc = Minecraft.getInstance();
            long tick = (mc.level != null) ? mc.level.getGameTime() : 0L;
            GestaltPlayerLayer.triggerImpactShake(uuid, tick);
        };

        // Bridge: soul projection state change → camera shake feedback.
        GestaltNetworking.onSoulProjectionStateCallback = projecting -> {
            if (projecting) SoulProjectionClientHandler.triggerActivationShake();
        };

        // Bridge: soul projection yank → shake intensity + sound vary by exit type.
        GestaltNetworking.onSoulProjectionYankCallback = SoulProjectionClientHandler::onYank;

        // Bridge: Phase Out state change → activation shake when ghost window starts.
        GestaltNetworking.onPhaseOutStateCallback = GestaltResonanceClient::onPhaseOutState;

        // Bridge: Phase Court post-hit freeze starts → screen shake.
        GestaltNetworking.onPhaseCourtStateCallback = GestaltResonanceClient::onPhaseCourtState;

        // Bridge: local player's gestalt crash → status icon cooldown fill.
        GestaltNetworking.onSelfCrashCallback = GestaltStatusHud::onSelfCrash;

        // Bridge: hit-chain impact packet → single wind-charge gust particle.
        GestaltNetworking.onHitParticlesCallback = packet -> {
            net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
            if (level == null) return;
            level.addParticle(net.minecraft.core.particles.ParticleTypes.GUST,
                    packet.x(), packet.y(), packet.z(), 0.0, 0.0, 0.0);
        };
    }

    /** Freeze all movement input when ledge grabbing, before vanilla processes it. */
    private static void onMovementInput(MovementInputUpdateEvent event) {
        if (event.getEntity() instanceof net.minecraft.client.player.LocalPlayer player) {
            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            if (state.isLedgeGrabbing()) {
                var input = event.getInput();
                input.leftImpulse = 0;
                input.forwardImpulse = 0;
                input.up = false;
                input.down = false;
                input.left = false;
                input.right = false;
                input.jumping = false;
                input.shiftKeyDown = false;
            }
        }
    }

    /** Tick summon progress for all players visible on the client. */
    private static void onClientLevelTick(LevelTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != event.getLevel()) return;
        for (Player player : mc.level.players()) {
            PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            state.tickSummonProgress();
            GestaltPlayerLayer.tickSmoothedYaw(player);


            // Client-side travel progression for charged strike. Server is authoritative for the
            // strike trigger (transitions to HIT_3 via SyncAttackActionS2C). The client mirrors
            // the per-tick advancement so renderers can lerp smoothly toward the target.
            if (state.getAction() == net.ragdot.gestaltresonance.common.GestaltAction.CHARGED_STRIKE_TRAVEL) {
                int tier = state.getChargedStrikeSpeedTier();
                if (tier >= 1 && tier < net.ragdot.gestaltresonance.common.GestaltCosts.CHARGED_STRIKE_TRAVEL_SPEED_BY_SPD.length) {
                    double speed = net.ragdot.gestaltresonance.common.GestaltCosts.CHARGED_STRIKE_TRAVEL_SPEED_BY_SPD[tier];
                    if (speed > 0) {
                        state.setChargedStrikeTraveled(state.getChargedStrikeTraveled() + speed);
                    }
                }
            }
        }
    }

    private static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GestaltEntities.BODY_DOUBLE.get(), BodyDoubleRenderer::new);
        event.registerEntityRenderer(GestaltEntities.PRIMED_BLOCK.get(), PrimedBlockRenderer::new);
        event.registerEntityRenderer(GestaltEntities.POP_POD.get(), PopPodRenderer::new);
        event.registerEntityRenderer(GestaltEntities.DRIP_DROP.get(), DripDropRenderer::new);
        event.registerEntityRenderer(GestaltEntities.PHASE_MINE.get(), PhaseMineRenderer::new);
        event.registerEntityRenderer(GestaltEntities.PHASE_AFTERIMAGE.get(), PhaseAfterimageRenderer::new);
        event.registerEntityRenderer(GestaltEntities.SPAWN_ILLUSION.get(), SpawnIllusionRenderer::new);
        event.registerEntityRenderer(GestaltEntities.PHASE_BLOSSOM.get(), PhaseBlossomRenderer::new);
        event.registerEntityRenderer(GestaltEntities.TIME_PHASE_BODY_DOUBLE.get(), TimePhaseBodyDoubleRenderer::new);
        event.registerEntityRenderer(GestaltEntities.TEAR_PROJECTILE.get(), TearProjectileRenderer::new);
    }

    private static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(GestaltParticles.GESTALT_ILLUSION.get(), GestaltIllusionParticle.Provider::new);
        event.registerSpriteSet(GestaltParticles.GESTALT_EXPLOSION.get(), GestaltExplosionParticle.Provider::new);
    }

    private static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(AmenBreakModel.LAYER, AmenBreakModel::createBodyLayer);
        event.registerLayerDefinition(SpillwaysModel.LAYER, SpillwaysModel::createBodyLayer);
        event.registerLayerDefinition(PhaseMineModel.LAYER, PhaseMineModel::createBodyLayer);
    }

    private static void addPlayerLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            var renderer = event.getSkin(skin);
            if (renderer instanceof PlayerRenderer playerRenderer) {
                AmenBreakModel amenBreakModel = new AmenBreakModel(event.getEntityModels().bakeLayer(AmenBreakModel.LAYER));
                SpillwaysModel spillwaysModel = new SpillwaysModel(event.getEntityModels().bakeLayer(SpillwaysModel.LAYER));
                java.util.Map<ResourceLocation, GestaltModel> models = java.util.Map.of(
                        GestaltIds.AMEN_BREAK, amenBreakModel,
                        GestaltIds.SPILLWAYS, spillwaysModel
                );
                playerRenderer.addLayer(new GestaltPlayerLayer(playerRenderer, models));
                GestaltFirstPersonRenderer.setModels(models);
            }
        }
    }

    private static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof AbstractClientPlayer player) {
            GestaltModel.removePlayer(player.getUUID());
        }
    }

    private static boolean prevPhaseOutActive = false;

    private static void onPhaseOutState(PhaseOutStateSyncS2C packet) {
        if (packet.active() && !prevPhaseOutActive) {
            SoulProjectionClientHandler.triggerActivationShake();
        }
        prevPhaseOutActive = packet.active();
        GestaltResonanceHud.onPhaseOutState(packet);
    }

    private static boolean prevPhaseCourtFreezeActive = false;

    private static void onPhaseCourtState(SyncPhaseCourtS2C packet) {
        if (packet.postHitFreezeActive() && !prevPhaseCourtFreezeActive) {
            SoulProjectionClientHandler.triggerActivationShake();
        }
        prevPhaseCourtFreezeActive = packet.postHitFreezeActive();
    }
}
