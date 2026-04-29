package net.ragdot.gestaltresonance.common;

/**
 * Central registry of all hunger/exhaustion costs for Gestalt actions.
 * Tune every drain number here; no other file should define raw exhaustion values.
 */
public final class GestaltCosts {

    /** Exhaustion applied every {@link #SUMMON_DRAIN_INTERVAL} ticks while a Gestalt is summoned. */
    public static final float SUMMON_DRAIN = 0.5f;

    /** How often (in ticks) the summon drain fires. 200 ticks = 10 seconds. */
    public static final int SUMMON_DRAIN_INTERVAL = 180;

    /** Exhaustion applied each time the guard absorbs incoming damage. */
    public static final float GUARD_ACTIVATION = 2.0f;

    /** Food level (inclusive) at or below which the gestalt force-unsummons. */
    public static final int CRASH_HUNGER_THRESHOLD = 6;

    /** Ticks the gestalt stays locked out after a hunger crash before it can be re-summoned. */
    public static final int CRASH_COOLDOWN_TICKS = 260;

    /** Accumulated absorbed damage required to trigger a guard break. */
    public static final float GUARD_BREAK_DAMAGE_THRESHOLD = 13f;

    /** Ticks the guard ability is locked out after a guard break. */
    public static final int GUARD_BREAK_COOLDOWN_TICKS = 280;

    /** Ticks between each guard damage decay pulse (1 point per interval). */
    public static final int GUARD_DECAY_INTERVAL = 6;

    private GestaltCosts() {}
}
