package com.aydep.thecatalog.client;

import static com.aydep.thecatalog.client.BookLayout.FOOTER;
import static com.aydep.thecatalog.client.BookLayout.HEADER;
import static com.aydep.thecatalog.client.BookLayout.MARGIN;
import static com.aydep.thecatalog.client.BookLayout.SLOT;
import static com.aydep.thecatalog.client.DisplayRecipe.sprite;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** The open book: an item grid with bookmark tabs, and recipe pages. Every page change turns a page. */
public class CatalogScreen extends Screen {
    private static final ResourceLocation PAGE_LEFT = sprite("page_left"), PAGE_RIGHT = sprite("page_right"), COVER = sprite("cover"),
            SEARCH_FIELD = sprite("search_field"), SEARCH_ICON = sprite("search_icon"),
            TAB = sprite("tab"), TAB_SELECTED = sprite("tab_selected"), TAB_UP = sprite("tab_up"), TAB_DOWN = sprite("tab_down"),
            FORWARD = sprite("page_forward"), FORWARD_HOT = sprite("page_forward_highlighted"),
            BACKWARD = sprite("page_backward"), BACKWARD_HOT = sprite("page_backward_highlighted"),
            BACK = sprite("back"), BACK_HOT = sprite("back_highlighted");
    private static final int INK = 0xFF4A3524, FAINT_INK = 0xFF9A8462, HOVER = 0x304A3524;
    private static final int SEARCH_TEXT = 0xFF5A4330, SEARCH_HINT = 0xFFB09A74, COVER_BORDER_X = 7, COVER_BORDER_Y = 6;
    private static final int TAB_HEIGHT = 22, RECIPE_HEADER = 34, RECIPE_GAP = 6;

    sealed interface View permits IndexView, RecipeView {
        int spread();
    }

    record IndexView(int tab, String query, int spread) implements View {}

    record RecipeView(ItemStack stack, boolean uses, int spread) implements View {}

    private record Hotspot(int x, int y, int w, int h, Runnable action) {}

    // Remembered while the game runs, so the book reopens where it was left, Back history included.
    private static View lastView = new IndexView(0, "", 0);
    private static final Deque<View> history = new ArrayDeque<>();
    private static int tabScroll;

    private final @Nullable Screen parent;
    private final CatalogIndex index = CatalogIndex.get();
    private final Map<Object, Object> cache = new HashMap<>();
    private final List<Hotspot> hotspots = new ArrayList<>();
    private View view;
    private @Nullable View flipFrom;
    private boolean flipForward;
    private long flipStart;
    private BookLayout layout;
    private EditBox search;
    private ItemStack hovered = ItemStack.EMPTY;
    private @Nullable Component hoveredText;

    /** @param parent screen to return to on close; @param start page to open on, or null for where the book was left */
    public CatalogScreen(@Nullable Screen parent, @Nullable RecipeView start) {
        super(Component.translatable("item.thecatalog.catalog"));
        this.parent = parent;
        if (start != null) {
            history.push(lastView);
            lastView = start;
        }
        view = lastView;
    }

    static void forget() {
        lastView = new IndexView(0, "", 0);
        history.clear();
        tabScroll = 0;
    }

    @Override
    protected void init() {
        layout = BookLayout.fit(width, height, ClientConfig.MAX_PAGE_WIDTH.get(), ClientConfig.MAX_PAGE_HEIGHT.get(), tabsLeft());
        cache.clear();
        int x = layout.x() + 48, y = layout.y() + layout.pageHeight() - 21;
        search = new EditBox(font, x, y, layout.pageWidth() - 48 - MARGIN, 10, Component.translatable("gui.thecatalog.search"));
        search.setBordered(false);
        search.setMaxLength(50);
        search.setTextColor(SEARCH_TEXT);
        search.setTextShadow(false);
        search.setHint(Component.translatable("gui.thecatalog.search").withColor(SEARCH_HINT));
        if (view instanceof IndexView v) search.setValue(v.query());
        search.setResponder(query -> {
            if (view instanceof IndexView v && !v.query().equals(query)) setView(new IndexView(v.tab(), query, 0));
        });
        addRenderableWidget(search);
        if (view.spread() >= spreads(view)) setView(withSpread(view, spreads(view) - 1));
        updateSearch();
    }

    // ---- navigation ----

    private void setView(View next) {
        view = next;
        lastView = next;
        updateSearch();
    }

