package net.ragdot.gestaltresonance.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.ragdot.gestaltresonance.client.GestaltFirstPersonRenderer;
import net.ragdot.gestaltresonance.client.GestaltKeybinds;
import net.ragdot.gestaltresonance.client.gestalt.GestaltModel;
import net.ragdot.gestaltresonance.common.GestaltAttachments;
import net.ragdot.gestaltresonance.common.GestaltStats;
import net.ragdot.gestaltresonance.common.GestaltStatsRegistry;
import net.ragdot.gestaltresonance.common.PlayerGestaltState;
import net.ragdot.gestaltresonance.common.network.SelectGestaltSkinC2S;
import net.ragdot.gestaltresonance.common.skin.GestaltSkin;
import net.ragdot.gestaltresonance.common.skin.GestaltSkinRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen opened by Sneak+G tap. Shows a 3D gestalt preview, skin selector, stats and a
 * 3x3 power grid keyed off the player's current gestalt level.
 *
 * Locked skins are hidden in the selector — only unlocked + default skins cycle.
 * On close the currently displayed skin is committed via {@link SelectGestaltSkinC2S}.
 */
public class GestaltManagementScreen extends Screen {

    private static final int PANEL_WIDTH = 256;
    private static final int PANEL_HEIGHT = 200;

    private static final int[][] POWER_LEVELS = {
            {0, 2, 3},
            {5, 7, 8},
            {10, 12, 13}
    };
    private static final String[] POWER_COLS = {"B", "S", "G"};

    private final PlayerGestaltState state;
    private final List<GestaltSkin> availableSkins;
    private int currentIndex;
    private float displayYaw = 0f;

    private GestaltManagementScreen(PlayerGestaltState state) {
        super(Component.literal("Gestalt"));
        this.state = state;
        this.availableSkins = collectAvailable(state);
        this.currentIndex = indexOfSelected(state, availableSkins);
    }

