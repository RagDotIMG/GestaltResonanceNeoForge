package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/** Server-to-client: wipe all client-side afterimages (Time Phase end, Phase Court teardown, disarm). */
public record ClearAfterimagesS2C() implements CustomPacketPayload {

    public static final Type<ClearAfterimagesS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "clear_afterimages"));

    public static final StreamCodec<ByteBuf, ClearAfterimagesS2C> STREAM_CODEC =
            StreamCodec.unit(new ClearAfterimagesS2C());

    @Override
    public Type<ClearAfterimagesS2C> type() { return TYPE; }
}
