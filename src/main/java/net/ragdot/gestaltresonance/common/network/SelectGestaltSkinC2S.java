package net.ragdot.gestaltresonance.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/** Client-to-server: player committed a skin selection in the management screen. */
public record SelectGestaltSkinC2S(ResourceLocation skinId) implements CustomPacketPayload {

    public static final Type<SelectGestaltSkinC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "select_gestalt_skin"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectGestaltSkinC2S> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, SelectGestaltSkinC2S::skinId,
                    SelectGestaltSkinC2S::new
            );

    @Override
    public Type<SelectGestaltSkinC2S> type() {
        return TYPE;
    }
}
