package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Client-to-server: the local player's mining state changed.
 * Mining detection lives on the local client (it depends on local input + crosshair pick),
 * so we push the boolean to the server which then broadcasts it to tracking clients.
 */
public record SyncMiningStateC2S(boolean mining) implements CustomPacketPayload {

    public static final Type<SyncMiningStateC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "sync_mining_state_c2s"));

    public static final StreamCodec<ByteBuf, SyncMiningStateC2S> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, SyncMiningStateC2S::mining,
                    SyncMiningStateC2S::new
            );

    @Override
    public Type<SyncMiningStateC2S> type() {
        return TYPE;
    }
}
