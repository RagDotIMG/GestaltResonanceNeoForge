package net.ragdot.gestaltresonance.common.power.amen_break;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.sounds.SoundSource;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltSounds;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltIds;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.entity.PrimedBlockEntity;
import net.ragdot.gestaltresonance.common.network.GestaltNetworking;
import net.ragdot.gestaltresonance.common.power.GestaltPowerKey;
import net.ragdot.gestaltresonance.common.power.GestaltPowerModifier;
import net.ragdot.gestaltresonance.common.power.GestaltPowerRegistry;
import net.ragdot.gestaltresonance.common.power.GestaltPowerSlot;

import net.minecraft.world.item.ItemStack;
import java.util.Set;

public final class AmenBreakPower1S {

    public static final GestaltPowerKey KEY = new GestaltPowerKey(
            GestaltIds.AMEN_BREAK, GestaltPowerSlot.POWER_1, GestaltPowerModifier.SNEAK);

    // Primeable blocks defined in code — vanilla BlockTags handle ores; all other eligible
    // blocks are listed explicitly. Excluded: obsidian, ancient debris, terracotta, sand/gravel, wood, organic.
    private static final Set<Block> PRIMEABLE_BLOCKS = Set.of(
        // Base stone
        Blocks.STONE, Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.ANDESITE, Blocks.DIORITE, Blocks.GRANITE,
        Blocks.TUFF, Blocks.CALCITE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE,
        Blocks.SMOOTH_STONE, Blocks.POLISHED_ANDESITE, Blocks.POLISHED_DIORITE,
        Blocks.POLISHED_GRANITE, Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_TUFF,
        // Stone bricks
        Blocks.STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS,
        Blocks.DEEPSLATE_BRICKS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.CHISELED_DEEPSLATE,
        Blocks.DEEPSLATE_TILES, Blocks.CRACKED_DEEPSLATE_TILES,
        // Blackstone
        Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS,
        Blocks.CHISELED_POLISHED_BLACKSTONE,
        // Nether stone
        Blocks.NETHERRACK, Blocks.BASALT, Blocks.SMOOTH_BASALT, Blocks.POLISHED_BASALT,
        // End
        Blocks.END_STONE, Blocks.END_STONE_BRICKS,
        // Sandstone
        Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.CHISELED_SANDSTONE,
        Blocks.RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE, Blocks.CHISELED_RED_SANDSTONE,
        // Prismarine
        Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE,
        // Bricks (clay)
        Blocks.BRICKS,
        // Raw ore blocks + mineral blocks
        Blocks.RAW_IRON_BLOCK, Blocks.RAW_GOLD_BLOCK, Blocks.RAW_COPPER_BLOCK,
        Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK, Blocks.COPPER_BLOCK, Blocks.DIAMOND_BLOCK,
        Blocks.EMERALD_BLOCK, Blocks.LAPIS_BLOCK, Blocks.REDSTONE_BLOCK, Blocks.COAL_BLOCK,
        Blocks.NETHERITE_BLOCK,
        // Concrete
        Blocks.WHITE_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.MAGENTA_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE,
        Blocks.YELLOW_CONCRETE, Blocks.LIME_CONCRETE, Blocks.PINK_CONCRETE, Blocks.GRAY_CONCRETE,
        Blocks.LIGHT_GRAY_CONCRETE, Blocks.CYAN_CONCRETE, Blocks.PURPLE_CONCRETE, Blocks.BLUE_CONCRETE,
        Blocks.BROWN_CONCRETE, Blocks.GREEN_CONCRETE, Blocks.RED_CONCRETE, Blocks.BLACK_CONCRETE,
        // Stairs
        Blocks.STONE_STAIRS, Blocks.COBBLESTONE_STAIRS, Blocks.MOSSY_COBBLESTONE_STAIRS,
        Blocks.STONE_BRICK_STAIRS, Blocks.MOSSY_STONE_BRICK_STAIRS,
        Blocks.ANDESITE_STAIRS, Blocks.DIORITE_STAIRS, Blocks.GRANITE_STAIRS,
        Blocks.POLISHED_ANDESITE_STAIRS, Blocks.POLISHED_DIORITE_STAIRS, Blocks.POLISHED_GRANITE_STAIRS,
        Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DEEPSLATE_TILE_STAIRS,
        Blocks.POLISHED_DEEPSLATE_STAIRS, Blocks.TUFF_STAIRS, Blocks.POLISHED_TUFF_STAIRS, Blocks.TUFF_BRICK_STAIRS,
        Blocks.BLACKSTONE_STAIRS, Blocks.POLISHED_BLACKSTONE_STAIRS, Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS,
        Blocks.SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.RED_SANDSTONE_STAIRS, Blocks.SMOOTH_RED_SANDSTONE_STAIRS,
        Blocks.PRISMARINE_STAIRS, Blocks.PRISMARINE_BRICK_STAIRS, Blocks.DARK_PRISMARINE_STAIRS,
        Blocks.BRICK_STAIRS, Blocks.END_STONE_BRICK_STAIRS,
        // Slabs
        Blocks.STONE_SLAB, Blocks.SMOOTH_STONE_SLAB, Blocks.COBBLESTONE_SLAB,
        Blocks.STONE_BRICK_SLAB, Blocks.MOSSY_STONE_BRICK_SLAB,
        Blocks.ANDESITE_SLAB, Blocks.DIORITE_SLAB, Blocks.GRANITE_SLAB,
        Blocks.POLISHED_ANDESITE_SLAB, Blocks.POLISHED_DIORITE_SLAB, Blocks.POLISHED_GRANITE_SLAB,
        Blocks.COBBLED_DEEPSLATE_SLAB, Blocks.DEEPSLATE_BRICK_SLAB, Blocks.DEEPSLATE_TILE_SLAB,
        Blocks.POLISHED_DEEPSLATE_SLAB, Blocks.TUFF_SLAB, Blocks.POLISHED_TUFF_SLAB, Blocks.TUFF_BRICK_SLAB,
        Blocks.BLACKSTONE_SLAB, Blocks.POLISHED_BLACKSTONE_SLAB, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB,
        Blocks.SANDSTONE_SLAB, Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.CUT_SANDSTONE_SLAB,
        Blocks.RED_SANDSTONE_SLAB, Blocks.SMOOTH_RED_SANDSTONE_SLAB, Blocks.CUT_RED_SANDSTONE_SLAB,
        Blocks.PRISMARINE_SLAB, Blocks.PRISMARINE_BRICK_SLAB, Blocks.DARK_PRISMARINE_SLAB,
        Blocks.BRICK_SLAB, Blocks.END_STONE_BRICK_SLAB,
        // Walls
        Blocks.COBBLESTONE_WALL, Blocks.MOSSY_COBBLESTONE_WALL, Blocks.STONE_BRICK_WALL, Blocks.MOSSY_STONE_BRICK_WALL,
        Blocks.ANDESITE_WALL, Blocks.DIORITE_WALL, Blocks.GRANITE_WALL, Blocks.SANDSTONE_WALL, Blocks.RED_SANDSTONE_WALL,
        Blocks.COBBLED_DEEPSLATE_WALL, Blocks.DEEPSLATE_BRICK_WALL, Blocks.DEEPSLATE_TILE_WALL, Blocks.POLISHED_DEEPSLATE_WALL,
        Blocks.BLACKSTONE_WALL, Blocks.POLISHED_BLACKSTONE_WALL, Blocks.POLISHED_BLACKSTONE_BRICK_WALL,
        Blocks.TUFF_WALL, Blocks.POLISHED_TUFF_WALL, Blocks.TUFF_BRICK_WALL,
        Blocks.BRICK_WALL, Blocks.END_STONE_BRICK_WALL
    );

