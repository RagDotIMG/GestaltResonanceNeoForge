package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * S2C: syncs the stored passive mob NBT to the owning client.
 * An empty CompoundTag signals that the stored slot is now empty.
 */
public record DominionStoredMobSyncS2C(CompoundTag nbt) implements CustomPacketPayload {

    public static final Type<DominionStoredMobSyncS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "dominion_stored_mob_sync"));

    public static final StreamCodec<ByteBuf, DominionStoredMobSyncS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.COMPOUND_TAG, DominionStoredMobSyncS2C::nbt,
                    DominionStoredMobSyncS2C::new
            );

    @Override
    public Type<DominionStoredMobSyncS2C> type() {
        return TYPE;
    }
}
