package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Client-to-server: player released SPACE, end ledge grab and trigger mantle.
 */
public record StopLedgeGrabC2S() implements CustomPacketPayload {

    public static final Type<StopLedgeGrabC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "stop_ledge_grab"));

    public static final StreamCodec<ByteBuf, StopLedgeGrabC2S> STREAM_CODEC =
            StreamCodec.unit(new StopLedgeGrabC2S());

    @Override
    public Type<StopLedgeGrabC2S> type() {
        return TYPE;
    }
}
