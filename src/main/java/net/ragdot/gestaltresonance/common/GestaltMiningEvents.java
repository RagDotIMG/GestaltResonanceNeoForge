package net.ragdot.gestaltresonance.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public class GestaltMiningEvents {

    // Eye-to-block-center threshold: a block face 3 blocks away has its center at ~3.5
    private static final double MINE_RANGE_THRESHOLD = 3.5;

    /**
     * Fires on both logical sides (client for animation, server for actual timing).
     * Overrides mining speed to the gestalt's tier value, ignoring the held tool.
     * Sets speed to 0 if the gestalt's tier is insufficient for the block.
     */
    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned()) return;

        if (!isLookingAtBlockInRange(player)) return;

        GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());
        if (stats == null) return;

        int tier = strengthToTier(stats.strength());
        if (!canTierMine(tier, event.getState())) {
            event.setNewSpeed(0f);
            return;
        }

        event.setNewSpeed(tierToSpeed(tier));
    }

    /**
     * Server-side safety net: cancels the break if the gestalt tier is insufficient.
     * Also intercepts drops when the player's held tool wouldn't satisfy
     * requiresCorrectToolForDrops, replacing it with a virtual pickaxe of the
     * correct tier so the loot table produces the right items.
     */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned()) return;

        BlockPos pos = event.getPos();
        if (!isWithinRange(player, pos)) return;

        GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());
        if (stats == null) return;

        int tier = strengthToTier(stats.strength());
        BlockState blockState = event.getState();

        if (!canTierMine(tier, blockState)) {
            event.setCanceled(true);
            return;
        }

        // If the player's held item wouldn't give drops for this block, handle the break
        // manually with a virtual pickaxe of the correct tier so drops are always correct.
        if (blockState.requiresCorrectToolForDrops() && !player.getMainHandItem().isCorrectToolForDrops(blockState)) {
            ServerLevel level = player.serverLevel();
            BlockEntity blockEntity = level.getBlockEntity(pos);
            ItemStack virtualTool = tierPickaxe(tier);

            event.setCanceled(true);
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            level.levelEvent(2001, pos, Block.getId(blockState));
            blockState.getBlock().playerDestroy(level, player, pos, blockState, blockEntity, virtualTool);
        }
        // Otherwise: vanilla flow handles drops correctly with the player's actual tool.
    }

    private static boolean isLookingAtBlockInRange(Player player) {
        HitResult hit = player.pick(MINE_RANGE_THRESHOLD, 0f, false);
        return hit instanceof BlockHitResult bhr && bhr.getType() != HitResult.Type.MISS;
    }

    private static boolean isWithinRange(Player player, BlockPos pos) {
        return Vec3.atCenterOf(pos).distanceTo(player.getEyePosition()) <= MINE_RANGE_THRESHOLD;
    }

    /** Strength 1–2 → tier 0 (wood), 3–4 → tier 1 (stone), 5 → tier 2 (iron). */
    private static int strengthToTier(int strength) {
        if (strength <= 2) return 0;
        if (strength <= 4) return 1;
        return 2;
    }

    private static float tierToSpeed(int tier) {
        return switch (tier) {
            case 0 -> 2.0f;
            case 1 -> 4.0f;
            default -> 6.0f;
        };
    }

    private static boolean canTierMine(int tier, BlockState state) {
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) return false;
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) return tier >= 2;
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) return tier >= 1;
        return true;
    }

    private static ItemStack tierPickaxe(int tier) {
        return new ItemStack(switch (tier) {
            case 0 -> Items.WOODEN_PICKAXE;
            case 1 -> Items.STONE_PICKAXE;
            default -> Items.IRON_PICKAXE;
        });
    }
}
