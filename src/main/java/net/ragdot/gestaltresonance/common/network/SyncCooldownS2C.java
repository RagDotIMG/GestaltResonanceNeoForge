package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Server-to-client (owning player only): notify the client that a chain/strike cooldown
 * just started so the cooldown HUD bar can fill correctly.
 */
public record SyncCooldownS2C(int entityId, int totalTicks) implements CustomPacketPayload {

    public static final Type<SyncCooldownS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "sync_cooldown"));

    public static final StreamCodec<ByteBuf, SyncCooldownS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncCooldownS2C::entityId,
                    ByteBufCodecs.VAR_INT, SyncCooldownS2C::totalTicks,
                    SyncCooldownS2C::new);

    @Override
    public Type<SyncCooldownS2C> type() {
        return TYPE;
    }
}
