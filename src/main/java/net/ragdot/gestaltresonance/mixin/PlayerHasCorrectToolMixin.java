package net.ragdot.gestaltresonance.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltMiningEvents;
import net.ragdot.gestaltresonance.common.GestaltStats;
import net.ragdot.gestaltresonance.common.GestaltStatsRegistry;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes hasCorrectToolForDrops() return true when the gestalt can mine the block.
 *
 * This is needed because some modded blocks override playerDestroy() and call
 * player.hasCorrectToolForDrops() internally as a guard before generating drops.
 * That method reads inventory.getSelected() directly and cannot be satisfied by
 * the getMainHandItem() mixin, so drops silently fail for those blocks.
 * Patching hasCorrectToolForDrops() ensures both vanilla's break flow and modded
 * playerDestroy overrides see the gestalt as having the correct tool.
 */
@Mixin(Player.class)
public class PlayerHasCorrectToolMixin {

    @Inject(method = "hasCorrectToolForDrops", at = @At("RETURN"), cancellable = true)
    private void gestalt$gestaltHasCorrectTool(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        Player self = (Player)(Object)this;
        PlayerGestaltState gestaltState = self.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (!gestaltState.isSummoned()) return;
        GestaltStats stats = GestaltStatsRegistry.getStats(gestaltState.getGestaltId());
        if (stats == null) return;
        int tier = GestaltMiningEvents.strengthToTier(stats.strength());
        if (GestaltMiningEvents.canTierMine(tier, state)) {
            cir.setReturnValue(true);
        }
    }
}
