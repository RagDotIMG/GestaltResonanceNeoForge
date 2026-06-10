package net.ragdot.gestaltresonance.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltStats;
import net.ragdot.gestaltresonance.common.GestaltStatsRegistry;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.network.PhaseOutStateSyncS2C;

/**
 * Draws the resonance/dissonance momentum bar at the bottom-left of the screen.
 *
 * Layout: equilibrium point divides the bar horizontally.
 *   Dissonance extends left  (dark blue fill).
 *   Resonance  extends right (gold fill).
 *   Segment dividers mark every 25-point boundary. The equilibrium divider is white.
 *
 * Only rendered when the gestalt is summoned.
 */
public class GestaltResonanceHud {

    private static final int SEGMENT_PX  = 20;  // pixels per 25-point segment
    private static final int BAR_HEIGHT  = 3;
    // Bar is right-anchored to the head icon's right edge: screenW/2 - 91 - 3.
    // barY sits between the head icon (which ends at screenH-32) and the hotbar (screenH-22).
    private static final int BAR_Y_OFFSET = 28;  // screenH - BAR_Y_OFFSET = fill top

    /** Ticks at 0 before the fade-out begins. */
    private static final int FADE_DELAY_TICKS = 200;
    /** Ticks over which the bar fades from full to invisible. */
    private static final int FADE_DURATION_TICKS = 20;

    private static final int COLOR_BG          = 0xAA222222;
    private static final int COLOR_DISSONANCE  = 0xFF2255BB;
    private static final int COLOR_RESONANCE   = 0xFFFFD700;
    private static final int COLOR_DIVIDER     = 0xFF555555;
    private static final int COLOR_EQUILIBRIUM = 0xFFFFFFFF;

    // Fade state: track when the resonance value last changed.
    private static int  lastTrackedValue  = Integer.MIN_VALUE;
    private static long lastChangedTime   = 0L;

    // Phase Out indicator state (updated from PhaseOutStateSyncS2C)
    private static boolean phaseOutArmed       = false;
    private static boolean phaseOutActive      = false;
    private static int     phaseOutCooldown    = 0;
    private static boolean phaseOutCanAfford   = false;

    /** Called from GestaltNetworking callback when Phase Out state changes. */
    public static void onPhaseOutState(PhaseOutStateSyncS2C packet) {
        phaseOutArmed     = packet.armed();
        phaseOutActive    = packet.active();
        phaseOutCooldown  = packet.cooldownTicks();
        phaseOutCanAfford = packet.canAfford();
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        if (!(mc.player instanceof LocalPlayer player)) return;
        if (mc.level == null) return;

        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned()) return;

        GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());
        if (stats == null) return;

        int value = state.getResonanceValue();
        long now  = mc.level.getGameTime();

        // Snap back immediately on any change; fadesnap = instant full alpha on change.
        if (value != lastTrackedValue) {
            lastTrackedValue = value;
            lastChangedTime  = now;
        }

        // Compute alpha: full while non-zero or recently changed; fade after FADE_DELAY_TICKS at 0.
        float alpha;
        if (value != 0) {
            alpha = 1.0f;
        } else {
            long idle = now - lastChangedTime;
            if (idle < FADE_DELAY_TICKS) {
                alpha = 1.0f;
            } else if (idle < FADE_DELAY_TICKS + FADE_DURATION_TICKS) {
                alpha = 1.0f - (float)(idle - FADE_DELAY_TICKS) / FADE_DURATION_TICKS;
            } else {
                alpha = 0f; // bar fully faded — indicator may still render if armed
            }
        }

        int res      = stats.resonance();
        int maxDis   = GestaltCosts.maxDissonance(res);
        int maxRes   = GestaltCosts.maxResonance(res);
        int disSeg   = maxDis / GestaltCosts.RESONANCE_SEGMENT_SIZE;
        int resSeg   = maxRes / GestaltCosts.RESONANCE_SEGMENT_SIZE;
        int totalSeg = disSeg + resSeg;

        if (totalSeg == 0 && !phaseOutArmed && !phaseOutActive && phaseOutCooldown <= 0) return;

        int screenW      = event.getGuiGraphics().guiWidth();
        int screenH      = event.getGuiGraphics().guiHeight();
        // Right-anchored to the head icon's right edge (screenW/2 - 91 - 3).
        // Bar extends leftward; this way wider bars (higher resonance stat) grow left, not right.
        int barRightX    = screenW / 2 - 91 - 3;
        int barWidth     = totalSeg * SEGMENT_PX;
        int barLeftX     = barRightX - barWidth;
        int barY         = screenH - BAR_Y_OFFSET;
        int equilibriumX = barLeftX + disSeg * SEGMENT_PX;

        var graphics = event.getGuiGraphics();
        boolean barFaded = (alpha == 0f);

        if (!barFaded) {
            // 1px white border (4 lines, no fill)
            int borderColor = withAlpha(COLOR_EQUILIBRIUM, alpha);
            graphics.fill(barLeftX - 1, barY - 1, barRightX + 1, barY,                    borderColor); // top
            graphics.fill(barLeftX - 1, barY + BAR_HEIGHT, barRightX + 1, barY + BAR_HEIGHT + 1, borderColor); // bottom
            graphics.fill(barLeftX - 1, barY - 1, barLeftX,               barY + BAR_HEIGHT + 1, borderColor); // left
            graphics.fill(barRightX,    barY - 1, barRightX + 1,          barY + BAR_HEIGHT + 1, borderColor); // right

            // Fill: resonance side (right of equilibrium)
            if (value > 0) {
                int fillPx = Math.min(Math.round((float) value / maxRes * resSeg * SEGMENT_PX), resSeg * SEGMENT_PX);
                if (fillPx > 0)
                    graphics.fill(equilibriumX, barY, equilibriumX + fillPx, barY + BAR_HEIGHT, withAlpha(COLOR_RESONANCE, alpha));
            }

            // Fill: dissonance side (left of equilibrium)
            if (value < 0) {
                int fillPx = Math.min(Math.round((float) -value / maxDis * disSeg * SEGMENT_PX), disSeg * SEGMENT_PX);
                if (fillPx > 0)
                    graphics.fill(equilibriumX - fillPx, barY, equilibriumX, barY + BAR_HEIGHT, withAlpha(COLOR_DISSONANCE, alpha));
            }

            // Segment dividers
            for (int i = 1; i < totalSeg; i++) {
                int divX = barLeftX + i * SEGMENT_PX;
                if (divX == equilibriumX) {
                    graphics.fill(divX, barY, divX + 3, barY + BAR_HEIGHT, withAlpha(COLOR_EQUILIBRIUM, alpha));
                } else {
                    graphics.fill(divX, barY, divX + 1, barY + BAR_HEIGHT, withAlpha(COLOR_DIVIDER, alpha));
                }
            }
        }

        // Phase Out indicator: attached to the LEFT of the bar so it doesn't overlap the hotbar.
        drawPhaseOutIndicator(graphics, barLeftX - PO_SIZE - 4, barY - 1);
    }

    // Phase Out indicator constants — square 5×5 inner area
    private static final int PO_SIZE       = 5;
    private static final int PO_COLOR_ARMED = 0xFFFFFFFF; // white
    private static final int PO_COLOR_OFF   = 0xFF666666; // grey

    private static void drawPhaseOutIndicator(net.minecraft.client.gui.GuiGraphics g, int x, int y) {
        if (!phaseOutArmed && !phaseOutActive && phaseOutCooldown <= 0) return;

        // Armed/active → full alpha; cooldown → dim grey/white, no border
        if (phaseOutCooldown > 0) {
            // No border during cooldown; fill at x,y directly
            g.fill(x, y, x + PO_SIZE, y + PO_SIZE, PO_COLOR_OFF);
            float fraction = 1f - (float) phaseOutCooldown / GestaltCosts.PHASE_OUT_COOLDOWN_TICKS;
            int fillH = Math.round(fraction * PO_SIZE);
            if (fillH > 0) {
                g.fill(x, y + PO_SIZE - fillH, x + PO_SIZE, y + PO_SIZE, PO_COLOR_ARMED);
            }
        } else {
            // Armed or active: 1px white border + solid white fill
            int bx = x - 1; int by = y - 1;
            int bx2 = x + PO_SIZE + 1; int by2 = y + PO_SIZE + 1;
            g.fill(bx, by,  bx2, by + 1,  COLOR_EQUILIBRIUM); // top
            g.fill(bx, by2 - 1, bx2, by2, COLOR_EQUILIBRIUM); // bottom
            g.fill(bx, by,  bx + 1, by2,  COLOR_EQUILIBRIUM); // left
            g.fill(bx2 - 1, by, bx2, by2, COLOR_EQUILIBRIUM); // right
            g.fill(x, y, x + PO_SIZE, y + PO_SIZE, PO_COLOR_ARMED);
        }
    }

    /** Scales the alpha channel of an ARGB color by the given [0..1] multiplier. */
    private static int withAlpha(int argb, float alpha) {
        int a = (int) ((argb >>> 24) * alpha) & 0xFF;
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
