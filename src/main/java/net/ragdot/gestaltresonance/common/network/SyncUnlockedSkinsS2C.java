package net.ragdot.gestaltresonance.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

import java.util.List;

/**
 * Server-to-client: full set of skins this player has unlocked.
 * Sent only to the owning player — others don't need to know what's been unlocked,
 * only what's currently selected.
 */
public record SyncUnlockedSkinsS2C(List<ResourceLocation> unlockedSkins) implements CustomPacketPayload {

    public static final Type<SyncUnlockedSkinsS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "sync_unlocked_skins"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncUnlockedSkinsS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()),
                    SyncUnlockedSkinsS2C::unlockedSkins,
                    SyncUnlockedSkinsS2C::new
            );

    @Override
    public Type<SyncUnlockedSkinsS2C> type() {
        return TYPE;
    }
}
