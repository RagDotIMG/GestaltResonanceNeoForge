package net.ragdot.gestaltresonance.common.power;

/**
 * Chord modifier inferred server-side from the player's current state at the moment
 * a power slot is activated. New modifiers can be added without breaking the wire format
 * because the modifier is never sent over the network.
 */
public enum GestaltPowerModifier {
    /** No chord — plain Z / X / C press. */
    NONE,
    /** Player is currently guarding (right-click held). */
    GUARD,
    /** Player is currently sneaking. */
    SNEAK
}
