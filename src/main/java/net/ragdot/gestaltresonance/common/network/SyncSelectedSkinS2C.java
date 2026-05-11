package net.ragdot.gestaltresonance.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/** Server-to-client: a player's selected gestalt skin changed. Goes to all tracking clients + self. */
public record SyncSelectedSkinS2C(int entityId, ResourceLocation skinId) implements CustomPacketPayload {

    public static final Type<SyncSelectedSkinS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "sync_selected_skin"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncSelectedSkinS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncSelectedSkinS2C::entityId,
                    ResourceLocation.STREAM_CODEC, SyncSelectedSkinS2C::skinId,
                    SyncSelectedSkinS2C::new
            );

    @Override
    public Type<SyncSelectedSkinS2C> type() {
        return TYPE;
    }
}
