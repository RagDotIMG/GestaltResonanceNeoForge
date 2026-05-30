package net.ragdot.gestaltresonance.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;

/**
 * Client-side screen effects during Phase Court (Power 3G).
 *
 * Three layered effects, each gated by a 0→1 intensity value that fades in/out:
 *   1. Fog color blended toward dark indigo — world takes on a ghostly violet tint.
 *   2. Far-plane fog compressed by up to 25% — slight depth reduction sells the liminal feel.
 *   3. Full-screen dark-purple GUI overlay at low alpha — adds a subtle "barely here" vignette.
 */
public class PhaseCourtClientHandler {

    private static float intensity  = 0f;
    private static float intensityO = 0f;

    private static final float FADE_IN_SPEED  = 0.06f;
    private static final float FADE_OUT_SPEED = 0.08f;

    // Target fog color (dark indigo)
    private static final float FOG_TARGET_R = 0.04f;
    private static final float FOG_TARGET_G = 0.01f;
    private static final float FOG_TARGET_B = 0.16f;

    // How much of the original fog color to displace at full intensity (0 = none, 1 = full replace)
    private static final float FOG_COLOR_STRENGTH = 0.55f;

    // Far-plane scale factor at full intensity (0.78 = fog wall 22% closer)
    private static final float FOG_FAR_SCALE = 0.78f;

    // Full-screen tint at max intensity: dark purple, ~12% alpha
    private static final int SCREEN_TINT_RGB = 0x0D0018;
    private static final int SCREEN_TINT_MAX_ALPHA = 0x1F; // ~12%

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        intensityO = intensity;

        if (mc.player == null) {
            intensity = Math.max(0f, intensity - FADE_OUT_SPEED);
            return;
        }

        PlayerGestaltState state = mc.player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (state.isPhaseCourtActive()) {
            intensity = Math.min(1f, intensity + FADE_IN_SPEED);
        } else {
            intensity = Math.max(0f, intensity - FADE_OUT_SPEED);
        }
    }

    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (intensity <= 0f) return;
        float t = intensity * FOG_COLOR_STRENGTH;
        event.setRed(event.getRed()   + (FOG_TARGET_R - event.getRed())   * t);
        event.setGreen(event.getGreen() + (FOG_TARGET_G - event.getGreen()) * t);
        event.setBlue(event.getBlue()  + (FOG_TARGET_B - event.getBlue())  * t);
    }

    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (intensity <= 0f) return;
        // Compress far plane linearly: scale=1 at intensity=0, scale=FOG_FAR_SCALE at intensity=1.
        float scale = 1f - (1f - FOG_FAR_SCALE) * intensity;
        event.scaleFarPlaneDistance(scale);
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (intensity <= 0f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int alpha = (int) (SCREEN_TINT_MAX_ALPHA * intensity);
        int color = (alpha << 24) | SCREEN_TINT_RGB;
        event.getGuiGraphics().fill(0, 0, screenW, screenH, color);
    }
}
