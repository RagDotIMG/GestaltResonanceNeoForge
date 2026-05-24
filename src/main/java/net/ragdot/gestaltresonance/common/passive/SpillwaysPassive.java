package net.ragdot.gestaltresonance.common.passive;

import net.minecraft.server.level.ServerPlayer;

/**
 * Spillways passive:
 * - Air drains at half speed while the gestalt is summoned and the player is submerged.
 * - Underwater mining speed penalty is already cancelled for all summoned gestalts by
 *   GestaltMiningEvents.onBreakSpeed (tier override replaces vanilla's 0.2× multiplier).
 */
public class SpillwaysPassive implements GestaltPassive {

    @Override
    public void tick(ServerPlayer player) {
        if (!player.isUnderWater()) return;
        int air = player.getAirSupply();
        int maxAir = player.getMaxAirSupply();
        // Vanilla drains 1 unit/tick. Add 1 back every other tick → net 0.5 drain/tick.
        if (air > 0 && air < maxAir && player.tickCount % 2 == 0) {
            player.setAirSupply(air + 1);
        }
    }

    @Override
    public void onActivate(ServerPlayer player) {}

    @Override
    public void onDeactivate(ServerPlayer player) {}
}
