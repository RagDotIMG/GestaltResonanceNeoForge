package net.ragdot.gestaltresonance.common;

/**
 * Describes how a soul projection ended. Drives damage, cooldown, and client-side feedback.
 */
public enum SoulProjectionExitType {
    /** Right-click on own body double. No damage, short cooldown. */
    CLEAN,
    /** Voluntary G-press while projecting. Fixed 2 HP damage, clamped above 0. Long cooldown. */
    EMERGENCY,
    /** Body double was hit. Doubled damage from the source, can kill. Long cooldown. */
    FORCED,
    /** Hunger fell to crash threshold. 6 HP starve damage, can kill, increments crash count. Long cooldown. */
    CRASH;

    public byte toByte() { return (byte) ordinal(); }

    public static SoulProjectionExitType fromByte(byte b) {
        SoulProjectionExitType[] values = values();
        int idx = Math.max(0, Math.min(values.length - 1, b));
        return values[idx];
    }
}
