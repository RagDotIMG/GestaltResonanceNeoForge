package net.ragdot.gestaltresonance.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WritableBookContent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.ragdot.gestaltresonance.common.network.SaveDustyDocC2S;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DustyDocumentsEditScreen extends Screen {

    private static final int PAGE_WIDTH              = 192;
    private static final int PAGE_HEIGHT             = 272;
    private static final int TEXT_X_OFFSET           = 16;
    private static final int TEXT_Y_OFFSET           = 30;
    private static final int TEXT_WIDTH              = PAGE_WIDTH - 2 * TEXT_X_OFFSET; // 160
    private static final int LINE_HEIGHT             = 9;
    private static final int PAGE_COUNT_BOTTOM_MARGIN = 10;
    private static final int MAX_LINES               = (PAGE_HEIGHT - TEXT_Y_OFFSET - PAGE_COUNT_BOTTOM_MARGIN - LINE_HEIGHT) / LINE_HEIGHT; // 24
    private static final int PAGE_TEXT_HEIGHT        = MAX_LINES * LINE_HEIGHT; // 216
    private static final int BOX_PADDING             = 4; // MultiLineEditBox inner padding
    private static final int TITLE_MAX_LENGTH        = 64;

    private final Player owner;
    private final int slot; // captured at open-time; hotbar changes while editing must not shift the target
    private final List<String> pages = new ArrayList<>();
    private int currentPage = 0;
    private boolean dirty = false;
    private String pendingTitle = ""; // survives init() re-calls on window resize

    private TextBox textBox;
    private EditBox titleBox;
    private Button backButton;
    private Button forwardButton;
    private Button addPageButton;

    public DustyDocumentsEditScreen(Player owner, ItemStack book, InteractionHand hand) {
        super(Component.empty());
        this.owner = owner;
        this.slot  = hand == InteractionHand.MAIN_HAND ? owner.getInventory().selected : 40;

        WritableBookContent content = book.get(DataComponents.WRITABLE_BOOK_CONTENT);
        if (content != null) {
            for (var page : content.pages()) pages.add(page.raw());
        }
        if (pages.isEmpty()) pages.add("");
        Component existingName = book.get(DataComponents.CUSTOM_NAME);
        if (existingName != null) pendingTitle = existingName.getString();
    }

    @Override
    protected void init() {
        super.init();
        int pageLeft = (width  - PAGE_WIDTH)  / 2;
        int pageTop  = (height - PAGE_HEIGHT) / 2;
        int btnY     = pageTop + PAGE_HEIGHT + 4;

        // Title edit box — replaces the static "FILE - WRITABLE" label
        titleBox = addRenderableWidget(new EditBox(
                font,
                pageLeft + TEXT_X_OFFSET,
                pageTop + 10,
                TEXT_WIDTH,
                12,
                Component.literal("Title...")));
        titleBox.setBordered(false);
        titleBox.setMaxLength(TITLE_MAX_LENGTH);
        titleBox.setTextColor(0xFF333333);
        titleBox.setTextColorUneditable(0xFF333333);
        titleBox.setTextShadow(false);
        titleBox.setHint(Component.literal("(untitled)").withStyle(ChatFormatting.ITALIC));
        if (!pendingTitle.isEmpty()) titleBox.setValue(pendingTitle);
        titleBox.setResponder(text -> { pendingTitle = text; dirty = true; });

        textBox = addRenderableWidget(new TextBox(
                font,
                pageLeft + TEXT_X_OFFSET - BOX_PADDING,
                pageTop  + TEXT_Y_OFFSET - BOX_PADDING,
                TEXT_WIDTH    + BOX_PADDING * 2,
                PAGE_TEXT_HEIGHT + BOX_PADDING * 2,
                Component.literal("...")));
        textBox.setCharacterLimit(WritableBookContent.PAGE_EDIT_LENGTH);
        textBox.setValue(pages.get(currentPage));
        textBox.setValueListener(text -> {
            if (currentPage < pages.size()) {
                pages.set(currentPage, text);
                dirty = true;
            }
        });

        backButton = addRenderableWidget(Button.builder(
                Component.literal("◀"),
                b -> navigate(-1)
        ).bounds(pageLeft, btnY, 24, 20).build());

        forwardButton = addRenderableWidget(Button.builder(
                Component.literal("▶"),
                b -> navigate(1)
        ).bounds(pageLeft + PAGE_WIDTH - 24, btnY, 24, 20).build());

        addPageButton = addRenderableWidget(Button.builder(
                Component.literal("+"),
                b -> addPage()
        ).bounds(pageLeft + (PAGE_WIDTH - 24) / 2, btnY, 24, 20).build());

        setFocused(textBox);
        updateButtons();
    }

    private void navigate(int delta) {
        int target = currentPage + delta;
        if (target < 0 || target >= pages.size()) return;
        currentPage = target;
        textBox.setValue(pages.get(currentPage));
        setFocused(textBox);
        updateButtons();
    }

    private void addPage() {
        if (pages.size() >= WritableBookContent.MAX_PAGES) return;
        pages.add(currentPage + 1, "");
        currentPage++;
        textBox.setValue("");
        dirty = true;
        setFocused(textBox);
        updateButtons();
    }

    private void updateButtons() {
        backButton.active    = currentPage > 0;
        forwardButton.active = currentPage < pages.size() - 1;
        addPageButton.active = pages.size() < WritableBookContent.MAX_PAGES;
    }

    @Override
    public void onClose() {
        if (dirty) saveToServer();
        super.onClose();
    }

    private void saveToServer() {
        String titleValue = pendingTitle.trim();
        Optional<String> title = titleValue.isEmpty() ? Optional.empty() : Optional.of(titleValue);
        PacketDistributor.sendToServer(new SaveDustyDocC2S(slot, pages, title));
    }

    // No blur — same reason as DustyDocumentsScreen.
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        int pageLeft = (width  - PAGE_WIDTH)  / 2;
        int pageTop  = (height - PAGE_HEIGHT) / 2;
        graphics.fill(pageLeft, pageTop, pageLeft + PAGE_WIDTH, pageTop + PAGE_HEIGHT, 0xFFEEEEE8);
        // Faint rule lines — one per line slot across the full text width
        for (int i = 0; i < MAX_LINES; i++) {
            int ruleY = pageTop + TEXT_Y_OFFSET + (i + 1) * LINE_HEIGHT - 1;
            graphics.fill(pageLeft + TEXT_X_OFFSET, ruleY,
                    pageLeft + PAGE_WIDTH - TEXT_X_OFFSET, ruleY + 1, 0xFFE0E0D8);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int pageLeft = (width  - PAGE_WIDTH)  / 2;
        int pageTop  = (height - PAGE_HEIGHT) / 2;

        // Separator line below title
        graphics.fill(pageLeft + TEXT_X_OFFSET, pageTop + 26,
                pageLeft + PAGE_WIDTH - TEXT_X_OFFSET, pageTop + 27, 0xFF999999);

        String indicator = (currentPage + 1) + " / " + pages.size();
        int indicatorWidth = font.width(indicator);
        graphics.drawString(font, indicator,
                pageLeft + PAGE_WIDTH - indicatorWidth - 4,
                pageTop + PAGE_HEIGHT - PAGE_COUNT_BOTTOM_MARGIN,
                0, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 267 && currentPage > 0)                   { navigate(-1); return true; }
        if (keyCode == 266 && currentPage < pages.size() - 1)   { navigate(1);  return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // -------------------------------------------------------------------------
    // Inner widget: strips the vanilla border, enforces the page line limit,
    // and renders text in the same dark colour as the read-only document viewer.
    // -------------------------------------------------------------------------
    private static final class TextBox extends MultiLineEditBox {

        private final Font myFont;
        private long myFocusedTime = Util.getMillis();

        // Reflected reference to the private MultilineTextField so we can read
        // the cursor position without reimplementing the full edit logic.
        @SuppressWarnings("FieldMayBeFinal")
        private net.minecraft.client.gui.components.MultilineTextField tf;

        TextBox(Font font, int x, int y, int width, int height, Component placeholder) {
            super(font, x, y, width, height, placeholder, placeholder);
            this.myFont = font;
            net.minecraft.client.gui.components.MultilineTextField tmp = null;
            try {
                java.lang.reflect.Field f = MultiLineEditBox.class.getDeclaredField("textField");
                f.setAccessible(true);
                tmp = (net.minecraft.client.gui.components.MultilineTextField) f.get(this);
            } catch (ReflectiveOperationException ignored) { }
            this.tf = tmp;
        }

        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);
            if (focused) myFocusedTime = Util.getMillis();
        }

        // No border sprite.
        @Override
        protected void renderBackground(GuiGraphics graphics) { }

        // No scrollbar or character-count label.
        @Override
        protected void renderDecorations(GuiGraphics graphics) { }

        /**
         * Draw text in black (matching the read-only viewer) and a simple cursor.
         * Called by AbstractScrollWidget.renderWidget() inside the scroll pose-transform
         * and scissor region, so position maths are the same as vanilla renderContents.
         */
        @Override
        protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            String s = getValue();
            int textX = getX() + innerPadding();
            int textY = getY() + innerPadding();
            int splitW = getWidth() - totalInnerPadding();

            boolean blink = isFocused() && (Util.getMillis() - myFocusedTime) / 300L % 2L == 0L;
            int cursorIdx = (tf != null) ? tf.cursor() : s.length();

            if (s.isEmpty()) {
                if (blink) graphics.fill(textX, textY, textX + 1, textY + 9, 0xFF555555);
                return;
            }

            List<FormattedCharSequence> lines = myFont.split(
                    net.minecraft.network.chat.Component.literal(s), splitW);
            int charPos = 0;

            for (int i = 0; i < lines.size(); i++) {
                int lineY = textY + i * 9;

                // Count UTF-16 code units (not codepoints) so charPos stays in sync
                // with tf.cursor(), which is a Java String char index.
                int[] cpCount = {0};
                lines.get(i).accept((idx, style, cp) -> {
                    cpCount[0] += Character.charCount(cp);
                    return true;
                });
                int lineEnd = charPos + cpCount[0];

                if (withinContentAreaTopBottom(lineY, lineY + 9)) {
                    graphics.drawString(myFont, lines.get(i), textX, lineY, 0x000000, false);

                    if (blink && cursorIdx >= charPos && cursorIdx <= lineEnd) {
                        int cx = textX + myFont.width(s.substring(charPos,
                                Math.min(cursorIdx, s.length())));
                        if (cursorIdx < s.length()) {
                            graphics.fill(cx, lineY - 1, cx + 1, lineY + 9, 0xFF555555);
                        } else {
                            graphics.drawString(myFont, "_", cx, lineY, 0xFF555555, false);
                        }
                    }
                }

                charPos = lineEnd;
                if (charPos < s.length() && s.charAt(charPos) == '\n') {
                    charPos++; // hard line break — skip the \n
                } else if (charPos < s.length() && s.charAt(charPos) == ' ') {
                    charPos++; // soft word-wrap — font.split() consumed this space
                }
            }
        }

        // Enforce MAX_LINES so content never requires scrolling.
        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            String before = getValue();
            boolean consumed = super.charTyped(codePoint, modifiers);
            if (consumed && lineCount(getValue()) > MAX_LINES) { setValue(before); }
            return consumed;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            String before = getValue();
            int linesBefore = lineCount(before);
            boolean consumed = super.keyPressed(keyCode, scanCode, modifiers);
            // Revert only if lines increased past the limit — this allows navigation keys
            // and deletion to work even when content was loaded with more than MAX_LINES.
            if (consumed && lineCount(getValue()) > MAX_LINES && lineCount(getValue()) > linesBefore) {
                setValue(before);
            }
            return consumed;
        }

        private int lineCount(String text) {
            if (text.isEmpty()) return 1;
            return myFont.split(net.minecraft.network.chat.Component.literal(text),
                    getWidth() - totalInnerPadding()).size();
        }
    }
}
