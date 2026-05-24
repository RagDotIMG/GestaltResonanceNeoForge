package net.ragdot.gestaltresonance.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.ragdot.gestaltresonance.common.GestaltBlockEntities;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltDelayedPlacer;
import net.ragdot.gestaltresonance.common.GestaltEntities;
import net.ragdot.gestaltresonance.common.entity.DripDropEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Block entity for the END block of a PopDrip vine. Scans every
 * {@link GestaltCosts#POPDRIP_SCAN_INTERVAL} ticks for hostile mobs and drops a
 * {@link DripDropEntity}. After the drop limit is exhausted the entire vine is
 * scheduled for bottom-up removal.
 */
public class PopDripBlockEntity extends BlockEntity {

    @Nullable
    private UUID ownerUuid;
    private int gestaltLevel   = 1;
    private int remainingDrops = GestaltCosts.POPDRIP_BASE_DROPS;
    private int scanTimer      = 0;

    public PopDripBlockEntity(BlockPos pos, BlockState state) {
        super(GestaltBlockEntities.POP_DRIP.get(), pos, state);
    }

    /**
     * Called by {@link GestaltDelayedPlacer} after the END block is placed.
     */
    public void init(UUID ownerUuid, int gestaltLevel) {
        this.ownerUuid = ownerUuid;
        this.gestaltLevel = gestaltLevel;
        this.remainingDrops = GestaltCosts.popdripMaxDrops(gestaltLevel);
        setChanged();
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public static void tick(Level level, BlockPos pos, BlockState state, PopDripBlockEntity be) {
        if (level.isClientSide) return;

        be.scanTimer++;
        if (be.scanTimer < GestaltCosts.POPDRIP_SCAN_INTERVAL) return;
        be.scanTimer = 0;

        if (be.remainingDrops <= 0) return;

        // Walk the center column downward until hitting a solid block, so the scan
        // doesn't pass through floors and detect mobs in unrelated rooms below.
        int scanBottom = level.getMinBuildHeight();
        for (int y = pos.getY() - 1; y > level.getMinBuildHeight(); y--) {
            BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());
            if (!level.getBlockState(checkPos).getCollisionShape(level, checkPos, CollisionContext.empty()).isEmpty()) {
                scanBottom = y + 1;
                break;
            }
        }

        // 2×2 footprint directly beneath the end block, down to nearest solid floor
        AABB scanBox = new AABB(
                pos.getX() - 0.5, scanBottom, pos.getZ() - 0.5,
                pos.getX() + 1.5, pos.getY(),  pos.getZ() + 1.5);
        List<LivingEntity> hostiles = level.getEntitiesOfClass(LivingEntity.class, scanBox,
                e -> e instanceof Enemy && !(e instanceof Player) && e.isAlive());
        if (hostiles.isEmpty()) return;

        DripDropEntity drop = new DripDropEntity(GestaltEntities.DRIP_DROP.get(), level);
        drop.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        drop.setDeltaMovement(0, -0.1, 0);
        drop.setOwnerData(be.ownerUuid, be.gestaltLevel);
        level.addFreshEntity(drop);

        be.remainingDrops--;
        be.setChanged();

        if (be.remainingDrops <= 0) {
            scheduleChainRemoval((ServerLevel) level, pos);
        }
    }

    // ── Chain removal ─────────────────────────────────────────────────────────

    boolean hasRemainingDrops() {
        return remainingDrops > 0;
    }

    static void scheduleChainRemoval(ServerLevel level, BlockPos endPos) {
        List<BlockPos> chain = new ArrayList<>();
        BlockPos scan = endPos;
        while (level.getBlockState(scan).getBlock() instanceof PopDripBlock) {
            chain.add(scan);
            scan = scan.above();
        }
        for (int i = 0; i < chain.size(); i++) {
            GestaltDelayedPlacer.scheduleRemoval(level, chain.get(i), (long) i * 10);
        }
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (ownerUuid != null) tag.putUUID("owner", ownerUuid);
        tag.putInt("gestaltLevel", gestaltLevel);
        tag.putInt("remainingDrops", remainingDrops);
        tag.putInt("scanTimer", scanTimer);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        ownerUuid = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        gestaltLevel = tag.getInt("gestaltLevel");
        remainingDrops = tag.contains("remainingDrops") ? tag.getInt("remainingDrops") : GestaltCosts.POPDRIP_BASE_DROPS;
        scanTimer = tag.getInt("scanTimer");
    }
}
