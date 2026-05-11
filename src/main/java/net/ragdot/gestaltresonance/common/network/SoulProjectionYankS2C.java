package net.ragdot.gestaltresonance.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * S2C: tells the owning player's client that their soul projection has just ended.
 * Carries the exit type (for shake intensity / VFX selection), damage amount taken,
 * and the snap position the server teleported them to.
 */
public record SoulProjectionYankS2C(int entityId,
                                    byte exitType,
                                    float damageAmount,
                                    double snapX, double snapY, double snapZ)
        implements CustomPacketPayload {

    public static final Type<SoulProjectionYankS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "soul_projection_yank"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SoulProjectionYankS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SoulProjectionYankS2C::entityId,
                    ByteBufCodecs.BYTE,    SoulProjectionYankS2C::exitType,
                    ByteBufCodecs.FLOAT,   SoulProjectionYankS2C::damageAmount,
                    ByteBufCodecs.DOUBLE,  SoulProjectionYankS2C::snapX,
                    ByteBufCodecs.DOUBLE,  SoulProjectionYankS2C::snapY,
                    ByteBufCodecs.DOUBLE,  SoulProjectionYankS2C::snapZ,
                    SoulProjectionYankS2C::new
            );

    public Vec3 snapPosition() { return new Vec3(snapX, snapY, snapZ); }

    @Override
    public Type<SoulProjectionYankS2C> type() {
        return TYPE;
    }
}
