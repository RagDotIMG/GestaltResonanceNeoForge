package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Server-to-client: a player's mining state for remote rendering.
 * Tracking clients use this to drive the gestalt mining animation/pose for non-local players.
 */
public record SyncMiningStateS2C(int entityId, boolean mining) implements CustomPacketPayload {

    public static final Type<SyncMiningStateS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "sync_mining_state_s2c"));

    public static final StreamCodec<ByteBuf, SyncMiningStateS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncMiningStateS2C::entityId,
                    ByteBufCodecs.BOOL,    SyncMiningStateS2C::mining,
                    SyncMiningStateS2C::new
            );

    @Override
    public Type<SyncMiningStateS2C> type() {
        return TYPE;
    }
}
