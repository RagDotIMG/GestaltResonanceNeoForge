package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/** Client-to-server: player released Sneak or SummonToggle, end the XP channel. */
public record StopChannelXpC2S() implements CustomPacketPayload {

    public static final Type<StopChannelXpC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "stop_channel_xp"));

    public static final StreamCodec<ByteBuf, StopChannelXpC2S> STREAM_CODEC =
            StreamCodec.unit(new StopChannelXpC2S());

    @Override
    public Type<StopChannelXpC2S> type() {
        return TYPE;
    }
}
