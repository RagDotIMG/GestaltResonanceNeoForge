package net.ragdot.gestaltresonance.common;

/**
 * The active action the gestalt is performing.
 * Used to drive animation selection and to gate action-specific rendering behaviour
 * (e.g. yaw smoothing only applies while IDLE).
 *
 * This is transient (not serialised) — it is derived from the rest of the gestalt state
 * and reset to IDLE on deserialization / login.
 */
public enum GestaltAction {
    /** Default state: gestalt is summoned and following the player. */
    IDLE,
    /** Player is hanging from a ledge; gestalt is locked to the wall face. */
    LEDGE_GRAB,
    /** Gestalt is actively blocking / parrying an incoming attack. */
    GUARD,
    /** Gestalt has been thrown / launched as a projectile. */
    THROW,
    /** Gestalt is performing the first hit of a melee chain. */
    HIT_1,
    /** Gestalt is performing the second hit of a melee chain. */
    HIT_2,
    /** Gestalt is performing the third (final) hit of a melee chain. */
    HIT_3
}
