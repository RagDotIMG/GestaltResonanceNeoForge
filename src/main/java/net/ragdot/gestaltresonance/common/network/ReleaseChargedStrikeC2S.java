package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Client-to-server: player released left-click while in CHARGED_STRIKE_WINDUP.
 * targetEntityId is the entity the client's crosshair was pointing at on release, or -1 if none.
 * The server validates the entity (alive, living, in range) and the windup duration before firing.
 */
public record ReleaseChargedStrikeC2S(int targetEntityId) implements CustomPacketPayload {

    public static final Type<ReleaseChargedStrikeC2S> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "release_charged_strike"));

    public static final StreamCodec<ByteBuf, ReleaseChargedStrikeC2S> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ReleaseChargedStrikeC2S::targetEntityId,
                    ReleaseChargedStrikeC2S::new
            );

    @Override
    public Type<ReleaseChargedStrikeC2S> type() {
        return TYPE;
    }
}
