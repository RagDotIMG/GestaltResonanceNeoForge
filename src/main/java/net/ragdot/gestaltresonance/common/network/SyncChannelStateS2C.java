package net.ragdot.gestaltresonance.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Server-to-client: a player's XP channeling state changed.
 * {@code active} reflects whether the channel is running. {@code broken} is true only when
 * the channel was forcibly interrupted (e.g. damage), to differentiate from a clean release.
 */
public record SyncChannelStateS2C(int entityId, boolean active, boolean broken) implements CustomPacketPayload {

    public static final Type<SyncChannelStateS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "sync_channel_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncChannelStateS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncChannelStateS2C::entityId,
                    ByteBufCodecs.BOOL, SyncChannelStateS2C::active,
                    ByteBufCodecs.BOOL, SyncChannelStateS2C::broken,
                    SyncChannelStateS2C::new
            );

    @Override
    public Type<SyncChannelStateS2C> type() {
        return TYPE;
    }
}
