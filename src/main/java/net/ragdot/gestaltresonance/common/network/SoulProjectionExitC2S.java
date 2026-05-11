package net.ragdot.gestaltresonance.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * C2S: client requests an exit from soul projection. Carries the requested ExitType
 * (typically EMERGENCY for G-press; CLEAN is server-detected via right-click on body
 * double). The server validates and chooses the actual exit type.
 */
public record SoulProjectionExitC2S(byte exitType) implements CustomPacketPayload {

    public static final Type<SoulProjectionExitC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "soul_projection_exit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SoulProjectionExitC2S> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BYTE, SoulProjectionExitC2S::exitType,
                    SoulProjectionExitC2S::new
            );

    @Override
    public Type<SoulProjectionExitC2S> type() {
        return TYPE;
    }
}
