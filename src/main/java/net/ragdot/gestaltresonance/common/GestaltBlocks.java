package net.ragdot.gestaltresonance.common;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.block.PopDripBlock;
import net.ragdot.gestaltresonance.common.block.PopPadBlock;
import net.ragdot.gestaltresonance.common.block.PopSproutBlock;
import net.ragdot.gestaltresonance.common.block.PopVineBlock;

public final class GestaltBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(GestaltResonance.MODID);

    public static final DeferredBlock<PopSproutBlock> POP_SPROUT =
            BLOCKS.registerBlock("pop_sprout", PopSproutBlock::new,
                    Block.Properties.of()
                            .noCollission()
                            .noLootTable()
                            .instabreak()
                            .noOcclusion()
                            .sound(SoundType.GRASS));

    public static final DeferredBlock<PopPadBlock> POP_PAD =
            BLOCKS.registerBlock("pop_pad", PopPadBlock::new,
                    Block.Properties.of()
                            .noLootTable()
                            .instabreak()
                            .noOcclusion()
                            .sound(SoundType.WET_GRASS));

    public static final DeferredBlock<PopVineBlock> POP_VINE =
            BLOCKS.registerBlock("pop_vine", PopVineBlock::new,
                    Block.Properties.of()
                            .noCollission()
                            .noLootTable()
                            .instabreak()
                            .noOcclusion()
                            .sound(SoundType.VINE));

    public static final DeferredBlock<PopDripBlock> POP_DRIP =
            BLOCKS.registerBlock("pop_drip", PopDripBlock::new,
                    Block.Properties.of()
                            .noCollission()
                            .noLootTable()
                            .instabreak()
                            .noOcclusion()
                            .sound(SoundType.VINE));

    private GestaltBlocks() {}
}
