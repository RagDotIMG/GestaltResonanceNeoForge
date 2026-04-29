package net.ragdot.gestaltresonance.common.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

import java.util.Optional;

/**
 * Server-to-client: sync a player's ledge grab state to tracking clients.
 * ledgePos is Optional — empty when not grabbing.
 * ledgeFaceIndex encodes the Direction ordinal (-1 when not grabbing).
 */
public record SyncLedgeGrabS2C(int entityId, boolean grabbing, Optional<BlockPos> ledgePos, int ledgeFaceIndex) implements CustomPacketPayload {

    public static final Type<SyncLedgeGrabS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "sync_ledge_grab"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncLedgeGrabS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncLedgeGrabS2C::entityId,
                    ByteBufCodecs.BOOL, SyncLedgeGrabS2C::grabbing,
                    ByteBufCodecs.optional(BlockPos.STREAM_CODEC), SyncLedgeGrabS2C::ledgePos,
                    ByteBufCodecs.VAR_INT, SyncLedgeGrabS2C::ledgeFaceIndex,
                    SyncLedgeGrabS2C::new
            );

    /** Helper to get the Direction from the encoded index, or null if invalid. */
    public Direction ledgeFace() {
        Direction[] dirs = Direction.values();
        return (ledgeFaceIndex >= 0 && ledgeFaceIndex < dirs.length) ? dirs[ledgeFaceIndex] : null;
    }

    @Override
    public Type<SyncLedgeGrabS2C> type() {
        return TYPE;
    }
}
