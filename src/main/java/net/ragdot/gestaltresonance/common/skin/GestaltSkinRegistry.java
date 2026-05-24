package net.ragdot.gestaltresonance.common.skin;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.ragdot.gestaltresonance.GestaltResonance;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the skin catalog for each gestalt. Each gestalt has up to 10 skins; slots 1–4
 * are defaults (always available) and slots 5–10 are unlockable via {@link SkinUnlockCondition}.
 */
public final class GestaltSkinRegistry {

    private static final Map<ResourceLocation, List<GestaltSkin>> SKINS = new HashMap<>();

    private GestaltSkinRegistry() {}

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "textures/gestalt/" + name + ".png");
    }

    private static ResourceLocation skinId(String gestalt, String slot) {
        return ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, gestalt + "/" + slot);
    }

    static {
        // ── Amen Break ───────────────────────────────────────────────────────
        ResourceLocation amenBreak = ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "amen_break");
        SKINS.put(amenBreak, List.of(
                new GestaltSkin(skinId("amen_break", "default"),
                        Component.literal("Default"), tex("amen_break/default"), null),
                new GestaltSkin(skinId("amen_break", "alt"),
                        Component.literal("Recolor I"), tex("amen_break/alt"), null),
                new GestaltSkin(skinId("amen_break", "alt2"),
                        Component.literal("Recolor II"), tex("amen_break/alt2"), null),
                new GestaltSkin(skinId("amen_break", "alt3"),
                        Component.literal("Recolor III"), tex("amen_break/alt3"), null),
                new GestaltSkin(skinId("amen_break", "biome"),
                        Component.literal("Biome"), tex("amen_break/biome"),
                        new SkinUnlockCondition.VisitBiome(Biomes.SWAMP)),
                new GestaltSkin(skinId("amen_break", "trial"),
                        Component.literal("Trial"), tex("amen_break/trial"),
                        new SkinUnlockCondition.LootVault()),
                new GestaltSkin(skinId("amen_break", "penrose"),
                        Component.literal("Penrose"), tex("amen_break/penrose"),
                        new SkinUnlockCondition.PickUpItem(Items.CHORUS_FLOWER)),
                new GestaltSkin(skinId("amen_break", "warden"),
                        Component.literal("Warden"), tex("amen_break/warden"),
                        new SkinUnlockCondition.KillMob(EntityType.WARDEN)),
                new GestaltSkin(skinId("amen_break", "glitch"),
                        Component.literal("Glitch"), tex("amen_break/glitch"),
                        new SkinUnlockCondition.GestaltCrashCount(50)),
                new GestaltSkin(skinId("amen_break", "mastery"),
                        Component.literal("Mastery"), tex("amen_break/mastery"),
                        new SkinUnlockCondition.GestaltLevel(15))
        ));

        // ── Spillways ─────────────────────────────────────────────────────────
        ResourceLocation spillways = ResourceLocation.fromNamespaceAndPath(GestaltResonance.MODID, "spillways");
        SKINS.put(spillways, List.of(
                new GestaltSkin(skinId("spillways", "default"),
                        Component.literal("Default"), tex("spillways/default"), null)
        ));
    }

    /** All skins for the given gestalt, in display order. Empty if the gestalt has no entry. */
    public static List<GestaltSkin> getSkins(ResourceLocation gestaltId) {
        return SKINS.getOrDefault(gestaltId, List.of());
    }

    /** Lookup a single skin by its id within the given gestalt. */
    @Nullable
    public static GestaltSkin getSkin(ResourceLocation gestaltId, ResourceLocation skinId) {
        for (GestaltSkin s : getSkins(gestaltId)) {
            if (s.id().equals(skinId)) return s;
        }
        return null;
    }

    /** The default (slot 1) skin for a gestalt — always available. May be null if gestalt has no skins defined. */
    @Nullable
    public static GestaltSkin getDefaultSkin(ResourceLocation gestaltId) {
        List<GestaltSkin> skins = getSkins(gestaltId);
        return skins.isEmpty() ? null : skins.get(0);
    }
}
