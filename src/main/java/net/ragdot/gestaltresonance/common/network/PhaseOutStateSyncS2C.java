package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Server-to-client sync of Phase Out HUD state.
 * Sent to the owning player whenever Phase Out state changes.
 */
public record PhaseOutStateSyncS2C(
        boolean armed,
        boolean active,
        int cooldownTicks,
        boolean canAfford
) implements CustomPacketPayload {

    public static final Type<PhaseOutStateSyncS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "phase_out_state_sync"));

    public static final StreamCodec<ByteBuf, PhaseOutStateSyncS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, PhaseOutStateSyncS2C::armed,
                    ByteBufCodecs.BOOL, PhaseOutStateSyncS2C::active,
                    ByteBufCodecs.VAR_INT, PhaseOutStateSyncS2C::cooldownTicks,
                    ByteBufCodecs.BOOL, PhaseOutStateSyncS2C::canAfford,
                    PhaseOutStateSyncS2C::new
            );

    @Override
    public Type<PhaseOutStateSyncS2C> type() {
        return TYPE;
    }
}
