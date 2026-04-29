package net.ragdot.gestaltresonance.common;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.ragdot.gestaltresonance.GestaltResonance;
import net.ragdot.gestaltresonance.common.item.StoredGestaltData;

import java.util.function.Supplier;

/**
 * Registers custom DataComponentTypes for the mod.
 * Currently: stored_gestalt — attached to Soul Vessel stacks that hold a player's gestalt.
 */
public class GestaltDataComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, GestaltResonance.MODID);

    public static final Supplier<DataComponentType<StoredGestaltData>> STORED_GESTALT =
            DATA_COMPONENTS.register("stored_gestalt", () ->
                    DataComponentType.<StoredGestaltData>builder()
                            .persistent(StoredGestaltData.CODEC)
                            .build()
            );
}
