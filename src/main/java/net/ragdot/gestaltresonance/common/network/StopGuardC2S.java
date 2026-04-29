package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Client-to-server: player released right-click while guarding.
 * Server clears the guard state and notifies all tracking clients.
 */
public record StopGuardC2S() implements CustomPacketPayload {

    public static final Type<StopGuardC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "stop_guard"));

    public static final StreamCodec<ByteBuf, StopGuardC2S> STREAM_CODEC =
            StreamCodec.unit(new StopGuardC2S());

    @Override
    public Type<StopGuardC2S> type() {
        return TYPE;
    }
}
