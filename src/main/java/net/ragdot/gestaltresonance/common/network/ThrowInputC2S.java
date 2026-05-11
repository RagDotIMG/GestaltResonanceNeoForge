package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/** Client-to-server: player pressed sneak+jump while gestalt is summoned and on the ground. */
public record ThrowInputC2S() implements CustomPacketPayload {

    public static final Type<ThrowInputC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "throw_input"));

    public static final StreamCodec<ByteBuf, ThrowInputC2S> STREAM_CODEC =
            StreamCodec.unit(new ThrowInputC2S());

    @Override
    public Type<ThrowInputC2S> type() {
        return TYPE;
    }
}
