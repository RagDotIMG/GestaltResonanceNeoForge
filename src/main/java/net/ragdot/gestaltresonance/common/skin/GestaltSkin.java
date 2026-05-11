package net.ragdot.gestaltresonance.common.skin;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * One unlockable visual variant for a gestalt.
 *
 * @param id          Unique skin identifier, scoped under the gestalt's namespace
 *                    (e.g. {@code gestaltresonance:amen_break/biome}).
 * @param displayName Human-readable name shown in the management screen.
 * @param texture     The texture used when this skin is selected.
 * @param condition   {@code null} for default skins (always available).
 */
public record GestaltSkin(
        ResourceLocation id,
        Component displayName,
        ResourceLocation texture,
        @Nullable SkinUnlockCondition condition
) {

    /** Default skins (those with a null condition) are always available regardless of unlock state. */
    public boolean isDefault() {
        return condition == null;
    }
}
