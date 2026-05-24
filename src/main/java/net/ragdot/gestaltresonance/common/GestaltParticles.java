package net.ragdot.gestaltresonance.common;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ragdot.gestaltresonance.GestaltResonance;

public class GestaltParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, GestaltResonance.MODID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GESTALT_ILLUSION =
            PARTICLE_TYPES.register("gestalt_illusion", () -> new SimpleParticleType(false));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GESTALT_EXPLOSION =
            PARTICLE_TYPES.register("gestalt_explosion", () -> new SimpleParticleType(false));
}
