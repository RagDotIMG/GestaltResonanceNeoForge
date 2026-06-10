package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;

/**
 * Server-to-client: a per-power-slot cooldown just started.
 * index = slot.ordinal() * 3 + modifier.ordinal() (0–8).
 * totalTicks = full cooldown duration in game ticks.
 */
public record SyncPowerCooldownS2C(int index, int totalTicks) implements CustomPacketPayload {

    public static final Type<SyncPowerCooldownS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "sync_power_cooldown"));

    public static final StreamCodec<ByteBuf, SyncPowerCooldownS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncPowerCooldownS2C::index,
                    ByteBufCodecs.VAR_INT, SyncPowerCooldownS2C::totalTicks,
                    SyncPowerCooldownS2C::new);

    @Override
    public Type<SyncPowerCooldownS2C> type() {
        return TYPE;
    }
}
