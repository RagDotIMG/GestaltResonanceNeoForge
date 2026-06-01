package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Server-to-client: spawn a client-only afterimage on the receiving player's screen.
 * fadeRate == 0 → persistent (cleared explicitly via Discard/ClearAll). Otherwise fades out.
 * tint is packed RGB (no alpha — opacity is carried separately as fadeRate/opacity fields).
 */
public record SpawnAfterimageS2C(
        int id,
        double x,
        double y,
        double z,
        int sourceEntityId,
        float opacity,
        float fadeRate,
        int tint
) implements CustomPacketPayload {

    public static final Type<SpawnAfterimageS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "spawn_afterimage"));

    public static final StreamCodec<ByteBuf, SpawnAfterimageS2C> STREAM_CODEC =
            StreamCodec.of(
                    (buf, pkt) -> {
                        ByteBufCodecs.VAR_INT.encode(buf, pkt.id());
                        ByteBufCodecs.DOUBLE.encode(buf, pkt.x());
                        ByteBufCodecs.DOUBLE.encode(buf, pkt.y());
                        ByteBufCodecs.DOUBLE.encode(buf, pkt.z());
                        ByteBufCodecs.VAR_INT.encode(buf, pkt.sourceEntityId());
                        ByteBufCodecs.FLOAT.encode(buf, pkt.opacity());
                        ByteBufCodecs.FLOAT.encode(buf, pkt.fadeRate());
                        ByteBufCodecs.VAR_INT.encode(buf, pkt.tint());
                    },
                    buf -> new SpawnAfterimageS2C(
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.DOUBLE.decode(buf),
                            ByteBufCodecs.DOUBLE.decode(buf),
                            ByteBufCodecs.DOUBLE.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.FLOAT.decode(buf),
                            ByteBufCodecs.FLOAT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf)
                    )
            );

    @Override
    public Type<SpawnAfterimageS2C> type() { return TYPE; }
}
