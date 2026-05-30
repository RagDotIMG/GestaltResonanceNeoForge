package net.ragdot.gestaltresonance.common.power.amen_break;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltIds;
import net.ragdot.gestaltresonance.common.GestaltSounds;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.entity.PhaseBlossomEntity;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;
import net.ragdot.gestaltresonance.common.power.GestaltPowerKey;
import net.ragdot.gestaltresonance.common.power.GestaltPowerModifier;
import net.ragdot.gestaltresonance.common.power.GestaltPowerRegistry;
import net.ragdot.gestaltresonance.common.power.GestaltPowerSlot;

import java.util.Optional;

/**
 * Amen Break — Phase Blossom (Power 3B).
 *
 * First activation: spend 30 XP, place a persistent PhaseBlossomEntity on a surface.
 * The entity makes the 3×3×3 block cube behind the surface intangible (no collision) on
 * both server and client via a collision mixin + PhaseBlossomZoneTracker.
 *
 * Second activation while blossom is alive: dismiss it (free), set 40-tick cooldown,
 * re-solidifying the zone. Entities inside the cube when it re-solidifies are trapped.
 */
public final class AmenBreakPower3B {

    public static final GestaltPowerKey KEY = new GestaltPowerKey(
            GestaltIds.AMEN_BREAK, GestaltPowerSlot.POWER_3, GestaltPowerModifier.NONE);

    public static void register() {
        GestaltPowerRegistry.register(KEY, AmenBreakPower3B::activate);
    }

    private static void playFail(ServerPlayer player) {
        player.playNotifySound(GestaltSounds.GESTALT_FAIL.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void activate(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        if (state.isPhaseCourtActive()) { playFail(player); return; }
        if (!state.isSummoned() || !state.isAwakened()) { playFail(player); return; }
        if (state.getGestaltLevel() < GestaltCosts.POWER_LEVELS[2][0]) { playFail(player); return; }

        long currentTick = player.getServer().getTickCount();
        if (state.hasPowerCooldown(KEY.slot(), KEY.modifier(), currentTick)) { playFail(player); return; }

        if (!(player.level() instanceof ServerLevel sl)) return;

        Optional<PhaseBlossomEntity> existing = PhaseBlossomEntity.findBlossomGlobal(player.getServer(), player.getUUID());

        if (existing.isPresent()) {
            // ── Dismiss path (free) ──────────────────────────────────────────
            // setCollapsing broadcasts DATA_COLLAPSING before the entity is removed so
            // both server and client unregister the zone in the same server tick.
            existing.get().setCollapsing(true);

            state.setPowerCooldown(KEY.slot(), KEY.modifier(), currentTick + GestaltCosts.PHASE_BLOSSOM_COOLDOWN_TICKS);
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            GestaltNetworking.syncCooldownToPlayer(player, GestaltCosts.PHASE_BLOSSOM_COOLDOWN_TICKS);

            GestaltResonance.LOGGER.debug("AmenBreak Phase Blossom 3B: dismissed for {}", player.getName().getString());

        } else {
            // ── Spawn path ────────────────────────────────────────────────────
            if (state.getTotalGestaltXp() < GestaltCosts.PHASE_BLOSSOM_XP_COST) { playFail(player); return; }

            HitResult hit = player.pick(GestaltCosts.PHASE_BLOSSOM_PLACE_RANGE, 0.0f, false);
            if (hit.getType() != HitResult.Type.BLOCK) {
                GestaltResonance.LOGGER.debug("AmenBreak Phase Blossom 3B: no block in range for {}", player.getName().getString());
                playFail(player);
                return;
            }

            BlockHitResult blockHit = (BlockHitResult) hit;
            Direction face = blockHit.getDirection();

            double x = blockHit.getBlockPos().getX() + 0.5 + face.getStepX() * 0.55;
            double y = blockHit.getBlockPos().getY() + 0.5 + face.getStepY() * 0.55;
            double z = blockHit.getBlockPos().getZ() + 0.5 + face.getStepZ() * 0.55;

            PhaseBlossomEntity blossom = new PhaseBlossomEntity(
                    player.level(), x, y, z, player.getUUID(), face, blockHit.getBlockPos());
            player.level().addFreshEntity(blossom);

            state.spendGestaltXp(GestaltCosts.PHASE_BLOSSOM_XP_COST);
            player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
            GestaltNetworking.syncGestaltXpToPlayer(player);

            player.level().playSound(null, x, y, z,
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                    1.0f, 0.8f + player.level().random.nextFloat() * 0.4f);

            GestaltResonance.LOGGER.debug("AmenBreak Phase Blossom 3B: placed for {} at ({},{},{}) face={}",
                    player.getName().getString(), x, y, z, face);
        }
    }

    private AmenBreakPower3B() {}
}
