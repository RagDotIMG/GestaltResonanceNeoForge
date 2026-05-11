package net.ragdot.gestaltresonance.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.ragdot.gestaltresonance.common.GestaltAction;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltStats;
import net.ragdot.gestaltresonance.common.GestaltStatsRegistry;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;

/**
 * Draws a small bar below the crosshair to communicate hit-chain and charged-strike timing.
 *
 * Cooldown mode (after a chain or charged strike):
 *   Bar fills 0→1 as the shared cooldown expires, then fades out over 10 ticks.
 *
 * Windup mode (CHARGED_STRIKE_WINDUP active):
 *   Bar fills 0→1 over CHARGED_STRIKE_WINDUP_TICKS.
 *   Turns orange when fully charged AND a valid target is in crosshair range.
 *
 * Replaces ChargedStrikeCrosshairOverlay.
 */
public class GestaltCooldownHud {

    private static final int BAR_WIDTH    = 14;
    private static final int BAR_HEIGHT   = 2;
    private static final int BAR_Y_OFFSET = 7; // pixels below crosshair center

    private static final int COLOR_BG       = 0x88333333;
    private static final int COLOR_COOLDOWN = 0xFFBBBBBB;
    private static final int COLOR_WINDUP   = 0xFF999999;
    private static final int COLOR_READY    = 0xFFFF6020; // orange: fully charged + valid target

    private static final int FADE_OUT_TICKS = 10;

    // Cooldown state — set from GestaltNetworking when SyncCooldownS2C arrives
    private static long cooldownStartTime  = -1L;
    private static int  cooldownTotalTicks = 0;
    private static long cooldownReadyTime  = -1L;

    // Windup state — derived from action transitions each render frame
    private static long        windupStartTime = -1L;
    private static GestaltAction prevAction    = GestaltAction.IDLE;

    // -------------------------------------------------------------------------
    // Called from GestaltNetworking when a cooldown packet is received
    // -------------------------------------------------------------------------

    public static void onCooldownReceived(int totalTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        cooldownStartTime  = mc.level.getGameTime();
        cooldownTotalTicks = totalTicks;
        cooldownReadyTime  = -1L;
        windupStartTime    = -1L;
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    public static void onRenderGuiLayer(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.CROSSHAIR)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (!(mc.player instanceof LocalPlayer player)) return;
        if (mc.level == null) return;

        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned()) return;

        GestaltAction action = state.getAction();
        long now = mc.level.getGameTime();

        // Detect windup start
        if (action == GestaltAction.CHARGED_STRIKE_WINDUP
                && prevAction != GestaltAction.CHARGED_STRIKE_WINDUP) {
            windupStartTime   = now;
            cooldownStartTime = -1L;
            cooldownReadyTime = -1L;
        }
        prevAction = action;

        int cx = event.getGuiGraphics().guiWidth()  / 2;
        int cy = event.getGuiGraphics().guiHeight() / 2;
        int barX = cx - BAR_WIDTH / 2;
        int barY = cy + BAR_Y_OFFSET;

        if (action == GestaltAction.CHARGED_STRIKE_WINDUP) {
            renderWindup(event.getGuiGraphics(), player, state, now, barX, barY);
        } else {
            renderCooldown(event.getGuiGraphics(), now, barX, barY);
        }
    }

    private static void renderWindup(net.minecraft.client.gui.GuiGraphics g,
                                     LocalPlayer player, PlayerGestaltState state,
                                     long now, int barX, int barY) {
        if (windupStartTime < 0) windupStartTime = now;
        float fill = Mth.clamp((float)(now - windupStartTime) / GestaltCosts.CHARGED_STRIKE_WINDUP_TICKS, 0f, 1f);
        boolean validTarget = fill >= 1.0f && isCrosshairTargetValid(player, state);
        drawBar(g, barX, barY, fill, 1.0f, validTarget ? COLOR_READY : COLOR_WINDUP);
    }

    private static void renderCooldown(net.minecraft.client.gui.GuiGraphics g,
                                       long now, int barX, int barY) {
        if (cooldownStartTime < 0 || cooldownTotalTicks <= 0) return;

        float fill  = Mth.clamp((float)(now - cooldownStartTime) / cooldownTotalTicks, 0f, 1f);
        float alpha = 1.0f;

        if (fill >= 1.0f) {
            if (cooldownReadyTime < 0) cooldownReadyTime = now;
            float fadeElapsed = now - cooldownReadyTime;
            if (fadeElapsed >= FADE_OUT_TICKS) {
                cooldownStartTime = -1L;
                return;
            }
            alpha = 1.0f - fadeElapsed / FADE_OUT_TICKS;
        }

        drawBar(g, barX, barY, fill, alpha, COLOR_COOLDOWN);
    }

    private static void drawBar(net.minecraft.client.gui.GuiGraphics g,
                                int barX, int barY, float fill, float alpha, int fillColor) {
        g.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, withAlpha(COLOR_BG, alpha));
        int fillPx = Mth.clamp(Math.round(fill * BAR_WIDTH), 0, BAR_WIDTH);
        if (fillPx > 0) {
            g.fill(barX, barY, barX + fillPx, barY + BAR_HEIGHT, withAlpha(fillColor, alpha));
        }
    }

    private static int withAlpha(int argb, float alpha) {
        int a = (int) ((argb >>> 24) * alpha) & 0xFF;
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static boolean isCrosshairTargetValid(LocalPlayer player, PlayerGestaltState state) {
        Entity picked = Minecraft.getInstance().crosshairPickEntity;
        if (!(picked instanceof LivingEntity le) || !le.isAlive()) return false;
        GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());
        int rng = (stats != null) ? stats.range() : 0;
        return player.distanceTo(le) <= 1 + 2 * rng;
    }
}
