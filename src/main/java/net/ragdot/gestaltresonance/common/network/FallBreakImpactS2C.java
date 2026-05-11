package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Server-to-client: signals a fall break landing impact for a player.
 * Clients trigger a short shake VFX on the gestalt renderer.
 */
public record FallBreakImpactS2C(int entityId) implements CustomPacketPayload {

    public static final Type<FallBreakImpactS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "fall_break_impact"));

    public static final StreamCodec<ByteBuf, FallBreakImpactS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FallBreakImpactS2C::entityId,
                    FallBreakImpactS2C::new
            );

    @Override
    public Type<FallBreakImpactS2C> type() {
        return TYPE;
    }
}
