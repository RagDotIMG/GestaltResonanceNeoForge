package net.ragdot.gestaltresonance.common.power.amen_break;

import net.minecraft.core.Direction;
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
import net.ragdot.gestaltresonance.common.entity.PhaseMineEntity;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;
import net.ragdot.gestaltresonance.common.power.GestaltPowerKey;
import net.ragdot.gestaltresonance.common.power.GestaltPowerModifier;
import net.ragdot.gestaltresonance.common.power.GestaltPowerRegistry;
import net.ragdot.gestaltresonance.common.power.GestaltPowerSlot;

public final class AmenBreakPower2S {

    public static final GestaltPowerKey KEY = new GestaltPowerKey(
            GestaltIds.AMEN_BREAK, GestaltPowerSlot.POWER_2, GestaltPowerModifier.SNEAK);

    public static void register() {
        GestaltPowerRegistry.register(KEY, AmenBreakPower2S::activate);
    }

    private static void playFail(ServerPlayer player) {
        player.playNotifySound(GestaltSounds.GESTALT_FAIL.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void activate(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        if (!state.isSummoned() || !state.isAwakened()) { playFail(player); return; }
        if (state.getGestaltLevel() < GestaltCosts.POWER_LEVELS[1][1]) { playFail(player); return; }

        if (state.getTotalGestaltXp() < GestaltCosts.POWER_2S_XP_COST) { playFail(player); return; }

        HitResult hit = player.pick(GestaltCosts.POWER_2S_RANGE, 0.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            GestaltResonance.LOGGER.debug("Power2S {}: no block in range", player.getName().getString());
            playFail(player);
            return;
        }

        BlockHitResult blockHit = (BlockHitResult) hit;
        Direction face = blockHit.getDirection();

        // Place mine on the center of the hit face.
        // The 0.05 epsilon pushes the entity outside the block boundary so its block position
        // resolves to air rather than the opaque surface block (which would zero out lighting).
        double x = blockHit.getBlockPos().getX() + 0.5 + face.getStepX() * 0.55;
        double y = blockHit.getBlockPos().getY() + 0.5 + face.getStepY() * 0.55;
        double z = blockHit.getBlockPos().getZ() + 0.5 + face.getStepZ() * 0.55;

        // Enforce level-based mine limit — discard the oldest mine when at capacity
        int limit = GestaltCosts.phaseMineLimit(state.getGestaltLevel());
        PhaseMineEntity.dismissOldestIfAtLimit(player.level(), player.getUUID(), limit);

        // Deduct costs (no placement cooldown)
        state.spendGestaltXp(GestaltCosts.POWER_2S_XP_COST);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncGestaltXpToPlayer(player);

        // Spawn mine
        PhaseMineEntity mine = new PhaseMineEntity(player.level(), x, y, z, player.getUUID(), face);
        player.level().addFreshEntity(mine);

        player.level().playSound(null, x, y, z, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0f, 0.9f + player.level().random.nextFloat() * 0.2f);

        GestaltResonance.LOGGER.debug("Power2S {}: placed Phase Mine at ({},{},{}) face={}",
                player.getName().getString(), x, y, z, face);
    }
}
