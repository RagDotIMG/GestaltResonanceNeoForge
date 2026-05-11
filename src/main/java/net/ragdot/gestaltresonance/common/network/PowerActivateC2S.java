package net.ragdot.gestaltresonance.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * C2S: client requests activation of a power slot (1, 2, or 3).
 * The chord modifier (NONE / GUARD / SNEAK / ...) is determined server-side from current
 * state, never trusted from the client.
 */
public record PowerActivateC2S(byte slot) implements CustomPacketPayload {

    public static final Type<PowerActivateC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "power_activate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PowerActivateC2S> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BYTE, PowerActivateC2S::slot,
                    PowerActivateC2S::new
            );

    @Override
    public Type<PowerActivateC2S> type() {
        return TYPE;
    }
}
