package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Server-to-client sync of Moist Air HUD state.
 * Sent to the owning player whenever Moist Air toggle state changes.
 */
public record MoistAirStateSyncS2C(
        boolean active
) implements CustomPacketPayload {

    public static final Type<MoistAirStateSyncS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "moist_air_state_sync"));

    public static final StreamCodec<ByteBuf, MoistAirStateSyncS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, MoistAirStateSyncS2C::active,
                    MoistAirStateSyncS2C::new
            );

    @Override
    public Type<MoistAirStateSyncS2C> type() {
        return TYPE;
    }
}
