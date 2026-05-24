package net.ragdot.gestaltresonance.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.ragdot.gestaltresonance.common.PhaseBlossomZoneTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase")
public abstract class BlockCollisionMixin {

    @Inject(
        method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void gestaltresonance$phaseBlossomCollision(BlockGetter level, BlockPos pos,
                                                        CollisionContext ctx,
                                                        CallbackInfoReturnable<VoxelShape> cir) {
        if (PhaseBlossomZoneTracker.isPhased(level, pos)) {
            cir.setReturnValue(Shapes.empty());
        }
    }

    @Inject(
        method = "isSuffocating(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void gestaltresonance$phaseBlossomSuffocating(BlockGetter level, BlockPos pos,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (PhaseBlossomZoneTracker.isPhased(level, pos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
        method = "isViewBlocking(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void gestaltresonance$phaseBlossomViewBlocking(BlockGetter level, BlockPos pos,
                                                           CallbackInfoReturnable<Boolean> cir) {
        if (PhaseBlossomZoneTracker.isPhased(level, pos)) {
            cir.setReturnValue(false);
        }
    }
}
