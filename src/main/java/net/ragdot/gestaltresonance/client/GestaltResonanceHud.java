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
    private static final int BAR_X       = 199;   // left margin

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
                return; // fully faded out
            }
        }

        int res      = stats.resonance();
        int maxDis   = GestaltCosts.maxDissonance(res);
        int maxRes   = GestaltCosts.maxResonance(res);
        int disSeg   = maxDis / GestaltCosts.RESONANCE_SEGMENT_SIZE;
        int resSeg   = maxRes / GestaltCosts.RESONANCE_SEGMENT_SIZE;
        int totalSeg = disSeg + resSeg;

        if (totalSeg == 0) return;

        int barWidth     = totalSeg * SEGMENT_PX;
        int barY         = mc.getWindow().getGuiScaledHeight() - 56;
        int equilibriumX = BAR_X + disSeg * SEGMENT_PX;

        var graphics = event.getGuiGraphics();

        // 1px white border (4 lines, no fill)
        int borderColor = withAlpha(COLOR_EQUILIBRIUM, alpha);
        graphics.fill(BAR_X - 1, barY - 1, BAR_X + barWidth + 1, barY,                   borderColor); // top
        graphics.fill(BAR_X - 1, barY + BAR_HEIGHT, BAR_X + barWidth + 1, barY + BAR_HEIGHT + 1, borderColor); // bottom
        graphics.fill(BAR_X - 1, barY - 1, BAR_X,                         barY + BAR_HEIGHT + 1, borderColor); // left
        graphics.fill(BAR_X + barWidth, barY - 1, BAR_X + barWidth + 1,   barY + BAR_HEIGHT + 1, borderColor); // right

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
            int divX = BAR_X + i * SEGMENT_PX;
            if (divX == equilibriumX) {
                graphics.fill(divX, barY - 0, divX + 3, barY + BAR_HEIGHT + 0, withAlpha(COLOR_EQUILIBRIUM, alpha));
            } else {
                graphics.fill(divX, barY, divX + 1, barY + BAR_HEIGHT, withAlpha(COLOR_DIVIDER, alpha));
            }
        }

        // Phase Out indicator: small square to the right of the resonance bar.
        drawPhaseOutIndicator(graphics, BAR_X + barWidth + 4, barY - 1, alpha);
    }

    // Phase Out indicator constants
    private static final int PO_INNER_W = 5;
    private static final int PO_INNER_H = 7;
    private static final int PO_COLOR_ARMED  = 0xFFFFFFFF; // white
    private static final int PO_COLOR_OFF    = 0xFF666666; // grey

    private static void drawPhaseOutIndicator(net.minecraft.client.gui.GuiGraphics g,
                                               int x, int y, float alpha) {
        // Only show when there is something to communicate (armed, active, or on cooldown)
        if (!phaseOutArmed && !phaseOutActive && phaseOutCooldown <= 0) return;

        int borderColor = withAlpha(COLOR_EQUILIBRIUM, alpha);
        int outerW = PO_INNER_W + 2;
        int outerH = PO_INNER_H + 2;
        int ix = x + 1; // inner top-left X
        int iy = y + 1; // inner top-left Y

        // Outer 1px border
        g.fill(x, y, x + outerW, y + 1,             borderColor); // top
        g.fill(x, y + outerH - 1, x + outerW, y + outerH, borderColor); // bottom
        g.fill(x, y, x + 1, y + outerH,             borderColor); // left
        g.fill(x + outerW - 1, y, x + outerW, y + outerH, borderColor); // right

        // Inner fill
        if (phaseOutCooldown > 0) {
            // Cooldown: grey background, white fills from bottom as cooldown elapses
            g.fill(ix, iy, ix + PO_INNER_W, iy + PO_INNER_H, withAlpha(PO_COLOR_OFF, alpha));
            float fraction = 1f - (float) phaseOutCooldown / GestaltCosts.PHASE_OUT_COOLDOWN_TICKS;
            int fillH = Math.round(fraction * PO_INNER_H);
            if (fillH > 0) {
                int fillY = iy + PO_INNER_H - fillH;
                g.fill(ix, fillY, ix + PO_INNER_W, iy + PO_INNER_H, withAlpha(PO_COLOR_ARMED, alpha));
            }
        } else if (phaseOutArmed || phaseOutActive) {
            // Armed or ghost window active: solid white
            g.fill(ix, iy, ix + PO_INNER_W, iy + PO_INNER_H, withAlpha(PO_COLOR_ARMED, alpha));
        } else {
            // Off (no cooldown, not armed): solid grey
            g.fill(ix, iy, ix + PO_INNER_W, iy + PO_INNER_H, withAlpha(PO_COLOR_OFF, alpha));
        }
    }

    /** Scales the alpha channel of an ARGB color by the given [0..1] multiplier. */
    private static int withAlpha(int argb, float alpha) {
        int a = (int) ((argb >>> 24) * alpha) & 0xFF;
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
