package net.ragdot.gestaltresonance.common;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.ragdot.gestaltresonance.GestaltResonance;

public class GestaltDamageTypes {

    public static final ResourceKey<DamageType> GESTALT = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "gestalt")
    );

    /** Build a DamageSource for use when the gestalt attacks on behalf of a player. */
    public static net.minecraft.world.damagesource.DamageSource gestalt(
            net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player owner) {
        return level.damageSources().source(GESTALT, owner, owner);
    }
}
