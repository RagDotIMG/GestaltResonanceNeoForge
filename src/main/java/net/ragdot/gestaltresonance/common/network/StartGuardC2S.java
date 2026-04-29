package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/** Client-to-server: player right-clicked with a low-priority interaction, start guarding. */
public record StartGuardC2S() implements CustomPacketPayload {

    public static final Type<StartGuardC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "start_guard"));

    public static final StreamCodec<ByteBuf, StartGuardC2S> STREAM_CODEC =
            StreamCodec.unit(new StartGuardC2S());

    @Override
    public Type<StartGuardC2S> type() {
        return TYPE;
    }
}
