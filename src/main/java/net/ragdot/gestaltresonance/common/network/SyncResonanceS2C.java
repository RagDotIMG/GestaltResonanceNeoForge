package net.ragdot.gestaltresonance.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Server-to-client: sync a player's resonance value to the owning client for the HUD.
 */
public record SyncResonanceS2C(int entityId, int resonanceValue) implements CustomPacketPayload {

    public static final Type<SyncResonanceS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "sync_resonance"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncResonanceS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncResonanceS2C::entityId,
                    ByteBufCodecs.VAR_INT, SyncResonanceS2C::resonanceValue,
                    SyncResonanceS2C::new
            );

    @Override
    public Type<SyncResonanceS2C> type() {
        return TYPE;
    }
}
