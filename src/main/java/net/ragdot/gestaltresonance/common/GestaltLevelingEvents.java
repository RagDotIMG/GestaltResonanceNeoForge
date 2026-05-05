package net.ragdot.gestaltresonance.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;

public class GestaltLevelingEvents {

    // ── XP routing: redirect orb XP to gestalt while summoned ──────────────────

    @SubscribeEvent
    public void onPickupXp(PlayerXpEvent.PickupXp event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned()) return;

        // Read the XP value before touching the orb entity
        int xp = event.getOrb().value;

        // Cancel vanilla XP gain and route to gestalt
        event.setCanceled(true);

        // Vanilla only calls orb.discard() in its non-cancelled code path, so we must
        // absorb and remove the orb manually — otherwise it lingers, playerTouch fires
        // again every tick, and the same orb grants XP repeatedly.
        serverPlayer.take(event.getOrb(), 1);
        event.getOrb().discard();

        int levelsGained = state.addGestaltExperience(xp);
        serverPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);

        if (levelsGained > 0) {
            serverPlayer.playNotifySound(GestaltSounds.GESTALT_LEVELUP.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
            net.ragdot.gestaltresonance.common.skin.GestaltSkinUnlockEvents.checkLevelUnlock(serverPlayer);
        }

        GestaltNetworking.syncGestaltXpToPlayer(serverPlayer);
    }

    // ── Death penalty: 5% XP loss on player death ──────────────────────────────

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) return;

        PlayerGestaltState state = serverPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isAwakened()) return;

        int currentXp = state.getGestaltXp();
        int penalty = (int) (currentXp * 0.05f);
        if (penalty <= 0) return;

        state.setGestaltXp(currentXp - penalty);
        serverPlayer.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncGestaltXpToPlayer(serverPlayer);
    }
}
