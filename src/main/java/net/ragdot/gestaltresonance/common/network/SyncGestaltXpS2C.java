package net.ragdot.gestaltresonance.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Server-to-client packet: sync a player's gestalt level and stored XP to the owning client.
 */
public record SyncGestaltXpS2C(int entityId, int gestaltLevel, int gestaltXp) implements CustomPacketPayload {

    public static final Type<SyncGestaltXpS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "sync_gestalt_xp"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncGestaltXpS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncGestaltXpS2C::entityId,
                    ByteBufCodecs.VAR_INT, SyncGestaltXpS2C::gestaltLevel,
                    ByteBufCodecs.VAR_INT, SyncGestaltXpS2C::gestaltXp,
                    SyncGestaltXpS2C::new
            );

    @Override
    public Type<SyncGestaltXpS2C> type() {
        return TYPE;
    }
}
