package net.ragdot.gestaltresonance.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/** Server-to-client: notify the owning player that a skin was just unlocked, so the client can show a toast. */
public record SkinUnlockedToastS2C(ResourceLocation skinId) implements CustomPacketPayload {

    public static final Type<SkinUnlockedToastS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "skin_unlocked_toast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SkinUnlockedToastS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, SkinUnlockedToastS2C::skinId,
                    SkinUnlockedToastS2C::new
            );

    @Override
    public Type<SkinUnlockedToastS2C> type() {
        return TYPE;
    }
}
