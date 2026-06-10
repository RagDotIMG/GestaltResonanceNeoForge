package net.ragdot.gestaltresonance.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltSounds;
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
    private static boolean prevAbilityActive = false;
    private static boolean prevPhaseCourtActive = false;
    private static SimpleSoundInstance ambiSound = null;
    // Fades in when Time Phase body double is dead — drives red fog tint
    private static float redIntensity  = 0f;
    private static float redIntensityO = 0f;

    private static final float FADE_IN_SPEED  = 0.06f;
    private static final float FADE_OUT_SPEED = 0.08f;

    // Target fog color (dark indigo)
    private static final float FOG_TARGET_R = 0.04f;
    private static final float FOG_TARGET_G = 0.01f;
    private static final float FOG_TARGET_B = 0.16f;

    // Target fog color when body double is dead (bright red)
    private static final float FOG_RED_R = 0.55f;
    private static final float FOG_RED_G = 0.02f;
    private static final float FOG_RED_B = 0.02f;

    // How much of the original fog color to displace at full intensity (0 = none, 1 = full replace)
    private static final float FOG_COLOR_STRENGTH = 0.80f;

    // Far-plane scale factor at full intensity (0.15 = fog wall 85% closer)
    private static final float FOG_FAR_SCALE = 0.15f;

    // Cached far-plane from the last frame our effect was inactive — used as the
    // compression baseline so other mods that modify fog distance don't skew our scale.
    private static float lastNormalFarPlane = 256f;

    // Full-screen tint at max intensity: dark purple, ~20% alpha
    private static final int SCREEN_TINT_RGB = 0x0D0018;
    private static final int SCREEN_TINT_MAX_ALPHA = 0x33; // ~20%

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        intensityO = intensity;
        redIntensityO = redIntensity;

        if (mc.player == null) {
            intensity = Math.max(0f, intensity - FADE_OUT_SPEED);
            redIntensity = Math.max(0f, redIntensity - FADE_OUT_SPEED);
            stopAmbiSound(mc);
            prevAbilityActive = false;
            prevPhaseCourtActive = false;
            return;
        }

        PlayerGestaltState state = mc.player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        boolean abilityActive = state.isPhaseCourtActive() || state.isTimePhaseActive();
        boolean phaseCourtActive = state.isPhaseCourtActive();

        if (abilityActive) {
            intensity = Math.min(1f, intensity + FADE_IN_SPEED);
        } else {
            intensity = Math.max(0f, intensity - FADE_OUT_SPEED);
        }

        boolean bdDead = state.isTimePhaseActive() && state.getTimePhaseBodyDoubleId() < 0;
        if (bdDead) {
            redIntensity = Math.min(1f, redIntensity + FADE_IN_SPEED);
        } else {
            redIntensity = Math.max(0f, redIntensity - FADE_OUT_SPEED);
        }

        // Restart sound when Phase Court activates mid-Time Phase (handoff), start fresh otherwise
        boolean handoff = phaseCourtActive && !prevPhaseCourtActive && prevAbilityActive;
        if ((abilityActive && !prevAbilityActive) || handoff) {
            stopAmbiSound(mc);
            ambiSound = SimpleSoundInstance.forUI(GestaltSounds.AMEN_BREAK_AMBI.get(), 1.0f);
            mc.getSoundManager().play(ambiSound);
        } else if (!abilityActive && prevAbilityActive) {
            stopAmbiSound(mc);
        }
        prevAbilityActive = abilityActive;
        prevPhaseCourtActive = phaseCourtActive;
    }

    private static void stopAmbiSound(Minecraft mc) {
        if (ambiSound != null) {
            mc.getSoundManager().stop(ambiSound);
            ambiSound = null;
        }
    }

    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (intensity <= 0f) return;
        float targetR = FOG_TARGET_R + (FOG_RED_R - FOG_TARGET_R) * redIntensity;
        float targetG = FOG_TARGET_G + (FOG_RED_G - FOG_TARGET_G) * redIntensity;
        float targetB = FOG_TARGET_B + (FOG_RED_B - FOG_TARGET_B) * redIntensity;
        float t = intensity * FOG_COLOR_STRENGTH;
        event.setRed(event.getRed()     + (targetR - event.getRed())   * t);
        event.setGreen(event.getGreen() + (targetG - event.getGreen()) * t);
        event.setBlue(event.getBlue()   + (targetB - event.getBlue())  * t);
    }

    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (intensity <= 0f) {
            // Cache the far plane each frame while we're not active so we have a clean
            // baseline the moment the ability kicks in, regardless of what other mods set.
            lastNormalFarPlane = event.getFarPlaneDistance();
            return;
        }
        // Push near to 0 so fog starts at the camera — without this, vanilla sets
        // nearPlane to ~75% of render distance in dry air, leaving almost no visible fog band
        // even with a drastically reduced farPlane.
        float scale = 1f - (1f - FOG_FAR_SCALE) * intensity;
        event.setNearPlaneDistance(0f);
        // Use our cached baseline instead of event.getFarPlaneDistance() so other mods
        // that already modified the far plane don't corrupt our compression ratio.
        event.setFarPlaneDistance(lastNormalFarPlane * scale);
        event.setCanceled(true);
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
