package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/** Client-to-server: player started holding Sneak+SummonToggle, request XP channel. */
public record StartChannelXpC2S() implements CustomPacketPayload {

    public static final Type<StartChannelXpC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "start_channel_xp"));

    public static final StreamCodec<ByteBuf, StartChannelXpC2S> STREAM_CODEC =
            StreamCodec.unit(new StartChannelXpC2S());

    @Override
    public Type<StartChannelXpC2S> type() {
        return TYPE;
    }
}
