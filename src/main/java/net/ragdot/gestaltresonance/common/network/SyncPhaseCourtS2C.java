package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Server-to-client sync of Phase Court HUD state.
 * Sent to the owning player whenever Phase Court state changes.
 */
public record SyncPhaseCourtS2C(
        boolean active,
        int ticksRemaining,
        boolean breakCoreUsed,
        int cooldownTicks,
        boolean postHitFreezeActive
) implements CustomPacketPayload {

    public static final Type<SyncPhaseCourtS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "phase_court_state_sync"));

    public static final StreamCodec<ByteBuf, SyncPhaseCourtS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, SyncPhaseCourtS2C::active,
                    ByteBufCodecs.VAR_INT, SyncPhaseCourtS2C::ticksRemaining,
                    ByteBufCodecs.BOOL, SyncPhaseCourtS2C::breakCoreUsed,
                    ByteBufCodecs.VAR_INT, SyncPhaseCourtS2C::cooldownTicks,
                    ByteBufCodecs.BOOL, SyncPhaseCourtS2C::postHitFreezeActive,
                    SyncPhaseCourtS2C::new
            );

    @Override
    public Type<SyncPhaseCourtS2C> type() {
        return TYPE;
    }
}
