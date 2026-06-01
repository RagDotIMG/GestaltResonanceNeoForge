package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.GestaltCosts;

/**
 * Server-to-client sync of Time Phase (3S) state.
 * Sent to the owning player whenever Time Phase state changes.
 * Also carries the tracked entity ID list so the client can suppress their renders.
 */
public record SyncTimePhaseS2C(
        boolean active,
        boolean predictionPhase,
        int ticksRemaining,
        int cooldownTicks,
        int bodyDoubleEntityId,
        int trackedCount,
        int[] trackedEntityIds
) implements CustomPacketPayload {

    public static final Type<SyncTimePhaseS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "time_phase_state_sync"));

    public static final StreamCodec<ByteBuf, SyncTimePhaseS2C> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        ByteBufCodecs.BOOL.encode(buf, pkt.active());
                        ByteBufCodecs.BOOL.encode(buf, pkt.predictionPhase());
                        ByteBufCodecs.VAR_INT.encode(buf, pkt.ticksRemaining());
                        ByteBufCodecs.VAR_INT.encode(buf, pkt.cooldownTicks());
                        ByteBufCodecs.VAR_INT.encode(buf, pkt.bodyDoubleEntityId());
                        ByteBufCodecs.VAR_INT.encode(buf, pkt.trackedCount());
                        for (int id : pkt.trackedEntityIds()) {
                            ByteBufCodecs.VAR_INT.encode(buf, id);
                        }
                    },
                    buf -> {
                        boolean active = ByteBufCodecs.BOOL.decode(buf);
                        boolean predictionPhase = ByteBufCodecs.BOOL.decode(buf);
                        int ticksRemaining = ByteBufCodecs.VAR_INT.decode(buf);
                        int cooldownTicks = ByteBufCodecs.VAR_INT.decode(buf);
                        int bodyDoubleEntityId = ByteBufCodecs.VAR_INT.decode(buf);
                        int trackedCount = ByteBufCodecs.VAR_INT.decode(buf);
                        int[] ids = new int[GestaltCosts.TIME_PHASE_MAX_ENTITIES];
                        for (int i = 0; i < ids.length; i++) {
                            ids[i] = ByteBufCodecs.VAR_INT.decode(buf);
                        }
                        return new SyncTimePhaseS2C(active, predictionPhase, ticksRemaining, cooldownTicks,
                                bodyDoubleEntityId, trackedCount, ids);
                    }
            );

    @Override
    public Type<SyncTimePhaseS2C> type() {
        return TYPE;
    }
}
