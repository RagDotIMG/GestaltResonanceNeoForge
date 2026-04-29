package net.ragdot.gestaltresonance.common;

import net.minecraft.world.entity.player.Player;

/**
 * Utility methods for converting between XP levels/progress and total XP points.
 * Minecraft XP formula:
 *   Levels 0-16:  xpForLevel = 2*level + 7
 *   Levels 17-31: xpForLevel = 5*level - 38
 *   Levels 32+:   xpForLevel = 9*level - 158
 *
 * Total XP to reach a level:
 *   0-16:  level^2 + 6*level
 *   17-31: 2.5*level^2 - 40.5*level + 360
 *   32+:   4.5*level^2 - 162.5*level + 2220
 */
public final class XpHelper {

    private XpHelper() {}

    /** Total XP points needed to reach the given level from 0. */
    public static int xpForLevel(int level) {
        if (level <= 0) return 0;
        if (level <= 16) return level * level + 6 * level;
        if (level <= 31) return (int) (2.5 * level * level - 40.5 * level + 360);
        return (int) (4.5 * level * level - 162.5 * level + 2220);
    }

    /** XP points required to go from level to level+1. */
    public static int xpForNextLevel(int level) {
        if (level < 0) return 0;
        if (level <= 15) return 2 * level + 7;
        if (level <= 30) return 5 * level - 38;
        return 9 * level - 158;
    }

    /** Get total XP points the player currently has. */
    public static int getTotalXp(Player player) {
        int total = xpForLevel(player.experienceLevel);
        total += Math.round(player.experienceProgress * xpForNextLevel(player.experienceLevel));
        return total;
    }

    /** Set the player's XP to the given total points value. Never goes negative. */
    public static void setTotalXp(Player player, int totalXp) {
        if (totalXp < 0) totalXp = 0;
        player.experienceLevel = 0;
        player.experienceProgress = 0.0f;
        player.totalExperience = totalXp;

        int remaining = totalXp;
        while (remaining > 0) {
            int needed = xpForNextLevel(player.experienceLevel);
            if (needed == 0) break;
            if (remaining >= needed) {
                remaining -= needed;
                player.experienceLevel++;
            } else {
                player.experienceProgress = (float) remaining / (float) needed;
                remaining = 0;
            }
        }
    }
}
