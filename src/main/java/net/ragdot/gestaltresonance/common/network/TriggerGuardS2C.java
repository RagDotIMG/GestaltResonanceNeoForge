package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Server-to-client: notify clients that a player's gestalt guard has activated or expired.
 * Clients update the target player's action state and play audio feedback on activation.
 */
public record TriggerGuardS2C(int entityId, boolean active) implements CustomPacketPayload {

    public static final Type<TriggerGuardS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "trigger_guard"));

    public static final StreamCodec<ByteBuf, TriggerGuardS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TriggerGuardS2C::entityId,
                    ByteBufCodecs.BOOL, TriggerGuardS2C::active,
                    TriggerGuardS2C::new
            );

    @Override
    public Type<TriggerGuardS2C> type() {
        return TYPE;
    }
}
