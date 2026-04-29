package net.ragdot.gestaltresonance;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.ragdot.gestaltresonance.client.GestaltKeybinds;
import net.ragdot.gestaltresonance.client.GestaltFirstPersonRenderer;
import net.ragdot.gestaltresonance.client.GestaltXpOverlay;
import net.ragdot.gestaltresonance.client.gestalt.AmenBreakModel;
import net.ragdot.gestaltresonance.client.gestalt.GestaltModel;
import net.ragdot.gestaltresonance.client.GestaltPlayerLayer;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;

@Mod(value = GestaltResonance.MODID, dist = Dist.CLIENT)
public class GestaltResonanceClient {

    public GestaltResonanceClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(GestaltKeybinds::register);
        modEventBus.addListener(GestaltResonanceClient::registerLayerDefinitions);
        modEventBus.addListener(GestaltResonanceClient::addPlayerLayers);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        GestaltResonance.LOGGER.info("GestaltResonance client setup");
        NeoForge.EVENT_BUS.addListener(GestaltKeybinds::onClientTick);
        NeoForge.EVENT_BUS.addListener(GestaltKeybinds::onInteractionKeyMappingTriggered);
        NeoForge.EVENT_BUS.addListener(GestaltFirstPersonRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(GestaltResonanceClient::onClientLevelTick);
        NeoForge.EVENT_BUS.addListener(GestaltResonanceClient::onMovementInput);
        NeoForge.EVENT_BUS.addListener(GestaltXpOverlay::onRenderGuiLayer);

        // Bridge: when the network handler observes a chain transition, reset the
        // client-side animation state directly. Avoids the IDLE-skip race condition.
        GestaltNetworking.onChainTransitionCallback = GestaltModel::notifyChainEnd;
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
        }
    }

    private static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(AmenBreakModel.LAYER, AmenBreakModel::createBodyLayer);
    }

    private static void addPlayerLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            var renderer = event.getSkin(skin);
            if (renderer instanceof PlayerRenderer playerRenderer) {
                AmenBreakModel model = new AmenBreakModel(event.getEntityModels().bakeLayer(AmenBreakModel.LAYER));
                playerRenderer.addLayer(new GestaltPlayerLayer(playerRenderer, model));
                // Share the model instance with the first-person renderer
                GestaltFirstPersonRenderer.setModel(model);
            }
        }
    }
}
