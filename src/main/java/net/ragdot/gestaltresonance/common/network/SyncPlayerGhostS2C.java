package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Broadcast to all clients tracking the player (including self) so other players can
 * render the ghost/translucency effect during Phase Court and Time Phase.
 * Uses entity ID so the handler updates the correct player's data attachment,
 * not the receiving client's own player.
 */
public record SyncPlayerGhostS2C(
        int entityId,
        boolean phaseCourtActive,
        boolean timePhaseActive
) implements CustomPacketPayload {

    public static final Type<SyncPlayerGhostS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "sync_player_ghost"));

    public static final StreamCodec<ByteBuf, SyncPlayerGhostS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncPlayerGhostS2C::entityId,
                    ByteBufCodecs.BOOL,    SyncPlayerGhostS2C::phaseCourtActive,
                    ByteBufCodecs.BOOL,    SyncPlayerGhostS2C::timePhaseActive,
                    SyncPlayerGhostS2C::new
            );

    @Override
    public Type<SyncPlayerGhostS2C> type() {
        return TYPE;
    }
}
