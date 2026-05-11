package net.ragdot.gestaltresonance.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;

public class GestaltXpOverlay {

    // Vanilla 1.21.1 GUI sprites (via hud/* in the gui atlas)
    private static final ResourceLocation SPRITE_BG =
            ResourceLocation.withDefaultNamespace("hud/experience_bar_background");
    // Cyan recolor of the vanilla progress sprite, registered via atlases/gui.json
    private static final ResourceLocation SPRITE_PROGRESS =
            ResourceLocation.fromNamespaceAndPath("gestaltresonance", "hud/gestalt_xp_progress");

    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        boolean isBar   = event.getName().equals(VanillaGuiLayers.EXPERIENCE_BAR);
        boolean isLevel = event.getName().equals(VanillaGuiLayers.EXPERIENCE_LEVEL);
        if (!isBar && !isLevel) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (!(mc.player instanceof LocalPlayer player)) return;

        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned()) return;

        // Suppress both vanilla layers; render everything during the bar pass
        event.setCanceled(true);
        if (!isBar) return;

        int screenWidth  = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int xPos = screenWidth / 2 - 91;
        int yPos = screenHeight - 32 + 3;

        // Background — vanilla sprite (dark frame/container), same as vanilla XP bar
        event.getGuiGraphics().blitSprite(SPRITE_BG, xPos, yPos, 182, 5);

        // Filled portion — cyan recolor of the vanilla progress sprite, cropped to progress width
        float progress    = state.getGestaltXpProgress();
        int   filledWidth = (int) (progress * 183.0f);
        if (filledWidth > 0) {
            // blitSprite(sprite, texW, texH, uOffset, vOffset, x, y, drawW, drawH)
            // renders the left `filledWidth` px of the 182×5 sprite — mirrors vanilla's approach
            event.getGuiGraphics().blitSprite(SPRITE_PROGRESS, 182, 5, 0, 0, xPos, yPos, filledWidth, 5);
        }

        // Gestalt level in gold, centered above the bar, with drop shadow
        String levelStr = String.valueOf(state.getGestaltLevel());
        int textX = screenWidth / 2 - mc.font.width(levelStr) / 2;
        event.getGuiGraphics().drawString(mc.font, levelStr, textX, yPos - 6, 0xFFD700, true);
    }
}
