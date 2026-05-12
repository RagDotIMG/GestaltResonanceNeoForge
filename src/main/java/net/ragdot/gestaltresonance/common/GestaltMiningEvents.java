package net.ragdot.gestaltresonance.common;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public class GestaltMiningEvents {

    /**
     * Computes the virtual tool the gestalt should be "holding" right now, based on
     * the block the player is currently looking at. Returns null if the gestalt isn't
     * summoned, no stats are known, or the player isn't targeting a block in range.
     *
     * Called from PlayerMainHandMixin on every getMainHandItem() lookup, so the tool
     * type updates in real time as the crosshair moves between blocks. This is what
     * Jade and any other tool-aware mod will see.
     */
    public static ItemStack computeVirtualTool(Player player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!state.isSummoned()) return null;
        GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());
        if (stats == null) return null;
        double range = GestaltCosts.mineRangeFor(state);
        HitResult hit = player.pick(range, 0f, false);
        if (!(hit instanceof BlockHitResult bhr) || bhr.getType() == HitResult.Type.MISS) return null;
        BlockState blockState = player.level().getBlockState(bhr.getBlockPos());
        int tier = strengthToTier(stats.strength());
        return virtualToolFor(blockState, tier);
    }

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
     * Server-side: cancels the break only when the gestalt tier is insufficient.
     * When the tier IS sufficient, vanilla's break flow handles everything:
     *   1. Vanilla calls getMainHandItem() BEFORE removing the block, so our
     *      PlayerMainHandMixin raycast succeeds and returns the correct virtual tool.
     *   2. PlayerHasCorrectToolMixin makes hasCorrectToolForDrops() return true.
     *   3. Vanilla calls playerDestroy() with the virtual tool — loot tables run
     *      normally, including any modded conditions, with the right tool context.
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
        if (!canTierMine(tier, event.getState())) {
            event.setCanceled(true);
        }
    }

    private static boolean isLookingAtBlockInRange(Player player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        double range = GestaltCosts.mineRangeFor(state);
        HitResult hit = player.pick(range, 0f, false);
        return hit instanceof BlockHitResult bhr && bhr.getType() != HitResult.Type.MISS;
    }

    private static boolean isWithinRange(Player player, BlockPos pos) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        double range = GestaltCosts.mineRangeFor(state);
        return Vec3.atCenterOf(pos).distanceTo(player.getEyePosition()) <= range;
    }

    /** Strength 1–2 → tier 0 (wood), 3–4 → tier 1 (stone), 5 → tier 2 (iron). */
    public static int strengthToTier(int strength) {
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

    /**
     * Returns true if the gestalt's virtual tool at this tier can mine the block.
     * Delegates to isCorrectToolForDrops so both vanilla and modded blocks are
     * handled without hardcoding any tag lists.
     */
    public static boolean canTierMine(int tier, BlockState state) {
        if (!state.requiresCorrectToolForDrops()) return true;
        return virtualToolFor(state, tier).isCorrectToolForDrops(state);
    }

    /**
     * Builds a virtual tool ItemStack of the right TYPE and TIER for a block.
     * Tool type is determined by vanilla MINEABLE_WITH_* tags, which nearly all
     * modded blocks register themselves under. Defaults to pickaxe, since most
     * blocks requiring correct-tool-for-drops are stone/ore types.
     */
    public static ItemStack virtualToolFor(BlockState state, int tier) {
        boolean shovel = state.is(BlockTags.MINEABLE_WITH_SHOVEL);
        boolean axe    = state.is(BlockTags.MINEABLE_WITH_AXE);
        boolean hoe    = state.is(BlockTags.MINEABLE_WITH_HOE);

        return new ItemStack(switch (tier) {
            case 0  -> shovel ? Items.WOODEN_SHOVEL  : axe ? Items.WOODEN_AXE  : hoe ? Items.WOODEN_HOE  : Items.WOODEN_PICKAXE;
            case 1  -> shovel ? Items.STONE_SHOVEL   : axe ? Items.STONE_AXE   : hoe ? Items.STONE_HOE   : Items.STONE_PICKAXE;
            default -> shovel ? Items.IRON_SHOVEL    : axe ? Items.IRON_AXE    : hoe ? Items.IRON_HOE    : Items.IRON_PICKAXE;
        });
    }
}
