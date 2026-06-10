package net.ragdot.gestaltresonance.common.power.spillways;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltIds;
import net.ragdot.gestaltresonance.common.GestaltSounds;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;
import net.ragdot.gestaltresonance.common.power.GestaltPowerKey;
import net.ragdot.gestaltresonance.common.power.GestaltPowerModifier;
import net.ragdot.gestaltresonance.common.power.GestaltPowerRegistry;
import net.ragdot.gestaltresonance.common.power.GestaltPowerSlot;

public final class SpillwaysPower2B {

    public static final GestaltPowerKey KEY = new GestaltPowerKey(
            GestaltIds.SPILLWAYS, GestaltPowerSlot.POWER_2, GestaltPowerModifier.NONE);

    public static void register() {
        GestaltPowerRegistry.register(KEY, SpillwaysPower2B::activate);
    }

    private static void playFail(ServerPlayer player) {
        player.playNotifySound(GestaltSounds.GESTALT_FAIL.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void activate(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        if (!state.isSummoned() || !state.isAwakened()) { playFail(player); return; }
        if (state.getGestaltLevel() < GestaltCosts.POWER_LEVELS[1][0]) { playFail(player); return; }
        if (player.getFoodData().getFoodLevel() <= GestaltCosts.CRASH_HUNGER_THRESHOLD) { playFail(player); return; }

        long currentTick = player.getServer().getTickCount();

        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(GestaltCosts.SPILLWAYS_LACHRYMA_RANGE));

        BlockHitResult hit = player.level().clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));

        if (hit.getType() == HitResult.Type.MISS) { playFail(player); return; }

        BlockPos hitPos = hit.getBlockPos();
        if (!player.level().mayInteract(player, hitPos)) { playFail(player); return; }

        BlockState hitState = player.level().getBlockState(hitPos);
        FluidState fluidAtHit = player.level().getFluidState(hitPos);

        // TAKE: free — no XP cost, no cooldown
        if (fluidAtHit.getType() == Fluids.WATER && fluidAtHit.isSource()) {
            if (hitState.getBlock() == Blocks.WATER) {
                player.level().setBlock(hitPos, Blocks.AIR.defaultBlockState(), 3);
            } else if (hitState.hasProperty(BlockStateProperties.WATERLOGGED)
                    && hitState.getValue(BlockStateProperties.WATERLOGGED)) {
                player.level().setBlock(hitPos, hitState.setValue(BlockStateProperties.WATERLOGGED, false), 3);
            } else {
                player.level().setBlock(hitPos, Blocks.AIR.defaultBlockState(), 3);
            }
            player.level().playSound(null, hitPos.getX() + 0.5, hitPos.getY() + 0.5, hitPos.getZ() + 0.5,
                    SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
            GestaltResonance.LOGGER.debug("SpillwaysPower2B {}: TAKE at {}", player.getName().getString(), hitPos);
            return;
        }

        // PLACE: costs XP and sets cooldown
        if (state.hasPowerCooldown(KEY.slot(), KEY.modifier(), currentTick)) { playFail(player); return; }
        if (state.getTotalGestaltXp() < GestaltCosts.SPILLWAYS_LACHRYMA_XP_COST) { playFail(player); return; }

        Direction face = hit.getDirection();
        BlockPos placePos = hitPos.relative(face);

        if (!player.level().isInWorldBounds(placePos)) { playFail(player); return; }
        if (!player.level().mayInteract(player, placePos)) { playFail(player); return; }

        // Don't overwrite an existing water source
        FluidState fluidAtPlace = player.level().getFluidState(placePos);
        if (fluidAtPlace.getType() == Fluids.WATER && fluidAtPlace.isSource()) { playFail(player); return; }

        // Nether: evaporate instead of placing
        if (player.level().dimensionType().ultraWarm()) {
            deductAndSync(player, state, currentTick);
            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        placePos.getX() + 0.5, placePos.getY() + 0.5, placePos.getZ() + 0.5,
                        8, 0.3, 0.3, 0.3, 0.05);
            }
            player.level().playSound(null, placePos.getX() + 0.5, placePos.getY() + 0.5, placePos.getZ() + 0.5,
                    SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 1.0f);
            GestaltResonance.LOGGER.debug("SpillwaysPower2B {}: PLACE evaporated at {}", player.getName().getString(), placePos);
            return;
        }

        BlockState placeState = player.level().getBlockState(placePos);
        boolean placed = false;

        if (placeState.isAir() || placeState.getBlock() == Blocks.WATER) {
            player.level().setBlock(placePos, Blocks.WATER.defaultBlockState(), 3);
            placed = true;
        } else if (placeState.getBlock() instanceof LiquidBlockContainer container
                && container.canPlaceLiquid(player, player.level(), placePos, placeState, Fluids.WATER)) {
            container.placeLiquid(player.level(), placePos, placeState, Fluids.WATER.defaultFluidState());
            placed = true;
        }

        if (!placed) { playFail(player); return; }

        deductAndSync(player, state, currentTick);
        player.level().playSound(null, placePos.getX() + 0.5, placePos.getY() + 0.5, placePos.getZ() + 0.5,
                SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        GestaltResonance.LOGGER.debug("SpillwaysPower2B {}: PLACE at {}", player.getName().getString(), placePos);
    }

    private static void deductAndSync(ServerPlayer player, PlayerGestaltState state, long currentTick) {
        state.spendGestaltXp(GestaltCosts.SPILLWAYS_LACHRYMA_XP_COST);
        state.setPowerCooldown(KEY.slot(), KEY.modifier(), currentTick + GestaltCosts.SPILLWAYS_LACHRYMA_COOLDOWN_TICKS);
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        GestaltNetworking.syncGestaltXpToPlayer(player);
        GestaltNetworking.syncCooldownToPlayer(player, GestaltCosts.SPILLWAYS_LACHRYMA_COOLDOWN_TICKS);
        GestaltNetworking.syncPowerCooldown(player, KEY.slot().ordinal() * 3 + KEY.modifier().ordinal(), GestaltCosts.SPILLWAYS_LACHRYMA_COOLDOWN_TICKS);
    }

    private SpillwaysPower2B() {}
}
