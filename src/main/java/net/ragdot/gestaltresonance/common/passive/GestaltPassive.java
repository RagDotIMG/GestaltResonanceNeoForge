package net.ragdot.gestaltresonance.common.passive;

import net.minecraft.server.level.ServerPlayer;

/**
 * A passive ability granted by a summoned Gestalt.
 * Implementations must be server-side only.
 */
public interface GestaltPassive {

    /** Called every server tick while this gestalt is summoned. */
    void tick(ServerPlayer player);

    /** Called when the gestalt is summoned (passive activates). */
    void onActivate(ServerPlayer player);

    /** Called when the gestalt is desummoned (passive deactivates). */
    void onDeactivate(ServerPlayer player);
}
