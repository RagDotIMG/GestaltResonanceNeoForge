package net.ragdot.gestaltresonance.common.network;

import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Server-to-client: sync a player's wall slide state to tracking clients.
 * wallFaceIndex encodes the Direction ordinal (-1 when not sliding).
 */
public record SyncWallSlideS2C(int entityId, boolean sliding, int wallFaceIndex) implements CustomPacketPayload {

    public static final Type<SyncWallSlideS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "sync_wall_slide"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncWallSlideS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncWallSlideS2C::entityId,
                    ByteBufCodecs.BOOL, SyncWallSlideS2C::sliding,
                    ByteBufCodecs.VAR_INT, SyncWallSlideS2C::wallFaceIndex,
                    SyncWallSlideS2C::new
            );

    /** Helper to get the Direction from the encoded index, or null if invalid. */
    public Direction wallFace() {
        Direction[] dirs = Direction.values();
        return (wallFaceIndex >= 0 && wallFaceIndex < dirs.length) ? dirs[wallFaceIndex] : null;
    }

    @Override
    public Type<SyncWallSlideS2C> type() {
        return TYPE;
    }
}
