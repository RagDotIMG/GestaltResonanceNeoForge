package net.ragdot.gestaltresonance.common.skin;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;

import javax.annotation.Nullable;

/**
 * Describes how a Gestalt skin is unlocked. Each variant ships its own data.
 * The corresponding event listener in {@link GestaltSkinUnlockEvents} matches
 * the runtime event against the variant and triggers an unlock when applicable.
 */
public sealed interface SkinUnlockCondition {

    /** Killing the given mob type unlocks the skin. */
    record KillMob(EntityType<?> entityType) implements SkinUnlockCondition {}

    /** Standing in the given biome unlocks the skin (server polls every 20 ticks). */
    record VisitBiome(ResourceKey<Biome> biome) implements SkinUnlockCondition {}

    /** Picking up the given item unlocks the skin. */
    record PickUpItem(Item item) implements SkinUnlockCondition {}

    /** Reaching the given gestalt level unlocks the skin. */
    record GestaltLevel(int level) implements SkinUnlockCondition {}

    /** Crashing out the gestalt the given number of times unlocks the skin. */
    record GestaltCrashCount(int count) implements SkinUnlockCondition {}

    /** Right-clicking a vault block with a trial key unlocks the skin. */
    record LootVault() implements SkinUnlockCondition {}

    /** Helper for biome matching by holder; returns true if the holder matches the condition. */
    static boolean biomeMatches(VisitBiome cond, @Nullable Holder<Biome> current) {
        return current != null && current.is(cond.biome());
    }
}
