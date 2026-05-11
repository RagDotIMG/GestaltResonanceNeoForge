package net.ragdot.gestaltresonance.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.skin.GestaltSkin;
import net.ragdot.gestaltresonance.common.skin.GestaltSkinRegistry;

/**
 * Client-only landing point for {@code SkinUnlockedToastS2C}. Displays a system toast
 * naming the freshly unlocked skin.
 */
public final class SkinUnlockToastQueue {

    private SkinUnlockToastQueue() {}

    public static void queue(ResourceLocation skinId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerGestaltState state = mc.player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        GestaltSkin skin = GestaltSkinRegistry.getSkin(state.getGestaltId(), skinId);
        Component name = (skin != null) ? skin.displayName() : Component.literal(skinId.toString());

        SystemToast toast = new SystemToast(
                SystemToast.SystemToastId.NARRATOR_TOGGLE,
                Component.literal("New Gestalt skin unlocked"),
                name);
        mc.getToasts().addToast(toast);
    }
}
