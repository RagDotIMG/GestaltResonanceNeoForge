package net.ragdot.gestaltresonance.common.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.ragdot.gestaltresonance.GestaltResonance;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class GestaltDocumentLootModifier extends LootModifier {

    private record Entry(Supplier<Item> item, float chance, Set<ResourceLocation> tables) {
        boolean matches(ResourceLocation table) { return tables.contains(table); }
    }

    // --- Add new document entries here ---
    private static final List<Entry> DOCUMENTS = List.of(

        new Entry(
            () -> GestaltResonance.FILE_117_AMEN_BREAK.get(),
            0.05f,
            Set.of(
                ResourceLocation.withDefaultNamespace("archaeology/desert_pyramid"),
                ResourceLocation.withDefaultNamespace("archaeology/desert_well")
            )
        )

    );

    public static final MapCodec<GestaltDocumentLootModifier> CODEC = RecordCodecBuilder.mapCodec(
            inst -> LootModifier.codecStart(inst).apply(inst, GestaltDocumentLootModifier::new));

    public GestaltDocumentLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ResourceLocation table = context.getQueriedLootTableId();
        for (Entry entry : DOCUMENTS) {
            if (entry.matches(table) && context.getRandom().nextFloat() < entry.chance()) {
                generatedLoot.add(new ItemStack(entry.item().get()));
            }
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
