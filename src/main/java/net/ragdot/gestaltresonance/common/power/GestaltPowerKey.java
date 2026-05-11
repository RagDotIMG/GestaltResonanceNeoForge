package net.ragdot.gestaltresonance.common.power;

import net.minecraft.resources.ResourceLocation;

/**
 * Identifies a specific power: which gestalt owns it, which slot, and which chord modifier
 * activates it. Used as the registry key.
 */
public record GestaltPowerKey(ResourceLocation gestaltId,
                              GestaltPowerSlot slot,
                              GestaltPowerModifier modifier) {
}
