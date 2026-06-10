package net.ragdot.gestaltresonance.common.loot;

import com.mojang.serialization.MapCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.ragdot.gestaltresonance.GestaltResonance;

public class GestaltLootModifiers {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, GestaltResonance.MODID);

    public static void register(IEventBus bus) {
        SERIALIZERS.register("helper_book_in_chests", () -> HelperBookLootModifier.CODEC);
        SERIALIZERS.register("gestalt_documents", () -> GestaltDocumentLootModifier.CODEC);
        SERIALIZERS.register(bus);
    }
}
