package net.ragdot.gestaltresonance.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * C2S: a soul-projecting player wants to perform an action on a target entity.
 * Currently only PICKUP (action=0) is used; the melee 3-hit chain piggybacks on the
 * existing AttackInputC2S and doesn't go through this packet.
 */
public record SoulProjectionActionC2S(byte action, int targetEntityId) implements CustomPacketPayload {

    public static final byte ACTION_PICKUP = 0;

    public static final Type<SoulProjectionActionC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "soul_projection_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SoulProjectionActionC2S> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BYTE,    SoulProjectionActionC2S::action,
                    ByteBufCodecs.VAR_INT, SoulProjectionActionC2S::targetEntityId,
                    SoulProjectionActionC2S::new
            );

    @Override
    public Type<SoulProjectionActionC2S> type() {
        return TYPE;
    }
}
