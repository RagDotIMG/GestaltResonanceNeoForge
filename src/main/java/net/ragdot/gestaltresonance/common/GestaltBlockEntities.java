package net.ragdot.gestaltresonance.common;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.block.PopDripBlockEntity;
import net.ragdot.gestaltresonance.common.block.PopSproutBlockEntity;

public final class GestaltBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, GestaltResonance.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PopDripBlockEntity>> POP_DRIP =
            BLOCK_ENTITY_TYPES.register("pop_drip", () ->
                    BlockEntityType.Builder
                            .of(PopDripBlockEntity::new, GestaltBlocks.POP_DRIP.get())
                            .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PopSproutBlockEntity>> POP_SPROUT =
            BLOCK_ENTITY_TYPES.register("pop_sprout", () ->
                    BlockEntityType.Builder
                            .of(PopSproutBlockEntity::new, GestaltBlocks.POP_SPROUT.get())
                            .build(null));

    private GestaltBlockEntities() {}
}
