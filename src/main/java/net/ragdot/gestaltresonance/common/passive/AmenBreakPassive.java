package net.ragdot.gestaltresonance.common.passive;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.Cat;
import net.ragdot.gestaltresonance.common.GestaltAction;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltAttackEvents;
import net.ragdot.gestaltresonance.common.GestaltSounds;
import net.ragdot.gestaltresonance.common.GestaltThrowEvents;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;

/**
 * Amen Break passive: makes the player invisible to sculk sensors
 * while the gestalt is summoned. The actual vibration dampening is
 * handled by a mixin on Entity.dampensVibrations().
 *
 * Also force-unsummons Amen Break when a cat enters 4 blocks — cats scare creepers.
 */
public class AmenBreakPassive implements GestaltPassive {

    private static final double CAT_FEAR_RADIUS = 4.0;

    @Override
    public void tick(ServerPlayer player) {
        if (!hasCatNearby(player)) return;

        // Cat entered radius — clean forced unsummon, no crash penalty.
        GestaltAttackEvents.cancelChain(player);
        GestaltThrowEvents.cancelThrow(player);

        // Re-read state after the cancel calls so their changes aren't overwritten.
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        boolean wasGuarding = state.isGuarding();
        state.clearGuard();
        state.clearLedgeGrab();
        state.setSummoned(false);
        state.setAction(GestaltAction.IDLE);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

        player.playNotifySound(GestaltSounds.GESTALT_DISSOLVE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
                GestaltSounds.GESTALT_DISSOLVE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        GestaltNetworking.syncToTracking(player);
        if (wasGuarding) GestaltNetworking.syncGuardToTracking(player, false);
    }

    @Override
    public void onActivate(ServerPlayer player) {
        // No additional activation logic needed.
    }

    @Override
    public void onDeactivate(ServerPlayer player) {
        // No additional deactivation logic needed.
    }

    /** Returns true if at least one Cat entity is within {@value CAT_FEAR_RADIUS} blocks of the player. */
    public static boolean hasCatNearby(ServerPlayer player) {
        return !player.level().getEntitiesOfClass(
                Cat.class,
                player.getBoundingBox().inflate(CAT_FEAR_RADIUS),
                e -> true
        ).isEmpty();
    }
}
