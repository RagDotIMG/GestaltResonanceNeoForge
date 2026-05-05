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
 * actionId encoding: 0 = IDLE, 1 = HIT_1, 2 = HIT_2, 3 = HIT_3, 4 = THROW,
 * 5 = CHARGED_STRIKE_WINDUP, 6 = CHARGED_STRIKE_TRAVEL, 7 = POWER_1G_WINDUP.
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
            case 4 -> GestaltAction.THROW;
            case 5 -> GestaltAction.CHARGED_STRIKE_WINDUP;
            case 6 -> GestaltAction.CHARGED_STRIKE_TRAVEL;
            case 7 -> GestaltAction.POWER_1G_WINDUP;
            default -> GestaltAction.IDLE;
        };
    }

    public static byte fromAction(GestaltAction action) {
        return switch (action) {
            case HIT_1 -> 1;
            case HIT_2 -> 2;
            case HIT_3 -> 3;
            case THROW -> 4;
            case CHARGED_STRIKE_WINDUP -> 5;
            case CHARGED_STRIKE_TRAVEL -> 6;
            case POWER_1G_WINDUP -> 7;
            default -> 0;
        };
    }
}
