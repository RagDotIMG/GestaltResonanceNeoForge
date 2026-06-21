package net.ragdot.gestaltresonance.common.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Client-to-server: request to start ledge grab at a specific block position and face.
 * Server will re-validate before accepting.
 */
public record StartLedgeGrabC2S(BlockPos ledgePos, Direction face) implements CustomPacketPayload {

    public static final Type<StartLedgeGrabC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "start_ledge_grab"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StartLedgeGrabC2S> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, StartLedgeGrabC2S::ledgePos,
                    Direction.STREAM_CODEC, StartLedgeGrabC2S::face,
                    StartLedgeGrabC2S::new
            );

    @Override
    public Type<StartLedgeGrabC2S> type() {
        return TYPE;
    }
}
