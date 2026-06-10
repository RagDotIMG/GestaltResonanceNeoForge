package net.ragdot.gestaltresonance.common.power.amen_break;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltEntities;
import net.ragdot.gestaltresonance.common.GestaltIds;
import net.ragdot.gestaltresonance.common.GestaltIllusionEvents;
import net.ragdot.gestaltresonance.common.GestaltSounds;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.entity.SpawnIllusionEntity;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;
import net.ragdot.gestaltresonance.common.power.GestaltPowerKey;
import net.ragdot.gestaltresonance.common.power.GestaltPowerModifier;
import net.ragdot.gestaltresonance.common.power.GestaltPowerRegistry;
import net.ragdot.gestaltresonance.common.power.GestaltPowerSlot;

/**
 * Amen Break — Phase Shifter (Power 2B).
 *
 * First activation: spend 30 XP, spawn a decoy illusion at the player's position.
 * Second activation while illusion is alive: spend 10 XP, teleport to the illusion,
 * receive 5 ticks of ghost-mode invulnerability, despawn the illusion (no explosion).
 * Cooldown (1200 ticks) only applied on expire or teleport, not on spawn.
 */
public final class AmenBreakPower2B {

    public static final GestaltPowerKey KEY = new GestaltPowerKey(
            GestaltIds.AMEN_BREAK, GestaltPowerSlot.POWER_2, GestaltPowerModifier.NONE);

    public static void register() {
        GestaltPowerRegistry.register(KEY, AmenBreakPower2B::activate);
    }

    private static void activate(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        if (state.isTimePhaseActive()) { playFail(player); return; }
        if (state.isPhaseCourtActive()) { playFail(player); return; }
        if (!state.isSummoned() || !state.isAwakened()) { playFail(player); return; }
        if (state.isSoulProjecting()) { playFail(player); return; }
        if (state.getGestaltLevel() < GestaltCosts.POWER_LEVELS[1][0]) { playFail(player); return; }

        long currentTick = player.getServer().getTickCount();
        if (state.hasPowerCooldown(KEY.slot(), KEY.modifier(), currentTick)) { playFail(player); return; }

        SpawnIllusionEntity existing = GestaltIllusionEvents.INSTANCE.getIllusion(player.getUUID());

        if (existing != null) {
            // ── Teleport path ─────────────────────────────────────────────────
            if (state.getTotalGestaltXp() < GestaltCosts.ILLUSION_TELEPORT_COST) {
                playFail(player);
                return;
            }

            player.teleportTo(existing.getX(), existing.getY(), existing.getZ());
            player.setDeltaMovement(0, 0, 0);
            GestaltIllusionEvents.INSTANCE.startMiniGhost(player, GestaltCosts.ILLUSION_TELEPORT_GHOST_TICKS);

            state.spendGestaltXp(GestaltCosts.ILLUSION_TELEPORT_COST);
            state.setPowerCooldown(KEY.slot(), KEY.modifier(),
                    currentTick + GestaltCosts.ILLUSION_COOLDOWN);
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            GestaltNetworking.syncGestaltXpToPlayer(player);
            GestaltNetworking.syncCooldownToPlayer(player, GestaltCosts.ILLUSION_COOLDOWN);
            GestaltNetworking.syncPowerCooldown(player, KEY.slot().ordinal() * 3 + KEY.modifier().ordinal(), GestaltCosts.ILLUSION_COOLDOWN);

            GestaltIllusionEvents.expire(existing, false);

        } else {
            // ── Spawn path ────────────────────────────────────────────────────
            if (state.getTotalGestaltXp() < GestaltCosts.ILLUSION_SPAWN_COST) {
                playFail(player);
                return;
            }

            SpawnIllusionEntity illusion = new SpawnIllusionEntity(
                    GestaltEntities.SPAWN_ILLUSION.get(), player.level());
            illusion.setOwnerData(player.getUUID(), player.getLookAngle());
            net.minecraft.world.phys.HitResult hit = player.pick(3.0, 0f, false);
            double spawnX, spawnY, spawnZ;
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                net.minecraft.world.phys.BlockHitResult blockHit = (net.minecraft.world.phys.BlockHitResult) hit;
                net.minecraft.core.BlockPos spawnPos = blockHit.getBlockPos().relative(blockHit.getDirection());
                spawnX = spawnPos.getX() + 0.5;
                spawnY = spawnPos.getY();
                spawnZ = spawnPos.getZ() + 0.5;
            } else {
                spawnX = player.getX();
                spawnY = player.getY();
                spawnZ = player.getZ();
            }
            illusion.setPos(spawnX, spawnY, spawnZ);
            player.level().addFreshEntity(illusion);
            GestaltIllusionEvents.INSTANCE.registerIllusion(player.getUUID(), illusion);

            state.spendGestaltXp(GestaltCosts.ILLUSION_SPAWN_COST);
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            GestaltNetworking.syncGestaltXpToPlayer(player);
        }
    }

    private static void playFail(ServerPlayer player) {
        player.playNotifySound(GestaltSounds.GESTALT_FAIL.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private AmenBreakPower2B() {}
}
