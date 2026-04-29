package net.ragdot.gestaltresonance.common.passive;

import net.minecraft.server.level.ServerPlayer;

/**
 * Amen Break passive: makes the player invisible to sculk sensors
 * while the gestalt is summoned. The actual vibration dampening is
 * handled by a mixin on Entity.dampensVibrations().
 */
public class AmenBreakPassive implements GestaltPassive {

    @Override
    public void tick(ServerPlayer player) {
        // Vibration dampening is handled by the mixin; no per-tick work needed.
    }

    @Override
    public void onActivate(ServerPlayer player) {
        // No additional activation logic needed.
    }

    @Override
    public void onDeactivate(ServerPlayer player) {
        // No additional deactivation logic needed.
    }
}
