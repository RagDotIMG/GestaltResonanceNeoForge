package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Server-to-client: signals that a player's gestalt was force-unsummoned by a hunger crash.
 * Clients set the crashingOut flag to play a slower, amplified dismiss VFX.
 */
public record TriggerGestaltCrashS2C(int entityId) implements CustomPacketPayload {

    public static final Type<TriggerGestaltCrashS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "trigger_gestalt_crash"));

    public static final StreamCodec<ByteBuf, TriggerGestaltCrashS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TriggerGestaltCrashS2C::entityId,
                    TriggerGestaltCrashS2C::new
            );

    @Override
    public Type<TriggerGestaltCrashS2C> type() {
        return TYPE;
    }
}