    /**
     * Open the screen if the local player has a gestalt assigned; no-op otherwise.
     * We can't use {@code state.isAwakened()} client-side — that flag isn't synced —
     * so we use the gestalt id as the proxy: a non-NONE id means the server set one.
     */
    public static void openIfEligible() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        PlayerGestaltState state = player.getData(GestaltAttachments.PLAYER_GESTALT_STATE.get());
        if (state.getGestaltId().equals(PlayerGestaltState.NONE)) return;
        if (GestaltSkinRegistry.getSkins(state.getGestaltId()).isEmpty()) return;
        mc.setScreen(new GestaltManagementScreen(state));
    }

    private static List<GestaltSkin> collectAvailable(PlayerGestaltState state) {
        List<GestaltSkin> out = new ArrayList<>();
        for (GestaltSkin skin : GestaltSkinRegistry.getSkins(state.getGestaltId())) {
            if (skin.isDefault() || state.isSkinUnlocked(skin.id())) {
                out.add(skin);
            }
        }
        return out;
    }

    private static int indexOfSelected(PlayerGestaltState state, List<GestaltSkin> skins) {
        ResourceLocation selected = state.getSelectedSkin();
        if (selected != null && !selected.equals(PlayerGestaltState.NONE)) {
            for (int i = 0; i < skins.size(); i++) {
                if (skins.get(i).id().equals(selected)) return i;
            }
        }
        return 0;
    }

    @Override
    protected void init() {
        super.init();
        int leftX = (this.width - PANEL_WIDTH) / 2;
        int topY = (this.height - PANEL_HEIGHT) / 2;
        int selectorY = topY + PANEL_HEIGHT - 28;

        // Skin selector buttons live in the left half.
        int leftHalfCenterX = leftX + PANEL_WIDTH / 4;
        addRenderableWidget(Button.builder(Component.literal("<"),
                        b -> cycle(-1))
                .bounds(leftHalfCenterX - 50, selectorY, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),
                        b -> cycle(1))
                .bounds(leftHalfCenterX + 30, selectorY, 20, 20).build());
    }

    private void cycle(int delta) {
        if (availableSkins.isEmpty()) return;
        int n = availableSkins.size();
        currentIndex = ((currentIndex + delta) % n + n) % n;
    }

    @Override
    public void onClose() {
        super.onClose();
        if (!availableSkins.isEmpty()) {
            ResourceLocation chosen = availableSkins.get(currentIndex).id();
            if (!chosen.equals(state.getSelectedSkin())) {
                PacketDistributor.sendToServer(new SelectGestaltSkinC2S(chosen));
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        displayYaw += 1.5f;  // slow auto-rotate
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
        super.render(g, mouseX, mouseY, partialTick);

        int leftX = (this.width - PANEL_WIDTH) / 2;
        int topY = (this.height - PANEL_HEIGHT) / 2;

        // ── Title bar ──
        g.drawString(this.font, this.title, leftX + 8, topY + 6, 0xFFFFFFFF);

        // ── 3D preview ──
        int previewCenterX = leftX + PANEL_WIDTH / 4;
        int previewCenterY = topY + PANEL_HEIGHT / 2 - 20;
        renderGestaltPreview(g, previewCenterX, previewCenterY, partialTick);

        // ── Skin name label ──
        if (!availableSkins.isEmpty()) {
            GestaltSkin current = availableSkins.get(currentIndex);
            int nameX = leftX + PANEL_WIDTH / 4;
            int nameY = topY + PANEL_HEIGHT - 22;
            int nameWidth = this.font.width(current.displayName());
            g.drawString(this.font, current.displayName(), nameX - nameWidth / 2, nameY, 0xFFFFFFFF);
        } else {
            g.drawCenteredString(this.font, Component.literal("No skins available"),
                    leftX + PANEL_WIDTH / 4, topY + PANEL_HEIGHT - 22, 0xFFAAAAAA);
        }

        // ── Stats ──
        renderStats(g, leftX + PANEL_WIDTH / 2 + 10, topY + 24);

        // ── Power grid ──
        renderPowerGrid(g, leftX + PANEL_WIDTH / 2 + 10, topY + 110);
    }

    private void renderStats(GuiGraphics g, int x, int y) {
        GestaltStats stats = GestaltStatsRegistry.getStats(state.getGestaltId());
        if (stats == null) {
            g.drawString(this.font, "No attributes", x, y, 0xFFFF8888);
            return;
        }
        g.drawString(this.font, "Attributes", x, y, 0xFFFFFF55);
        int lh = 11;
        int color = 0xFFEEEEEE;
        g.drawString(this.font, "STR: " + stats.strength(),   x, y + lh,     color);
        g.drawString(this.font, "SPD: " + stats.speed(),      x, y + lh * 2, color);
        g.drawString(this.font, "DUR: " + stats.durability(), x, y + lh * 3, color);
        g.drawString(this.font, "RNG: " + stats.range(),      x, y + lh * 4, color);
        g.drawString(this.font, "RES: " + stats.resonance(),  x, y + lh * 5, color);
    }

    private void renderPowerGrid(GuiGraphics g, int x, int y) {
        int cell = 24;
        int gap = 4;
        int origin = y;

        // Column headers
        for (int c = 0; c < 3; c++) {
            int colX = x + 18 + c * (cell + gap);
            g.drawString(this.font, POWER_COLS[c], colX + cell / 2 - 3, origin - 11, 0xFFFFFF55);
        }
        // Row headers + cells. The row label is the live key bound to POWER_1/2/3 — pulled via
        // KeyMapping#getTranslatedKeyMessage so it stays in sync with the player's controls config.
        net.minecraft.client.KeyMapping[] powerBindings = {
                GestaltKeybinds.POWER_1.get(),
                GestaltKeybinds.POWER_2.get(),
                GestaltKeybinds.POWER_3.get()
        };
        for (int r = 0; r < 3; r++) {
            int rowY = origin + r * (cell + gap);
            Component rowLabel = powerBindings[r].getTranslatedKeyMessage();
            g.drawString(this.font, rowLabel, x, rowY + cell / 2 - 4, 0xFFFFFF55);

            for (int c = 0; c < 3; c++) {
                int cx = x + 18 + c * (cell + gap);
                int required = POWER_LEVELS[r][c];
                boolean unlocked = state.getGestaltLevel() >= required;
                int fill = unlocked ? 0xFF555555 : 0xFF1A1A1A;
                int border = unlocked ? 0xFFAAAAAA : 0xFF444444;
                int textColor = unlocked ? 0xFFFFFFFF : 0xFF666666;

                g.fill(cx, rowY, cx + cell, rowY + cell, fill);
                g.fill(cx, rowY, cx + cell, rowY + 1, border);
                g.fill(cx, rowY + cell - 1, cx + cell, rowY + cell, border);
                g.fill(cx, rowY, cx + 1, rowY + cell, border);
                g.fill(cx + cell - 1, rowY, cx + cell, rowY + cell, border);

                // Required-level label centered in cell — no per-cell tier/category text;
                // the row + column headers already disambiguate.
                String reqLabel = "L" + required;
                int rlw = this.font.width(reqLabel);
                g.drawString(this.font, reqLabel, cx + (cell - rlw) / 2, rowY + cell / 2 - 4, textColor);
            }
        }
    }

    private void renderGestaltPreview(GuiGraphics g, int cx, int cy, float partialTick) {
        GestaltModel model = GestaltFirstPersonRenderer.getModel();
        Minecraft mc = Minecraft.getInstance();
        if (model == null || mc.player == null) return;

        // Texture for the currently *displayed* (not yet committed) skin.
        ResourceLocation texture;
        if (!availableSkins.isEmpty()) {
            texture = availableSkins.get(currentIndex).texture();
        } else {
            GestaltSkin def = GestaltSkinRegistry.getDefaultSkin(state.getGestaltId());
            if (def == null) return;
            texture = def.texture();
        }

        PoseStack pose = g.pose();
        pose.pushPose();
        // Anchor the pose origin near the visual "feet" of the preview (below center) —
        // the model parts extend upward from there because the AmenBreak rig has its head
        // at negative model-Y and feet at positive model-Y, which renders right-side up
        // when no Y flip is applied (GUI screen-Y points down, matching the model's natural
        // direction). Adding scale(-1,-1,1) here, like a world-space entity renderer would,
        // would actually flip the model upside down — that was the previous bug.
        pose.translate(cx, cy + 30, 100);
        pose.scale(40f, 40f, 40f);
        // 180° around Y so the gestalt's front faces the camera; spin slowly on top of that.
        pose.mulPose(Axis.YP.rotationDegrees(180f + displayYaw + partialTick * 1.5f));

        com.mojang.blaze3d.platform.Lighting.setupForEntityInInventory();
        model.setupAnim(mc.player, 0, 0, mc.player.tickCount + partialTick, 0, 0);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(texture));
        int light = LightTexture.pack(15, 15);
        model.renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        buffers.endBatch();

        com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
        pose.popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Suppress unused-import warning for ItemRenderer (kept for future placeholder icons).
    @SuppressWarnings("unused")
    private static final Class<?> _itemRendererRef = ItemRenderer.class;
}
