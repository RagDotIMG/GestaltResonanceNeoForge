package net.ragdot.gestaltresonance.client;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side registry mapping gestalt IDs to their HUD sprites.
 *
 * Register entries during client setup via {@link #registerHudIcon} and
 * {@link #registerPowerIcons}. Adding a new gestalt only requires calls here
 * — no edits to rendering classes.
 *
 * Power icon array is indexed [row][col] matching the 3×3 management screen grid
 * (row 0 = POWER_1, col 0 = B/NONE, col 1 = S/SNEAK, col 2 = G/GUARD).
 * Null entries gracefully fall back to the level-label cell.
 */
public final class GestaltHudAssets {

    private static final Map<ResourceLocation, ResourceLocation> HUD_ICONS = new HashMap<>();
    private static final Map<ResourceLocation, ResourceLocation[][]> POWER_ICONS = new HashMap<>();

    public static void registerHudIcon(ResourceLocation gestaltId, ResourceLocation sprite) {
        HUD_ICONS.put(gestaltId, sprite);
    }

    public static void registerPowerIcons(ResourceLocation gestaltId, ResourceLocation[][] icons) {
        POWER_ICONS.put(gestaltId, icons);
    }

    /** Returns null if no icon is registered for this gestalt. */
    public static ResourceLocation getHudIcon(ResourceLocation gestaltId) {
        return HUD_ICONS.get(gestaltId);
    }

    /** Returns null if no icon is registered for this cell (falls back to level label). */
    public static ResourceLocation getPowerIcon(ResourceLocation gestaltId, int row, int col) {
        ResourceLocation[][] icons = POWER_ICONS.get(gestaltId);
        if (icons == null || row >= icons.length || col >= icons[row].length) return null;
        return icons[row][col];
    }

    private GestaltHudAssets() {}
}
