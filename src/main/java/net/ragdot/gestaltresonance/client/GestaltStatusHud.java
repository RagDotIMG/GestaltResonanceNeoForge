package net.ragdot.gestaltresonance.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltStats;
import net.ragdot.gestaltresonance.common.GestaltStatsRegistry;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;

/**
 * Bottom-right HUD icon showing the current gestalt state.
 *
 * Priority (highest shown first):
 *   UNAVAILABLE – no gestalt; dark blue, static
 *   COOLDOWN    – crash cooldown active; dark blue with white fill growing from bottom
 *   DESPERATE   – summoned + >80% dissonance; pulsing red (sine wave)
 *   HEIGHTENED  – summoned + at max resonance; gold
 *   SUMMONED    – summoned, normal; white
 *   UNSUMMONED  – available but not summoned; dim white, fades to invisible after 3 s
 */
public class GestaltStatusHud {

    // Each gestalt supplies its own icon sprite; only Amen Break exists so far.
    private static final ResourceLocation SPRITE_AB =
            ResourceLocation.fromNamespaceAndPath("gestaltresonance", "hud/ab_hud_icon");

    private static final int ICON_SIZE = 32;

    private static final int COLOR_UNAVAILABLE = 0xFF2255BB; // dark blue  (matches dissonance bar)
    private static final int COLOR_DESPERATE   = 0xFFFF3030; // red        (pulsing)
    private static final int COLOR_HEIGHTENED  = 0xFFFFD700; // gold       (matches resonance bar)
    private static final int COLOR_SUMMONED    = 0xFFFFFFFF; // white
    private static final int COLOR_UNSUMMONED  = 0xFFAAAAAA; // dim white

    private static final int UNSUMMONED_SHOW_TICKS = 60; // 3 s at full opacity before fade
    private static final int UNSUMMONED_FADE_TICKS = 20; // fade-out duration

    // Set by the crash callback; -1 means no active cooldown tracking.
    private static long crashCooldownStart = -1L;
    // Tracks summon→unsummon transition for the fade timer.
    private static boolean prevSummoned   = false;
    private static long    unsummonedTime = -1L;

    /** Called from GestaltNetworking when the local player's gestalt crashes. */
    public static void onSelfCrash() {
        Minecraft mc = Minecraft.getInstance();
        crashCooldownStart = (mc.level != null) ? mc.level.getGameTime() : 0L;
        unsummonedTime = -1L; // reset so the fade timer starts fresh after cooldown ends
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (!(mc.player instanceof LocalPlayer player)) return;
        if (mc.level == null) return;

        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        long now = mc.level.getGameTime();

        int screenW = event.getGuiGraphics().guiWidth();
        int screenH = event.getGuiGraphics().guiHeight();
        // Left of hotbar (hotbar left edge = screenW/2 - 91), 3px gap, vertically centred then raised 5px.
        int iconX   = screenW / 2 - 91 - 3 - ICON_SIZE;
        int iconY   = screenH - 22 + (22 - ICON_SIZE) / 2 - 5;

        ResourceLocation sprite = SPRITE_AB;

        // ── 1. No gestalt — unavailable ─────────────────────────────────────
        if (state.getGestaltId().equals(PlayerGestaltState.NONE)) {
            prevSummoned = false;
            renderTinted(event.getGuiGraphics(), sprite, iconX, iconY, COLOR_UNAVAILABLE, 1.0f);
            return;
        }

        // ── 2. Crash cooldown — dark blue with fill ──────────────────────────
        if (crashCooldownStart >= 0) {
            long elapsed = now - crashCooldownStart;
            if (elapsed < GestaltCosts.CRASH_COOLDOWN_TICKS) {
                prevSummoned = false;
                unsummonedTime = -1L;
                float fill = (float) elapsed / GestaltCosts.CRASH_COOLDOWN_TICKS;
                renderCooldownFill(event.getGuiGraphics(), sprite, iconX, iconY, fill);
                return;
            }
            crashCooldownStart = -1L; // cooldown just expired
        }

        // ── Unsummon-time tracking (needed for state 6) ──────────────────────
        boolean currSummoned = state.isSummoned();
        if (prevSummoned && !currSummoned) {
            unsummonedTime = now; // summon→unsummon transition: start countdown
        }
        if (!currSummoned && unsummonedTime < 0) {
            unsummonedTime = now; // first render while unsummoned (e.g. initial login)
        }
        if (currSummoned) {
            unsummonedTime = -1L; // summoned: clear so it resets on next unsummon
        }
        prevSummoned = currSummoned;

        if (currSummoned) {
            GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());

            // ── 3. Desperate struggle — pulsing red ─────────────────────────
            if (state.isDesperateStruggle(stats)) {
                float pulse = (float) Math.sin(now * 0.3) * 0.5f + 0.5f;
                renderTinted(event.getGuiGraphics(), sprite, iconX, iconY, COLOR_DESPERATE, 0.5f + 0.5f * pulse);
                return;
            }

            // ── 4. Heightened — gold ─────────────────────────────────────────
            if (stats != null) {
                int maxRes = GestaltCosts.maxResonance(stats.resonance());
                if (maxRes > 0 && state.getResonanceValue() >= maxRes) {
                    renderTinted(event.getGuiGraphics(), sprite, iconX, iconY, COLOR_HEIGHTENED, 1.0f);
                    return;
                }
            }

            // ── 5. Normal summoned — white ────────────────────────────────────
            renderTinted(event.getGuiGraphics(), sprite, iconX, iconY, COLOR_SUMMONED, 1.0f);
            return;
        }

        // ── 6. Unsummoned — dim white, fades to invisible after 3 s ─────────
        long idleTicks = unsummonedTime >= 0 ? (now - unsummonedTime) : Long.MAX_VALUE;
        if (idleTicks >= UNSUMMONED_SHOW_TICKS + UNSUMMONED_FADE_TICKS) return;
        float alpha = idleTicks < UNSUMMONED_SHOW_TICKS
                ? 1.0f
                : 1.0f - (float)(idleTicks - UNSUMMONED_SHOW_TICKS) / UNSUMMONED_FADE_TICKS;
        renderTinted(event.getGuiGraphics(), sprite, iconX, iconY, COLOR_UNSUMMONED, alpha);
    }

    private static void renderTinted(GuiGraphics g, ResourceLocation sprite,
                                      int x, int y, int argb, float extraAlpha) {
        float a  = ((argb >>> 24) / 255.0f) * extraAlpha;
        float r  = ((argb >> 16) & 0xFF)    / 255.0f;
        float gr = ((argb >> 8)  & 0xFF)    / 255.0f;
        float b  = ( argb        & 0xFF)    / 255.0f;
        RenderSystem.setShaderColor(r, gr, b, a);
        g.blitSprite(sprite, x, y, ICON_SIZE, ICON_SIZE);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * Renders the icon with a dark-blue background and a white fill that grows from the bottom
     * as the crash cooldown elapses (fill=0 at cooldown start, fill=1 when ready).
     */
    private static void renderCooldownFill(GuiGraphics g, ResourceLocation sprite,
                                            int x, int y, float fill) {
        renderTinted(g, sprite, x, y, COLOR_UNAVAILABLE, 1.0f);
        int fillH = Mth.clamp(Math.round(fill * ICON_SIZE), 0, ICON_SIZE);
        if (fillH > 0) {
            int vOffset = ICON_SIZE - fillH;
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            g.blitSprite(sprite, ICON_SIZE, ICON_SIZE, 0, vOffset, x, y + vOffset, ICON_SIZE, fillH);
        }
    }
}
