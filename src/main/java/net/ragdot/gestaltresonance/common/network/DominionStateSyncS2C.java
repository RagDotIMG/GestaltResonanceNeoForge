package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * S2C: notifies clients that a living entity has entered or left a Dominion field.
 * Clients use this to show/hide the bubble overlay.
 */
public record DominionStateSyncS2C(int entityId, boolean active) implements CustomPacketPayload {

    public static final Type<DominionStateSyncS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "dominion_state_sync"));

    public static final StreamCodec<ByteBuf, DominionStateSyncS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DominionStateSyncS2C::entityId,
                    ByteBufCodecs.BOOL, DominionStateSyncS2C::active,
                    DominionStateSyncS2C::new
            );

    @Override
    public Type<DominionStateSyncS2C> type() {
        return TYPE;
    }
}
