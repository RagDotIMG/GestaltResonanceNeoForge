package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Client-to-server packet: request toggle of gestalt summoned state.
 * No payload data needed — the server knows which player sent it.
 */
public record ToggleSummonC2S() implements CustomPacketPayload {

    public static final Type<ToggleSummonC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "toggle_summon"));

    public static final StreamCodec<ByteBuf, ToggleSummonC2S> STREAM_CODEC =
            StreamCodec.unit(new ToggleSummonC2S());

    @Override
    public Type<ToggleSummonC2S> type() {
        return TYPE;
    }
}
