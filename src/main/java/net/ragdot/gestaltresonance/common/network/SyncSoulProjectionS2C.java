package net.ragdot.gestaltresonance.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * S2C: notify tracking clients (and self) that a player started or stopped soul projecting.
 * Carries the body double's network id and the projection anchor so the owning client can
 * predict range clamping. Max range is recomputed client-side from the player's RNG stat.
 *
 * When {@link #projecting()} is false, the other fields are filler (anchor at origin).
 */
public record SyncSoulProjectionS2C(int entityId,
                                    boolean projecting,
                                    int bodyDoubleEntityId,
                                    double anchorX, double anchorY, double anchorZ)
        implements CustomPacketPayload {

    public static final Type<SyncSoulProjectionS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "sync_soul_projection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncSoulProjectionS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncSoulProjectionS2C::entityId,
                    ByteBufCodecs.BOOL,    SyncSoulProjectionS2C::projecting,
                    ByteBufCodecs.VAR_INT, SyncSoulProjectionS2C::bodyDoubleEntityId,
                    ByteBufCodecs.DOUBLE,  SyncSoulProjectionS2C::anchorX,
                    ByteBufCodecs.DOUBLE,  SyncSoulProjectionS2C::anchorY,
                    ByteBufCodecs.DOUBLE,  SyncSoulProjectionS2C::anchorZ,
                    SyncSoulProjectionS2C::new
            );

    public Vec3 anchor() { return new Vec3(anchorX, anchorY, anchorZ); }

    @Override
    public Type<SyncSoulProjectionS2C> type() {
        return TYPE;
    }
}
