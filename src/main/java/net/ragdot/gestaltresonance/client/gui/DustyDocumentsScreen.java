package net.ragdot.gestaltresonance.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class DustyDocumentsScreen extends Screen {

    // Swap these ResourceLocations for real textures when ready.
    // When textures exist, replace the graphics.fill() calls in renderBackground() with graphics.blit().
    public static ResourceLocation WRITTEN_PAGE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("gestaltresonance", "textures/gui/dusty_docs_written.png");
    public static ResourceLocation RIPPED_PAGE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("gestaltresonance", "textures/gui/dusty_docs_ripped.png");

    private static final int PAGE_WIDTH    = 192;
    private static final int PAGE_HEIGHT   = 272; // DIN A4 ratio: 1 : √2 ≈ 192 × 1.4142
    private static final int TEXT_X_OFFSET = 16;
    private static final int TEXT_Y_OFFSET = 30;
    private static final int TEXT_WIDTH    = PAGE_WIDTH - 2 * TEXT_X_OFFSET; // 160
    private static final int LINE_HEIGHT   = 9;
    private static final int PAGE_COUNT_BOTTOM_MARGIN = 10;
    private static final int MAX_LINES     = (PAGE_HEIGHT - TEXT_Y_OFFSET - PAGE_COUNT_BOTTOM_MARGIN - LINE_HEIGHT) / LINE_HEIGHT; // 24

    private final WrittenBookContent content;
    private int currentPage = 0;

    private Button backButton;
    private Button forwardButton;

    public DustyDocumentsScreen(WrittenBookContent content) {
        super(Component.empty());
        this.content = content;
    }

    @Override
    protected void init() {
        super.init();
        int pageLeft = (width - PAGE_WIDTH) / 2;
        int pageTop  = (height - PAGE_HEIGHT) / 2;
        int btnY     = pageTop + PAGE_HEIGHT + 4;

        backButton = addRenderableWidget(Button.builder(
                Component.literal("◀"),
                b -> { currentPage--; updateButtons(); }
        ).bounds(pageLeft, btnY, 24, 20).build());

        forwardButton = addRenderableWidget(Button.builder(
                Component.literal("▶"),
                b -> { currentPage++; updateButtons(); }
        ).bounds(pageLeft + PAGE_WIDTH - 24, btnY, 24, 20).build());

        updateButtons();
    }

    private void updateButtons() {
        backButton.active    = currentPage > 0;
        forwardButton.active = currentPage < content.pages().size() - 1;
    }

    // Override renderBackground to avoid the blur effect — vanilla BookViewScreen does the same.
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);

        int pageLeft = (width - PAGE_WIDTH) / 2;
        int pageTop  = (height - PAGE_HEIGHT) / 2;

        List<Filterable<Component>> pages = content.pages();
        Component pageText = pages.isEmpty()
                ? Component.literal("")
                : pages.get(currentPage).raw();
        boolean isRipped = pageText.getString().isEmpty() && pageText.getSiblings().isEmpty();

        // Page background — replace fills with graphics.blit(WRITTEN/RIPPED_PAGE_TEXTURE, ...) when textures are ready
        if (isRipped) {
            graphics.fill(pageLeft, pageTop, pageLeft + PAGE_WIDTH, pageTop + PAGE_HEIGHT, 0xFF111111);
        } else {
            graphics.fill(pageLeft, pageTop, pageLeft + PAGE_WIDTH, pageTop + PAGE_HEIGHT, 0xFFEEEEE8);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int pageLeft = (width - PAGE_WIDTH) / 2;
        int pageTop  = (height - PAGE_HEIGHT) / 2;

        List<Filterable<Component>> pages = content.pages();
        Component pageText = pages.isEmpty()
                ? Component.literal("")
                : pages.get(currentPage).raw();
        boolean isRipped = pageText.getString().isEmpty() && pageText.getSiblings().isEmpty();

        if (!isRipped) {
            // Title — left-aligned with text body, bold, no drop shadow
            Component boldTitle = Component.literal(content.title().raw()).withStyle(ChatFormatting.BOLD);
            graphics.drawString(font, boldTitle, pageLeft + TEXT_X_OFFSET, pageTop + 14, 0xFF333333, false);
            // Separator
            graphics.fill(pageLeft + TEXT_X_OFFSET, pageTop + 26,
                          pageLeft + PAGE_WIDTH - TEXT_X_OFFSET, pageTop + 27, 0xFF999999);
            // Faint rule lines — one per line slot across the full text width
            for (int i = 0; i < MAX_LINES; i++) {
                int ruleY = pageTop + TEXT_Y_OFFSET + (i + 1) * LINE_HEIGHT - 1;
                graphics.fill(pageLeft + TEXT_X_OFFSET, ruleY,
                        pageLeft + PAGE_WIDTH - TEXT_X_OFFSET, ruleY + 1, 0xFFE0E0D8);
            }
            // Page text — same width/offsets as vanilla BookViewScreen
            List<FormattedCharSequence> lines = font.split(pageText, TEXT_WIDTH);
            int k = Math.min(MAX_LINES, lines.size());
            for (int i = 0; i < k; i++) {
                graphics.drawString(font, lines.get(i),
                        pageLeft + TEXT_X_OFFSET, pageTop + TEXT_Y_OFFSET + i * LINE_HEIGHT,
                        0, false);
            }
        }

        // Page indicator — bottom right corner of the document
        String indicator = (currentPage + 1) + " / " + pages.size();
        int indicatorWidth = font.width(indicator);
        graphics.drawString(font, indicator,
                pageLeft + PAGE_WIDTH - indicatorWidth - 4,
                pageTop + PAGE_HEIGHT - PAGE_COUNT_BOTTOM_MARGIN,
                isRipped ? 0xFF888888 : 0, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // GLFW 266/267 = PageUp/PageDown, matching vanilla BookViewScreen
        if (keyCode == 267 && currentPage > 0) {
            currentPage--;
            updateButtons();
            return true;
        }
        if (keyCode == 266 && currentPage < content.pages().size() - 1) {
            currentPage++;
            updateButtons();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
