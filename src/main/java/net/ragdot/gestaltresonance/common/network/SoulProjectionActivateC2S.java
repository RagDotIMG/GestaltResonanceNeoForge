package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

public record SoulProjectionActivateC2S() implements CustomPacketPayload {

    public static final Type<SoulProjectionActivateC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "soul_projection_activate"));

    public static final StreamCodec<ByteBuf, SoulProjectionActivateC2S> STREAM_CODEC =
            StreamCodec.unit(new SoulProjectionActivateC2S());

    @Override
    public Type<SoulProjectionActivateC2S> type() {
        return TYPE;
    }
}
