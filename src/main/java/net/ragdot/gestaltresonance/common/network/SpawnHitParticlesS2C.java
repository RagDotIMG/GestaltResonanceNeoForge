package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Server-to-client: spawn wind-charge gust particles at an impact position.
 * hitNumber: 1 or 2 → GUST_EMITTER_SMALL, 3 → GUST_EMITTER_LARGE.
 */
public record SpawnHitParticlesS2C(float x, float y, float z, byte hitNumber) implements CustomPacketPayload {

    public static final Type<SpawnHitParticlesS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "spawn_hit_particles"));

    public static final StreamCodec<ByteBuf, SpawnHitParticlesS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, SpawnHitParticlesS2C::x,
                    ByteBufCodecs.FLOAT, SpawnHitParticlesS2C::y,
                    ByteBufCodecs.FLOAT, SpawnHitParticlesS2C::z,
                    ByteBufCodecs.BYTE,  SpawnHitParticlesS2C::hitNumber,
                    SpawnHitParticlesS2C::new
            );

    @Override
    public Type<SpawnHitParticlesS2C> type() {
        return TYPE;
    }
}
