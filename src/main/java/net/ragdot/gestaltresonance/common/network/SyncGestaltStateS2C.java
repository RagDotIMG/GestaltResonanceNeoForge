package net.ragdot.gestaltresonance.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Server-to-client packet: sync a player's gestalt state to tracking clients.
 * Contains the target player's entity id so the client knows whose state to update.
 */
public record SyncGestaltStateS2C(int entityId, boolean summoned, ResourceLocation gestaltId) implements CustomPacketPayload {

    public static final Type<SyncGestaltStateS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "sync_gestalt_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncGestaltStateS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncGestaltStateS2C::entityId,
                    ByteBufCodecs.BOOL, SyncGestaltStateS2C::summoned,
                    ResourceLocation.STREAM_CODEC, SyncGestaltStateS2C::gestaltId,
                    SyncGestaltStateS2C::new
            );

    @Override
    public Type<SyncGestaltStateS2C> type() {
        return TYPE;
    }
}
