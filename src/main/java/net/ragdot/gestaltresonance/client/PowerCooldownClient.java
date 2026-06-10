package net.ragdot.gestaltresonance.client;

import net.minecraft.client.Minecraft;

import java.util.Arrays;

/**
 * Client-side storage for per-power-slot cooldown state.
 * Used to render the darkened cooldown overlay on the power grid in GestaltManagementScreen.
 *
 * index = slot.ordinal() * 3 + modifier.ordinal() (0–8), matching the grid's r*3+c layout.
 */
public final class PowerCooldownClient {

    private static final long[] startTick = new long[9];
    private static final int[]  total     = new int[9];

    static { Arrays.fill(startTick, -1L); }

    /** Called from SyncPowerCooldownS2C handler when a cooldown just started. */
    public static void set(int index, int totalTicks) {
        if (index < 0 || index >= 9 || totalTicks <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        startTick[index] = mc.level.getGameTime();
        total[index] = totalTicks;
    }

    /**
     * Called from existing sync packets that carry remaining ticks
     * (PhaseOutStateSyncS2C for 2G, SyncTimePhaseS2C for 3S, SyncPhaseCourtS2C for 3G).
     */
    public static void setFromRemaining(int index, int remainingTicks, int totalTicks) {
        if (index < 0 || index >= 9) return;
        if (remainingTicks <= 0 || totalTicks <= 0) {
            startTick[index] = -1L;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        startTick[index] = mc.level.getGameTime() - (totalTicks - remainingTicks);
        total[index] = totalTicks;
    }

    /**
     * Returns the fraction of the cooldown that has elapsed [0..1),
     * or -1 if no active cooldown for this slot.
     * 0 = just started (full dark overlay); approaching 1 = nearly done (thin sliver at top).
     */
    public static float getOverlayFill(int index, long now) {
        if (index < 0 || index >= 9) return -1f;
        long start = startTick[index];
        int t = total[index];
        if (start < 0 || t <= 0) return -1f;
        float fill = (float)(now - start) / t;
        if (fill >= 1.0f) {
            startTick[index] = -1L;
            return -1f;
        }
        return Math.max(0f, fill);
    }

    private PowerCooldownClient() {}
}
