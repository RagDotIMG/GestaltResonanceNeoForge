package net.ragdot.gestaltresonance.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltStats;
import net.ragdot.gestaltresonance.common.GestaltStatsRegistry;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;

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
    }

    /** Scales the alpha channel of an ARGB color by the given [0..1] multiplier. */
    private static int withAlpha(int argb, float alpha) {
        int a = (int) ((argb >>> 24) * alpha) & 0xFF;
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
