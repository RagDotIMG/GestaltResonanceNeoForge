package net.ragdot.gestaltresonance.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Server-to-client: a player's charged strike just entered the TRAVEL phase.
 * Carries the launch position, target entity id, and SPD-based speed tier so client
 * renderers can lerp the gestalt position from launch toward the homing target.
 */
public record SyncChargedStrikeTravelS2C(
        int playerEntityId,
        int targetEntityId,
        double launchX,
        double launchY,
        double launchZ,
        byte speedTier
) implements CustomPacketPayload {

    public static final Type<SyncChargedStrikeTravelS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "sync_charged_strike_travel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncChargedStrikeTravelS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,    SyncChargedStrikeTravelS2C::playerEntityId,
                    ByteBufCodecs.VAR_INT,    SyncChargedStrikeTravelS2C::targetEntityId,
                    ByteBufCodecs.DOUBLE,     SyncChargedStrikeTravelS2C::launchX,
                    ByteBufCodecs.DOUBLE,     SyncChargedStrikeTravelS2C::launchY,
                    ByteBufCodecs.DOUBLE,     SyncChargedStrikeTravelS2C::launchZ,
                    ByteBufCodecs.BYTE,       SyncChargedStrikeTravelS2C::speedTier,
                    SyncChargedStrikeTravelS2C::new
            );

    @Override
    public Type<SyncChargedStrikeTravelS2C> type() {
        return TYPE;
    }
}