    private void flipTo(View next, boolean forward) {
        if (ClientConfig.PAGE_TURN_ANIMATION.get()) {
            flipFrom = view;
            flipForward = forward;
            flipStart = Util.getMillis();
        }
        if (ClientConfig.PAGE_TURN_SOUND.get()) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }
        setView(next);
    }

    private void updateSearch() {
        search.visible = view instanceof IndexView && flipFrom == null;
        if (!search.visible) search.setFocused(false);
        if (view instanceof IndexView v && !search.getValue().equals(v.query())) search.setValue(v.query());
    }

    private void open(ItemStack stack, boolean uses) {
        if (view instanceof RecipeView v && v.uses() == uses && ItemStack.isSameItemSameComponents(v.stack(), stack)) return;
        history.push(view);
        flipTo(new RecipeView(stack.copyWithCount(1), uses, 0), true);
    }

    private void back() {
        if (!history.isEmpty()) flipTo(history.pop(), false);
    }

    private void turnPage(int direction) {
        int spread = Math.clamp(view.spread() + direction, 0, spreads(view) - 1);
        if (spread != view.spread()) flipTo(withSpread(view, spread), direction > 0);
    }

    private void selectTab(int tab) {
        String query = view instanceof IndexView v ? v.query() : "";
        int current = view instanceof IndexView v ? v.tab() : -1;
        if (tab == current) return;
        if (view instanceof RecipeView) history.push(view);
        flipTo(new IndexView(tab, query, 0), tab > current);
    }

    private static View withSpread(View v, int spread) {
        return switch (v) {
            case IndexView i -> new IndexView(i.tab(), i.query(), spread);
            case RecipeView r -> new RecipeView(r.stack(), r.uses(), spread);
        };
    }

    // ---- data ----

    @SuppressWarnings("unchecked")
    private <T> T cached(Object key, Supplier<T> compute) {
        return (T) cache.computeIfAbsent(key, k -> compute.get());
    }

    private CatalogIndex.Tab tab(IndexView v) {
        return index.tabs.get(Math.clamp(v.tab(), 0, index.tabs.size() - 1));
    }

    private List<ItemStack> items(IndexView v) {
        return cached(List.of(v.tab(), v.query()), () -> {
            List<ItemStack> items = tab(v).items();
            boolean hideUncraftable = ClientConfig.HIDE_ITEMS_WITHOUT_RECIPES.get();
            if (v.query().isBlank() && !hideUncraftable) return items;
            return items.stream().filter(s -> {
                if (hideUncraftable && index.recipesFor(s).isEmpty()) return false;
                String mod = CatalogIndex.modId(s);
                return CatalogSearch.matches(v.query(), s.getHoverName().getString(), mod, CatalogIndex.modName(mod));
            }).toList();
        });
    }

    /** The view's recipes split into pages; left pages leave room for the header. */
    private List<List<DisplayRecipe>> recipePages(RecipeView v) {
        return cached(List.of(v.stack().getItem(), v.stack().getComponentsPatch(), v.uses()), () -> {
            List<List<DisplayRecipe>> pages = new ArrayList<>();
            List<DisplayRecipe> page = new ArrayList<>();
            int used = 0;
            for (DisplayRecipe recipe : v.uses() ? index.usesFor(v.stack()) : index.recipesFor(v.stack())) {
                int room = layout.pageHeight() - MARGIN - FOOTER - (pages.size() % 2 == 0 ? RECIPE_HEADER : 0);
                // shortcut: a recipe taller than a whole page (huge modded grids) overflows it instead of being scaled down.
                if (!page.isEmpty() && used + recipe.height > room) {
                    pages.add(page);
                    page = new ArrayList<>();
                    used = 0;
                }
                page.add(recipe);
                used += recipe.height + RECIPE_GAP;
            }
            if (!page.isEmpty()) pages.add(page);
            return pages;
        });
    }

    private int spreads(View v) {
        int pages = switch (v) {
            case IndexView i -> (items(i).size() + layout.perPage() - 1) / layout.perPage();
            case RecipeView r -> recipePages(r).size();
        };
        return BookLayout.spreads(pages);
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (flipFrom != null && Util.getMillis() - flipStart >= ClientConfig.PAGE_TURN_MILLIS.get()) {
            flipFrom = null;
            updateSearch();
        }
        hovered = ItemStack.EMPTY;
        hoveredText = null;
        hotspots.clear();
        super.render(g, mouseX, mouseY, partialTick); // the book (renderBackground), then the search box
        if (!hovered.isEmpty()) {
            List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, hovered));
            if (ClientConfig.SHOW_MOD_NAME.get()) lines.add(Component.literal(CatalogIndex.modName(CatalogIndex.modId(hovered))).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
            g.renderTooltip(font, lines, hovered.getTooltipImage(), hovered, mouseX, mouseY);
        } else if (hoveredText != null) {
            g.renderTooltip(font, hoveredText, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        if (flipFrom != null) mouseX = mouseY = -1; // nothing is hoverable while a page turns
        int bx = layout.x(), by = layout.y(), pw = layout.pageWidth(), ph = layout.pageHeight();
        // cover, then bookmarks lying on it, then the pages on top of the bookmarks' inner ends
        g.blitSprite(COVER, bx - COVER_BORDER_X, by - COVER_BORDER_Y, 2 * pw + 2 * COVER_BORDER_X, ph + 2 * COVER_BORDER_Y);
        drawTabs(g, mouseX, mouseY);
        g.blitSprite(PAGE_LEFT, bx, by, pw, ph);
        g.blitSprite(PAGE_RIGHT, bx + pw, by, pw, ph);
        if (search.visible) {
            g.blitSprite(SEARCH_FIELD, search.getX() - 13, search.getY() - 3, search.getWidth() + 15, 14);
            g.blitSprite(SEARCH_ICON, search.getX() - 11, search.getY() - 1, 9, 9);
        }
        if (flipFrom == null) {
            drawPage(g, view, 0, mouseX, mouseY);
            drawPage(g, view, 1, mouseX, mouseY);
            return;
        }
        // A sheet turns around the spine. Under it, the side it leaves already shows the new page,
        // the side it lands on still shows the old one. Its front is the old page, its back the new one.
        float t = Math.min(1, (Util.getMillis() - flipStart) / ClientConfig.PAGE_TURN_MILLIS.get().floatValue());
        int from = flipForward ? 1 : 0, to = 1 - from;
        drawPage(g, view, from, -1, -1);
        drawPage(g, flipFrom, to, -1, -1);
        boolean front = t < 0.5f;
        int side = front ? from : to;
        float squash = Math.abs(Mth.cos(t * Mth.PI));
        int spine = bx + pw, px = bx + side * pw;
        g.pose().pushPose();
        g.pose().translate(spine, 0, 250); // above the items drawn underneath
        g.pose().scale(squash, 1, 1);
        g.pose().translate(-spine, 0, 0);
        g.blitSprite(side == 0 ? PAGE_LEFT : PAGE_RIGHT, px, by, pw, ph);
        drawPage(g, front ? flipFrom : view, side, -1, -1);
        g.fill(px, by, px + pw, by + ph, (int) ((1 - squash) * 0x60) << 24); // darker as it turns edge-on
        g.pose().popPose();
    }

    private static boolean tabsLeft() {
        return ClientConfig.TAB_SIDE.get() == ClientConfig.TabSide.LEFT;
    }

    /** Bookmarks down one edge (4px tucked under the page); arrows scroll to the previous / next group when they don't all fit. */
    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        List<CatalogIndex.Tab> tabs = index.tabs;
        boolean left = tabsLeft();
        int pw = layout.pageWidth(), top = layout.y() + 12;
        int tx = left ? layout.x() - BookLayout.TAB_WIDTH + 4 : layout.x() + 2 * pw - 4;
        int outward = left ? -3 : 3, visibleX = left ? tx : tx + 4; // the part of the tab not under the page
        int visible = (layout.pageHeight() - 24) / TAB_HEIGHT;
        tabScroll = Math.clamp(tabScroll, 0, Math.max(0, tabs.size() - visible));
        if (tabScroll > 0) {
            button(g, TAB_UP, TAB_UP, visibleX + 5, layout.y() + 3, 12, 7, mouseX, mouseY, () -> tabScroll -= visible);
        }
        if (tabScroll + visible < tabs.size()) {
            button(g, TAB_DOWN, TAB_DOWN, visibleX + 5, top + visible * TAB_HEIGHT + 2, 12, 7, mouseX, mouseY, () -> tabScroll += visible);
        }
        int current = view instanceof IndexView v ? v.tab() : -1;
        for (int i = tabScroll; i < Math.min(tabs.size(), tabScroll + visible); i++) {
            int x = tx + (i == current ? outward : 0), y = top + (i - tabScroll) * TAB_HEIGHT;
            g.blitSprite(i == current ? TAB_SELECTED : TAB, x, y, 26, TAB_HEIGHT);
            g.renderItem(tabs.get(i).icon(), x + 5, y + 3);
            if (inside(mouseX, mouseY, x, y, 26, TAB_HEIGHT)) hoveredText = tabs.get(i).name();
            int tab = i;
            hotspots.add(new Hotspot(x, y, 26, TAB_HEIGHT, () -> selectTab(tab)));
        }
    }

    private void drawPage(GuiGraphics g, View v, int side, int mouseX, int mouseY) {
        int pw = layout.pageWidth(), px = layout.x() + side * pw, py = layout.y();
        switch (v) {
            case IndexView i -> drawIndexPage(g, i, side, px, py, mouseX, mouseY);
            case RecipeView r -> drawRecipePage(g, r, side, px, py, mouseX, mouseY);
        }
        int spreads = spreads(v), footerY = py + layout.pageHeight() - 21;
        if (side == 0 && v.spread() > 0) {
            button(g, BACKWARD, BACKWARD_HOT, px + MARGIN, footerY, 18, 10, mouseX, mouseY, () -> turnPage(-1));
        }
        if (side == 1 && v.spread() < spreads - 1) {
            button(g, FORWARD, FORWARD_HOT, px + pw - MARGIN - 18, footerY, 18, 10, mouseX, mouseY, () -> turnPage(1));
        }
        if (side == 1) {
            String number = (v.spread() + 1) + " / " + spreads;
            g.drawString(font, number, px + (pw - font.width(number)) / 2, footerY + 1, FAINT_INK, false);
        }
    }

    private void drawIndexPage(GuiGraphics g, IndexView v, int side, int px, int py, int mouseX, int mouseY) {
        List<ItemStack> items = items(v);
        int pw = layout.pageWidth(), cols = layout.cols(), perPage = layout.perPage();
        int gx = px + MARGIN + (pw - 2 * MARGIN - cols * SLOT) / 2, gy = py + MARGIN + HEADER;
        if (side == 0) {
            FormattedCharSequence name = trim(tab(v).name(), pw - 2 * MARGIN);
            g.drawString(font, name, px + (pw - font.width(name)) / 2, py + MARGIN, INK, false);
            if (items.isEmpty()) g.drawString(font, Component.translatable("gui.thecatalog.nothing_found"), gx, gy, FAINT_INK, false);
        }
        int start = (v.spread() * 2 + side) * perPage;
        for (int i = 0; i < perPage && start + i < items.size(); i++) {
            drawStack(g, items.get(start + i), gx + i % cols * SLOT, gy + i / cols * SLOT, mouseX, mouseY);
        }
    }

    private void drawRecipePage(GuiGraphics g, RecipeView v, int side, int px, int py, int mouseX, int mouseY) {
        List<List<DisplayRecipe>> pages = recipePages(v);
        int page = v.spread() * 2 + side, width = layout.pageWidth() - 2 * MARGIN;
        int x = px + MARGIN, y = py + MARGIN;
        if (side == 0) {
            if (!history.isEmpty()) {
                button(g, BACK, BACK_HOT, x, y + 3, 14, 10, mouseX, mouseY, this::back);
                if (inside(mouseX, mouseY, x, y + 3, 14, 10)) hoveredText = Component.translatable("gui.thecatalog.back");
            }
            drawStack(g, v.stack(), x + 16, y - 1, mouseX, mouseY);
            g.drawString(font, trim(v.stack().getHoverName(), width - 36), x + 36, y + 4, INK, false);
            Component recipes = Component.translatable("gui.thecatalog.recipes"), uses = Component.translatable("gui.thecatalog.uses");
            modeToggle(g, recipes, x, y + 22, !v.uses(), mouseX, mouseY, () -> switchMode(v, false));
            modeToggle(g, uses, x + font.width(recipes) + 10, y + 22, v.uses(), mouseX, mouseY, () -> switchMode(v, true));
            y += RECIPE_HEADER;
            if (pages.isEmpty()) {
                String key = v.uses() ? "gui.thecatalog.no_uses" : "gui.thecatalog.no_recipes";
                g.drawString(font, Component.translatable(key), x, y, FAINT_INK, false);
            }
        }
        if (page >= pages.size()) return;
        for (DisplayRecipe recipe : pages.get(page)) {
            drawRecipe(g, recipe, x, y, width, mouseX, mouseY);
            y += recipe.height + RECIPE_GAP;
        }
    }

    private void switchMode(RecipeView v, boolean uses) {
        if (v.uses() != uses) flipTo(new RecipeView(v.stack(), uses, 0), uses);
    }

    private void modeToggle(GuiGraphics g, Component label, int x, int y, boolean active, int mouseX, int mouseY, Runnable action) {
        int w = font.width(label);
        boolean hover = inside(mouseX, mouseY, x, y - 1, w, 11);
        g.drawString(font, label, x, y, active || hover ? INK : FAINT_INK, false);
        if (active) g.fill(x, y + 9, x + w, y + 10, INK);
        hotspots.add(new Hotspot(x, y - 1, w, 11, action));
    }

    private void drawRecipe(GuiGraphics g, DisplayRecipe recipe, int x, int y, int width, int mouseX, int mouseY) {
        g.drawString(font, trim(recipe.caption, width), x, y, FAINT_INK, false);
        for (DisplayRecipe.Picture p : recipe.pictures) g.blitSprite(p.sprite(), x + p.x(), y + p.y(), p.width(), p.height());
        for (DisplayRecipe.Slot slot : recipe.inputs) drawStack(g, slot.shown(), x + slot.x(), y + slot.y(), mouseX, mouseY);
        drawStack(g, recipe.shownOutput(), x + recipe.output.x(), y + recipe.output.y(), mouseX, mouseY);
        if (recipe.note != null) g.drawString(font, recipe.note, x + recipe.noteX, y + recipe.noteY, FAINT_INK, false);
    }

    private void drawStack(GuiGraphics g, ItemStack stack, int x, int y, int mouseX, int mouseY) {
        if (stack.isEmpty()) return;
        if (inside(mouseX, mouseY, x, y, SLOT, SLOT)) {
            g.fill(x, y, x + SLOT, y + SLOT, HOVER);
            hovered = stack;
        }
        g.renderItem(stack, x + 1, y + 1);
        g.renderItemDecorations(font, stack, x + 1, y + 1);
    }

    private void button(GuiGraphics g, ResourceLocation sprite, ResourceLocation hotSprite, int x, int y, int w, int h,
                        int mouseX, int mouseY, Runnable action) {
        g.blitSprite(inside(mouseX, mouseY, x, y, w, h) ? hotSprite : sprite, x, y, w, h);
        hotspots.add(new Hotspot(x, y, w, h, action));
    }

    private FormattedCharSequence trim(Component text, int maxWidth) {
        return Language.getInstance().getVisualOrder(font.substrByWidth(text, maxWidth));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (search.visible && search.isMouseOver(mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) search.setValue("");
            return super.mouseClicked(mouseX, mouseY, button);
        }
        search.setFocused(false);
        setFocused(null);
        if (flipFrom != null) return true;
        // hovered and hotspots come from the last frame drawn, which is what the player is looking at
        if (!hovered.isEmpty() && button <= GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            open(hovered, button == GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            for (Hotspot h : hotspots) {
                if (inside(mouseX, mouseY, h.x(), h.y(), h.w(), h.h())) {
                    h.action().run();
                    return true;
                }
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            back();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) return false;
        boolean overTabs = tabsLeft() ? mouseX < layout.x() : mouseX >= layout.x() + 2 * layout.pageWidth();
        if (overTabs) tabScroll -= (int) Math.signum(scrollY); // over the bookmarks
        else if (flipFrom == null) turnPage(scrollY < 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (search.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            super.keyPressed(keyCode, scanCode, modifiers);
            return true; // typing must not trigger the shortcuts below
        }
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (flipFrom == null) {
            boolean recipes = CatalogClient.RECIPES.isActiveAndMatches(key), uses = CatalogClient.USES.isActiveAndMatches(key);
            if (!hovered.isEmpty() && (recipes || uses)) {
                open(hovered, uses);
                return true;
            }
            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE -> back();
                case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP -> turnPage(-1);
                case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN -> turnPage(1);
                default -> {
                    if (minecraft.options.keyInventory.isActiveAndMatches(key) || CatalogClient.OPEN.matches(keyCode, scanCode)) {
                        onClose();
                        return true;
                    }
                    return super.keyPressed(keyCode, scanCode, modifiers);
                }
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return ClientConfig.PAUSE_GAME.get();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
