package net.ragdot.gestaltresonance.common;

/**
 * The five core statistics shared by every Gestalt.
 * All values are positive integers with no enforced upper bound.
 */
public record GestaltStats(int strength, int speed, int durability, int range, int resonance) {
}
