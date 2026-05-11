package net.ragdot.gestaltresonance.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Handles the ghost player state: makes a player invulnerable and invisible to mob AI.
 *
 * This is a reusable system. Any ability can call {@link #setGhostState(ServerPlayer, boolean)}
 * to toggle ghost state on a player — this class does not know why the player is ghosted.
 */
public class GhostPlayerHandler {

    /**
     * Activate or deactivate ghost state for a player.
     * On activation, any mobs within 64 blocks that are currently targeting this player
     * have their target cleared immediately.
     */
    public static void setGhostState(ServerPlayer player, boolean active) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        state.setGhostState(active);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

        if (active) {
            player.level().getEntitiesOfClass(
                    Mob.class,
                    player.getBoundingBox().inflate(64.0),
                    mob -> mob.getTarget() == player
            ).forEach(mob -> mob.setTarget(null));
        }
    }

    /** Prevent mobs from acquiring a ghost player as a new target. */
    @SubscribeEvent
    public void onChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer player)) return;
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (state.isGhostState()) event.setCanceled(true);
    }

    /** Make a ghost player fully invulnerable to all incoming damage. */
    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (state.isGhostState()) event.setCanceled(true);
    }
}
