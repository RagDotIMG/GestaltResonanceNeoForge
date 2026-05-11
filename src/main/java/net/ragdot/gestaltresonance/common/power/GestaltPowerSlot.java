package net.ragdot.gestaltresonance.common.power;

/**
 * Numbered power slot — bound to the Z / X / C keybinds. Within a slot, different
 * {@link GestaltPowerModifier} chords select different concrete powers.
 */
public enum GestaltPowerSlot {
    POWER_1, POWER_2, POWER_3;

    public byte toByte() { return (byte) ordinal(); }

    public static GestaltPowerSlot fromByte(byte b) {
        GestaltPowerSlot[] values = values();
        int idx = Math.max(0, Math.min(values.length - 1, b));
        return values[idx];
    }
}
