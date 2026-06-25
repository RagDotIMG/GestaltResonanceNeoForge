package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/** Client-to-server: toggle Moist Air active state. Sent when X is pressed while guarding as Spillways. */
public record MoistAirToggleC2S() implements CustomPacketPayload {

    public static final Type<MoistAirToggleC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "moist_air_toggle"));

    public static final StreamCodec<ByteBuf, MoistAirToggleC2S> STREAM_CODEC =
            StreamCodec.unit(new MoistAirToggleC2S());

    @Override
    public Type<MoistAirToggleC2S> type() {
        return TYPE;
    }
}
