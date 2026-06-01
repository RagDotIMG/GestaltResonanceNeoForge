package net.ragdot.gestaltresonance.common.block;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltBlockEntities;
import net.ragdot.gestaltresonance.common.GestaltCosts;
import net.ragdot.gestaltresonance.common.GestaltDamageTypes;
import net.ragdot.gestaltresonance.common.GestaltExplosionUtil;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.PopSproutTracker;

public class PopSproutBlockEntity extends BlockEntity {

    @Nullable
    private UUID ownerUuid;

    public PopSproutBlockEntity(BlockPos pos, BlockState state) {
        super(GestaltBlockEntities.POP_SPROUT.get(), pos, state);
    }

    public void setOwner(UUID uuid) {
        this.ownerUuid = uuid;
        setChanged();
    }

    @Nullable
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    /**
     * Removes the block first (prevents double-trigger), then detonates a scaled explosion.
     * Called from PopSproutBlock.entityInside and from PopPodEntity when hitting an existing sprout.
     */
    public void detonate(ServerLevel level, BlockPos pos) {
        if (ownerUuid != null) {
            PopSproutTracker.get(level.getServer()).removeSprout(ownerUuid, pos);
        }
        level.removeBlock(pos, false);

        Player ownerPlayer = ownerUuid != null ? level.getServer().getPlayerList().getPlayer(ownerUuid) : null;
        int gestaltLevel = 1;
        if (ownerPlayer != null) {
            PlayerGestaltState s = ownerPlayer.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
            gestaltLevel = s.getGestaltLevel();
        }

        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        float radius = GestaltExplosionUtil.scaledRadius(GestaltCosts.POWER_1B_EXPLOSION_BASE_RADIUS, gestaltLevel);
        float damage = GestaltExplosionUtil.scaledDamage(GestaltCosts.POWER_1B_EXPLOSION_BASE_DAMAGE, gestaltLevel);
        DamageSource src = ownerPlayer != null
                ? GestaltDamageTypes.gestalt(level, ownerPlayer)
                : level.damageSources().source(GestaltDamageTypes.GESTALT, null, null);

        GestaltExplosionUtil.detonate(level, center, radius, damage, src, null);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (ownerUuid != null) {
            tag.putUUID("owner", ownerUuid);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.hasUUID("owner")) {
            ownerUuid = tag.getUUID("owner");
        }
    }
}
