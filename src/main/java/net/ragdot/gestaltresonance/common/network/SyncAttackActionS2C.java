package net.ragdot.gestaltresonance.common.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.GestaltAction;

/**
 * Server-to-client: sync the current attack-chain action for a player.
 * actionId encoding: 0 = IDLE (chain ended), 1 = HIT_1, 2 = HIT_2, 3 = HIT_3.
 */
public record SyncAttackActionS2C(int entityId, byte actionId) implements CustomPacketPayload {

    public static final Type<SyncAttackActionS2C> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "sync_attack_action"));

    public static final StreamCodec<ByteBuf, SyncAttackActionS2C> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncAttackActionS2C::entityId,
                    ByteBufCodecs.BYTE,    SyncAttackActionS2C::actionId,
                    SyncAttackActionS2C::new
            );

    @Override
    public Type<SyncAttackActionS2C> type() {
        return TYPE;
    }

    public GestaltAction toAction() {
        return switch (actionId) {
            case 1 -> GestaltAction.HIT_1;
            case 2 -> GestaltAction.HIT_2;
            case 3 -> GestaltAction.HIT_3;
            default -> GestaltAction.IDLE;
        };
    }

    public static byte fromAction(GestaltAction action) {
        return switch (action) {
            case HIT_1 -> 1;
            case HIT_2 -> 2;
            case HIT_3 -> 3;
            default -> 0;
        };
    }
}
