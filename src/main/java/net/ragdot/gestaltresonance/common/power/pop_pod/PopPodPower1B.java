package net.ragdot.gestaltresonance.common.power.pop_pod;

import net.minecraft.server.level.ServerPlayer;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.GestaltAction;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltEntities;
import net.ragdot.gestaltresonance.common.GestaltIds;
import net.ragdot.gestaltresonance.common.GestaltSounds;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.entity.PopPodEntity;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;
import net.ragdot.gestaltresonance.common.power.GestaltPowerKey;
import net.ragdot.gestaltresonance.common.power.GestaltPowerModifier;
import net.ragdot.gestaltresonance.common.power.GestaltPowerRegistry;
import net.ragdot.gestaltresonance.common.power.GestaltPowerSlot;
import net.minecraft.sounds.SoundSource;

/**
 * Amen Break — Power 1B (plain Z, no modifier).
 *
 * Throws a PopPodEntity toward the player's look direction.
 * On impact:
 *   entity hit → PopSprout proximity mine
 *   floor hit  → PopPad bounce pad
 *   wall hit   → PopVine climbable panel (3 blocks)
 *   ceiling hit→ PopDrip hanging vine (4 blocks)
 *
 * Cost: 1 gestalt XP + 1.0 exhaustion. 40-tick cooldown.
 */
public final class PopPodPower1B {

    public static final GestaltPowerKey KEY =
            new GestaltPowerKey(GestaltIds.AMEN_BREAK, GestaltPowerSlot.POWER_1, GestaltPowerModifier.NONE);

    public static void register() {
        GestaltPowerRegistry.register(KEY, PopPodPower1B::activate);
    }

    public static void activate(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        if (!state.isSummoned()) return;
        if (!state.isAwakened()) return;
        if (state.getAction() != GestaltAction.IDLE) return;

        long currentTick = player.getServer().getTickCount();
        if (state.hasPowerCooldown(currentTick)) {
            playFail(player);
            return;
        }
        if (state.getTotalGestaltXp() < GestaltCosts.POWER_1B_XP_COST) {
            playFail(player);
            return;
        }

        state.spendGestaltXp(GestaltCosts.POWER_1B_XP_COST);
        player.causeFoodExhaustion(GestaltCosts.POWER_1B_EXHAUSTION);
        state.setPowerCooldownUntilTick(currentTick + GestaltCosts.POWER_1B_COOLDOWN_TICKS);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

        GestaltNetworking.syncGestaltXpToPlayer(player);
        GestaltNetworking.syncCooldownToPlayer(player, GestaltCosts.POWER_1B_COOLDOWN_TICKS);

        PopPodEntity pod = new PopPodEntity(GestaltEntities.POP_POD.get(), player, player.level());
        pod.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 1.2f, 0.5f);
        player.level().addFreshEntity(pod);

        GestaltResonance.LOGGER.debug("PopPod Power 1B activated by {}", player.getName().getString());
    }

    private static void playFail(ServerPlayer player) {
        player.playNotifySound(GestaltSounds.GESTALT_FAIL.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private PopPodPower1B() {}
}