    public static void register() {
        GestaltPowerRegistry.register(KEY, AmenBreakPower1S::activate);
    }

    private static void playFail(ServerPlayer player) {
        player.playNotifySound(GestaltSounds.GESTALT_FAIL.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static boolean isPrimeable(BlockState state) {
        // Ore tags cover all variants (normal + deepslate, nether variants)
        if (state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES) || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.DIAMOND_ORES)
                || state.is(BlockTags.EMERALD_ORES) || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.REDSTONE_ORES)) return true;
        // Nether ores without a vanilla tag
        Block b = state.getBlock();
        if (b == Blocks.NETHER_QUARTZ_ORE || b == Blocks.NETHER_GOLD_ORE) return true;
        return PRIMEABLE_BLOCKS.contains(b);
    }

    /** Called from Phase Court Break Core 1S — skips the primeable whitelist check. */
    public static void activateBypassWhitelist(ServerPlayer player) {
        activateInternal(player, true);
    }

    public static void activate(ServerPlayer player) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (state.isTimePhaseActive()) {
            AmenBreakPower3S.activateTimeSkip(player);
            return;
        }
        if (state.isPhaseCourtActive()) {
            if (state.isBreakCoreUsed()) {
                playFail(player);
                return;
            }
            activateInternal(player, true);
            return;
        }
        activateInternal(player, false);
    }

    private static void activateInternal(ServerPlayer player, boolean bypassPrimeableCheck) {
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());

        if (!state.isSummoned()) {
            GestaltResonance.LOGGER.debug("AmenBreak Block Breaker 1S{}: not summoned", player.getName().getString());
            playFail(player);
            return;
        }
        if (!player.isCreative() && !state.isAwakened()) {
            GestaltResonance.LOGGER.debug("AmenBreak Block Breaker 1S{}: not awakened", player.getName().getString());
            playFail(player);
            return;
        }
        if (!player.isCreative() && state.getGestaltLevel() < GestaltCosts.POWER_LEVELS[0][1]) {
            GestaltResonance.LOGGER.debug("AmenBreak Block Breaker 1S{}: level too low ({})", player.getName().getString(), state.getGestaltLevel());
            playFail(player);
            return;
        }

        long currentTick = player.getServer().getTickCount();
        if (state.hasPowerCooldown(KEY.slot(), KEY.modifier(), currentTick)) {
            GestaltResonance.LOGGER.debug("AmenBreak Block Breaker 1S{}: on cooldown", player.getName().getString());
            playFail(player);
            return;
        }
        ItemStack heldItem = player.getInventory().getSelected();
        boolean hasCatalyst = !heldItem.isEmpty() && GestaltCosts.POWER_1S_CATALYSTS.contains(heldItem.getItem());
        int xpCost = hasCatalyst ? GestaltCosts.POWER_1S_REDUCED_XP_COST : GestaltCosts.POWER_1S_XP_COST;

        if (!player.isCreative() && state.getTotalGestaltXp() < xpCost) {
            GestaltResonance.LOGGER.debug("AmenBreak Block Breaker 1S{}: insufficient XP (total {} < {})",
                    player.getName().getString(), state.getTotalGestaltXp(), xpCost);
            playFail(player);
            return;
        }

        HitResult hit = player.pick(4.5, 0.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            GestaltResonance.LOGGER.debug("AmenBreak Block Breaker 1S{}: not targeting a block", player.getName().getString());
            playFail(player);
            return;
        }

        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        BlockState blockState = player.level().getBlockState(pos);
        if (!bypassPrimeableCheck && !isPrimeable(blockState)) {
            GestaltResonance.LOGGER.debug("AmenBreak Block Breaker 1S{}: block {} not primeable",
                    player.getName().getString(), blockState.getBlock().getDescriptionId());
            playFail(player);
            return;
        }

        // Deduct costs (may de-level if within-level XP is insufficient)
        if (!player.isCreative()) {
            if (hasCatalyst) heldItem.shrink(1);
            state.spendGestaltXp(xpCost);
        }
        state.setPowerCooldown(KEY.slot(), KEY.modifier(), currentTick + GestaltCosts.POWER_1S_COOLDOWN);
        if (bypassPrimeableCheck && state.isPhaseCourtActive()) {
            state.setBreakCoreUsed(true);
        }
        player.setData(GestaltAttachments.PLAYER_GESTALT_STATE.get(), state);
        if (!player.isCreative()) GestaltNetworking.syncGestaltXpToPlayer(player);
        GestaltNetworking.syncCooldownToPlayer(player, GestaltCosts.POWER_1S_COOLDOWN);
        if (bypassPrimeableCheck) GestaltNetworking.syncPhaseCourtToPlayer(player);

        // Remove block and spawn primed entity
        Level level = player.level();
        level.removeBlock(pos, false);

        PrimedBlockEntity primedBlock = new PrimedBlockEntity(level,
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                player, blockState);
        if (bypassPrimeableCheck) {
            primedBlock.setSuppressFlash(true);
            primedBlock.setFuse((int) (GestaltCosts.POWER_1S_FUSE_TICKS * 1.5f));
        }
        level.addFreshEntity(primedBlock);

        level.playSound(null, primedBlock.getX(), primedBlock.getY(), primedBlock.getZ(),
                net.minecraft.sounds.SoundEvents.TNT_PRIMED, net.minecraft.sounds.SoundSource.BLOCKS,
                1.0F, 1.0F);

        GestaltResonance.LOGGER.debug("AmenBreak Block Breaker 1S{}: primed {} at {} (catalyst={})",
                player.getName().getString(), blockState.getBlock().getDescriptionId(), pos, hasCatalyst);
    }
}
