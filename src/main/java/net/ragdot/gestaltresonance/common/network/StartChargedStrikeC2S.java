package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/** Client-to-server: player started holding left-click while in GUARD or HIT_1/HIT_2 — request charged-strike windup. */
public record StartChargedStrikeC2S() implements CustomPacketPayload {

    public static final Type<StartChargedStrikeC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "start_charged_strike"));

    public static final StreamCodec<ByteBuf, StartChargedStrikeC2S> STREAM_CODEC =
            StreamCodec.unit(new StartChargedStrikeC2S());

    @Override
    public Type<StartChargedStrikeC2S> type() {
        return TYPE;
    }
}
