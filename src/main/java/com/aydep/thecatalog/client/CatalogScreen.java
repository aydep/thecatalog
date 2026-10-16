package com.aydep.thecatalog.client;

import static com.aydep.thecatalog.client.BookLayout.FOOTER;
import static com.aydep.thecatalog.client.BookLayout.HEADER;
import static com.aydep.thecatalog.client.BookLayout.MARGIN;
import static com.aydep.thecatalog.client.BookLayout.SLOT;
import static com.aydep.thecatalog.client.DisplayRecipe.sprite;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
    private static final ResourceLocation CRAFTING = ResourceLocation.withDefaultNamespace("crafting");
    private static final ResourceLocation PAGE_LEFT = sprite("page_left"), PAGE_RIGHT = sprite("page_right"), COVER = sprite("cover"),
            SEARCH_TAB = sprite("search_tab"), SEARCH_ICON = sprite("search_icon"),
            SLOT_BOX = sprite("slot"), RECIPE_PANEL = sprite("recipe_panel"), OUTPUT_FRAME = sprite("output_frame"),
            CHEVRON = sprite("chevron"), TAB_TOP = sprite("tab_top"), TAB_TOP_SELECTED = sprite("tab_top_selected"),
            TAB_PREV = sprite("tab_prev"), TAB_NEXT = sprite("tab_next"),
            TAB = sprite("tab"), TAB_SELECTED = sprite("tab_selected"),
            TAB_RIGHT = sprite("tab_right"), TAB_RIGHT_SELECTED = sprite("tab_right_selected"), TAB_UP = sprite("tab_up"), TAB_DOWN = sprite("tab_down"),
            FORWARD = sprite("page_forward"), FORWARD_HOT = sprite("page_forward_highlighted"),
            BACKWARD = sprite("page_backward"), BACKWARD_HOT = sprite("page_backward_highlighted"),
            BACK = sprite("back"), BACK_HOT = sprite("back_highlighted");
    private static final int INK = 0xFF4A3524, FAINT_INK = 0xFF9A8462, HOVER = 0x304A3524, BRACE = 0xFFC4A877;
    private static final int SEARCH_TEXT = 0xFF5A4330, SEARCH_HINT = 0xFFB09A74, COVER_BORDER_X = 7, COVER_BORDER_Y = 6;
    private static final int TOP_TAB_WIDTH = 22, TOP_TAB_HEIGHT = 32, TOP_TAB_STEP = 23;
    private static final int SEARCH_TAB_INSET = 10, TAB_HEIGHT = 22, RECIPE_HEADER = 34, RECIPE_GAP = 6;

    sealed interface View permits IndexView, RecipeView {
        int spread();
    }

    record IndexView(int tab, String query, int spread) implements View {}

    /** {@code category}: which category bookmark is open, an index into that item's categories. */
    record RecipeView(ItemStack stack, boolean uses, int category, int spread) implements View {}

    private record Hotspot(int x, int y, int w, int h, Runnable action) {}

    // Remembered while the game runs, so the book reopens where it was left, Back history included.
    private static View lastView = new IndexView(0, "", 0);
    private static final Deque<View> history = new ArrayDeque<>();
    private static int tabScroll;
    private static int lastTab; // the item grid's bookmark, for searches typed on a recipe page

    private final @Nullable Screen parent;
    private @Nullable CatalogIndex index = CatalogIndex.current(); // null until the first background build is done
    private final Map<Object, Object> cache = new HashMap<>();
    private final List<Hotspot> hotspots = new ArrayList<>();
    private View view;
    private int categoryScroll;
    private @Nullable View categoryScrollView;
    private @Nullable View flipFrom;
    private boolean flipForward;
    private long flipStart;
    private BookLayout layout;
    private EditBox search;
    private ItemStack hovered = ItemStack.EMPTY;
    private @Nullable ExternalRecipes.Entry hoveredExternal; // a JEI recipe under the mouse draws its own tooltips
    private @Nullable Component hoveredText;

    /** @param parent screen to return to on close; @param start page to open on, or null for where the book was left */
    public CatalogScreen(@Nullable Screen parent, @Nullable RecipeView start) {
        super(Component.translatable("item.thecatalog.catalog"));
        this.parent = parent;
        CatalogIndex.requestIfMissing();
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
        lastTab = 0;
    }

    @Override
    protected void init() {
        layout = BookLayout.fit(width, height, ClientConfig.MAX_COLUMNS.get(), ClientConfig.MAX_ROWS.get(), tabsLeft());
        cache.clear();
        // the search sits on a paper tab hanging under the left page, outside the turning pages
        int x = layout.x() + SEARCH_TAB_INSET + 16, y = layout.y() + layout.pageHeight() + 9;
        search = new EditBox(font, x, y, layout.pageWidth() - 2 * SEARCH_TAB_INSET - 20, 10, Component.translatable("gui.thecatalog.search"));
        search.setBordered(false);
        search.setMaxLength(50);
        search.setTextColor(SEARCH_TEXT);
        search.setTextShadow(false);
        search.setHint(Component.translatable("gui.thecatalog.search").withColor(SEARCH_HINT));
        if (view instanceof IndexView v) search.setValue(v.query());
        search.setResponder(query -> {
            if (view instanceof IndexView v) {
                if (!v.query().equals(query)) setView(new IndexView(v.tab(), query, 0));
            } else {
                history.push(view); // typing on a recipe page goes to the item grid, Back returns
                setView(new IndexView(lastTab, query, 0));
            }
        });
        addRenderableWidget(search);
        if (index != null) clampSpread();
        updateSearch();
    }

    /** True once the index exists; picks it up the moment the background build finishes. */
    private boolean ready() {
        if (index == null && (index = CatalogIndex.current()) != null) clampSpread();
        return index != null;
    }

    /** After a resize or a new index there may be fewer pages than the page we were on. */
    private void clampSpread() {
        if (view.spread() >= spreads(view)) setView(withSpread(view, spreads(view) - 1));
    }

    // ---- navigation ----

    private void setView(View next) {
        view = next;
        lastView = next;
        if (next instanceof IndexView v) lastTab = v.tab();
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

    /** Shows the grid's query when the grid is shown; on recipe pages the last query stays visible. */
    private void updateSearch() {
        if (view instanceof IndexView v && !search.getValue().equals(v.query())) search.setValue(v.query());
    }

    private void open(ItemStack stack, boolean uses) {
        if (view instanceof RecipeView v && v.uses() == uses && ItemStack.isSameItemSameComponents(v.stack(), stack)) return;
        history.push(view);
        flipTo(new RecipeView(stack.copyWithCount(1), uses, 0, 0), true);
    }

    private void back() {
        if (!history.isEmpty()) flipTo(history.pop(), false);
    }

    private void turnPage(int direction) {
        if (!ready()) return;
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
            case RecipeView r -> new RecipeView(r.stack(), r.uses(), r.category(), spread);
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
            CatalogIndex.Tab tab = tab(v);
            boolean hideUncraftable = ClientConfig.HIDE_ITEMS_WITHOUT_RECIPES.get();
            if (v.query().isBlank() && !hideUncraftable) return tab.items();
            List<ItemStack> found = new ArrayList<>();
            for (int i = 0; i < tab.items().size(); i++) {
                ItemStack s = tab.items().get(i);
                String mod = CatalogIndex.modId(s);
                if (CatalogSearch.matches(v.query(), tab.names().get(i), mod, CatalogIndex.modName(mod))
                        && !(hideUncraftable && index.recipesFor(s).isEmpty())) found.add(s);
            }
            return found;
        });
    }

    /** The view's recipes split into pages; left pages leave room for the header. */
    /** Recipes from JEI when it is installed (every mod's JEI plugin), otherwise The Catalog's own index. */
    private List<? extends RecipeEntry> lookup(RecipeView v) {
        ExternalRecipes.Source external = ExternalRecipes.active();
        if (external != null) return external.lookup(v.stack(), v.uses());
        return v.uses() ? index.usesFor(v.stack()) : index.recipesFor(v.stack());
    }

    private List<? extends RecipeEntry> allRecipes(RecipeView v) {
        return cached(List.of("all", v.stack().getItem(), v.stack().getComponentsPatch(), v.uses()), () -> lookup(v));
    }

    /** One entry per category, in order of first appearance; JEI's order, or crafting first for The Catalog's own. */
    private List<RecipeEntry> categories(RecipeView v) {
        return cached(List.of("categories", v.stack().getItem(), v.stack().getComponentsPatch(), v.uses()), () -> {
            Map<Object, RecipeEntry> first = new LinkedHashMap<>();
            for (RecipeEntry entry : allRecipes(v)) first.putIfAbsent(entry.category(), entry);
            List<RecipeEntry> categories = new ArrayList<>(first.values());
            categories.sort(Comparator.comparing(e -> !CRAFTING.equals(e.category()))); // stable: only moves crafting to the front
            return categories;
        });
    }

    /** The open category's recipes. */
    private List<RecipeEntry> recipesIn(RecipeView v) {
        List<RecipeEntry> categories = categories(v);
        if (categories.isEmpty()) return List.of();
        Object category = categories.get(Math.clamp(v.category(), 0, categories.size() - 1)).category();
        return allRecipes(v).stream().filter(e -> e.category().equals(category)).map(e -> (RecipeEntry) e).toList();
    }

    /** Page 0 (the first left page) is left blank; page 1 starts with the item header. */
    private List<List<RecipeEntry>> recipePages(RecipeView v) {
        return cached(List.of(v.stack().getItem(), v.stack().getComponentsPatch(), v.uses(), v.category()), () -> {
            List<List<RecipeEntry>> pages = new ArrayList<>();
            pages.add(List.of());
            List<RecipeEntry> page = new ArrayList<>();
            int used = 0, width = layout.pageWidth() - 2 * MARGIN;
            for (RecipeEntry recipe : recipesIn(v)) {
                int room = layout.pageHeight() - MARGIN - FOOTER - (pages.size() == 1 ? RECIPE_HEADER : 0);
                int height = recipe.pageHeight(width);
                // shortcut: a recipe taller than a whole page (huge modded grids) overflows it instead of being scaled down.
                if (!page.isEmpty() && used + height > room) {
                    pages.add(page);
                    page = new ArrayList<>();
                    used = 0;
                }
                page.add(recipe);
                used += height + RECIPE_GAP;
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
        hoveredExternal = null;
        hoveredText = null;
        hotspots.clear();
        super.render(g, mouseX, mouseY, partialTick); // the book (renderBackground), then the search box
        if (hoveredExternal != null && hoveredExternal.drawTooltip(g, mouseX, mouseY)) {
            // JEI drew its own tooltip (fluids, chances, tag lists...)
        } else if (!hovered.isEmpty()) {
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
        // search tab, cover, then bookmarks lying on it, then the pages on top of the bookmarks' inner ends
        g.blitSprite(SEARCH_TAB, bx + SEARCH_TAB_INSET, by + ph - 8, pw - 2 * SEARCH_TAB_INSET, 8 + BookLayout.SEARCH_HEIGHT);
        g.blitSprite(COVER, bx - COVER_BORDER_X, by - COVER_BORDER_Y, 2 * pw + 2 * COVER_BORDER_X, ph + 2 * COVER_BORDER_Y);
        boolean ready = ready();
        if (ready) drawTabs(g, mouseX, mouseY);
        if (ready && view instanceof RecipeView v) drawCategoryTabs(g, v, mouseX, mouseY);
        g.blitSprite(PAGE_LEFT, bx, by, pw, ph);
        g.blitSprite(PAGE_RIGHT, bx + pw, by, pw, ph);
        g.blitSprite(SEARCH_ICON, search.getX() - 13, search.getY() - 1, 9, 9);
        if (!ready) {
            Component text = Component.translatable("gui.thecatalog.indexing");
            g.drawString(font, text, bx + (pw - font.width(text)) / 2, by + ph / 2 - 4, FAINT_INK, false);
            return;
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
        int outward = left ? -6 : 6, visibleX = left ? tx : tx + 4; // visibleX: the part of the tab not under the page
        int visible = (layout.pageHeight() - 24) / TAB_HEIGHT;
        tabScroll = Math.clamp(tabScroll, 0, Math.max(0, tabs.size() - visible));
        if (tabScroll > 0) {
            button(g, TAB_UP, TAB_UP, visibleX + 8, layout.y() + 3, 12, 7, mouseX, mouseY, () -> tabScroll -= visible);
        }
        if (tabScroll + visible < tabs.size()) {
            button(g, TAB_DOWN, TAB_DOWN, visibleX + 8, top + visible * TAB_HEIGHT + 2, 12, 7, mouseX, mouseY, () -> tabScroll += visible);
        }
        int current = view instanceof IndexView v ? v.tab() : -1;
        for (int i = tabScroll; i < Math.min(tabs.size(), tabScroll + visible); i++) {
            int x = tx + (i == current ? outward : 0), y = top + (i - tabScroll) * TAB_HEIGHT;
            ResourceLocation sprite = left ? (i == current ? TAB_SELECTED : TAB) : (i == current ? TAB_RIGHT_SELECTED : TAB_RIGHT);
            g.blitSprite(sprite, x, y, BookLayout.TAB_WIDTH, TAB_HEIGHT);
            g.renderItem(tabs.get(i).icon(), x + 8, y + 3);
            if (inside(mouseX, mouseY, x, y, BookLayout.TAB_WIDTH, TAB_HEIGHT)) hoveredText = tabs.get(i).name();
            int tab = i;
            hotspots.add(new Hotspot(x, y, BookLayout.TAB_WIDTH, TAB_HEIGHT, () -> selectTab(tab)));
        }
    }

    /** Category bookmarks along the top edge (4px tucked under the page); arrows scroll when they don't all fit. */
    private void drawCategoryTabs(GuiGraphics g, RecipeView v, int mouseX, int mouseY) {
        List<RecipeEntry> categories = categories(v);
        int bx = layout.x(), by = layout.y(), left = bx + 18, right = bx + 2 * layout.pageWidth() - 18;
        int visible = Math.max(1, (right - left) / TOP_TAB_STEP);
        int current = Math.clamp(v.category(), 0, Math.max(0, categories.size() - 1));
        // when the view changes (another item, Back), scroll so the open bookmark is in view; otherwise keep the player's scrolling
        if (v != categoryScrollView) {
            categoryScrollView = v;
            if (current < categoryScroll || current >= categoryScroll + visible) categoryScroll = current / visible * visible;
        }
        categoryScroll = Math.clamp(categoryScroll, 0, Math.max(0, categories.size() - visible));
        int arrowY = by - 23;
        if (categoryScroll > 0) {
            button(g, TAB_PREV, TAB_PREV, bx + 7, arrowY, 7, 12, mouseX, mouseY, () -> categoryScroll -= visible);
        }
        if (categoryScroll + visible < categories.size()) {
            button(g, TAB_NEXT, TAB_NEXT, right + 4, arrowY, 7, 12, mouseX, mouseY, () -> categoryScroll += visible);
        }
        for (int i = categoryScroll; i < Math.min(categories.size(), categoryScroll + visible); i++) {
            int x = left + (i - categoryScroll) * TOP_TAB_STEP, y = by - TOP_TAB_HEIGHT + 4 - (i == current ? 6 : 0);
            g.blitSprite(i == current ? TAB_TOP_SELECTED : TAB_TOP, x, y, TOP_TAB_WIDTH, TOP_TAB_HEIGHT);
            categories.get(i).drawCategoryIcon(g, x + 3, y + 6);
            if (inside(mouseX, mouseY, x, y, TOP_TAB_WIDTH, TOP_TAB_HEIGHT - 4)) hoveredText = categories.get(i).categoryTitle();
            int category = i;
            hotspots.add(new Hotspot(x, y, TOP_TAB_WIDTH, TOP_TAB_HEIGHT - 4, () -> selectCategory(v, category)));
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
        List<List<RecipeEntry>> pages = recipePages(v);
        int page = v.spread() * 2 + side, width = layout.pageWidth() - 2 * MARGIN;
        int x = px + MARGIN, y = py + MARGIN;
        if (page == 1) {
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
            if (pages.size() == 1) {
                String key = v.uses() ? "gui.thecatalog.no_uses" : "gui.thecatalog.no_recipes";
                g.drawString(font, Component.translatable(key), x, y, FAINT_INK, false);
            }
        }
        if (page >= pages.size()) return;
        for (RecipeEntry entry : pages.get(page)) {
            if (entry instanceof DisplayRecipe recipe) drawRecipe(g, recipe, x, y, mouseX, mouseY);
            else if (entry instanceof ExternalRecipes.Entry external) drawExternal(g, external, x, y, width, mouseX, mouseY);
            y += entry.pageHeight(width) + RECIPE_GAP;
        }
    }

    private void switchMode(RecipeView v, boolean uses) {
        if (v.uses() != uses) flipTo(new RecipeView(v.stack(), uses, 0, 0), uses);
    }

    private void selectCategory(RecipeView v, int category) {
        if (category != v.category()) flipTo(new RecipeView(v.stack(), v.uses(), category, 0), category > v.category());
    }

    private void modeToggle(GuiGraphics g, Component label, int x, int y, boolean active, int mouseX, int mouseY, Runnable action) {
        int w = font.width(label);
        boolean hover = inside(mouseX, mouseY, x, y - 1, w, 11);
        g.drawString(font, label, x, y, active || hover ? INK : FAINT_INK, false);
        if (active) g.fill(x, y + 9, x + w, y + 10, INK);
        hotspots.add(new Hotspot(x, y - 1, w, 11, action));
    }

    /** Input slots in a rounded panel; a brace, a chevron and the framed output, centered on the grid. */
    private void drawRecipe(GuiGraphics g, DisplayRecipe recipe, int x, int y, int mouseX, int mouseY) {
        int top = y + DisplayRecipe.CAPTION, bottom = top + recipe.panelHeight, right = x + recipe.panelWidth;
        g.blitSprite(RECIPE_PANEL, x, top, recipe.panelWidth, recipe.panelHeight);
        for (DisplayRecipe.Slot slot : recipe.inputs) {
            g.blitSprite(SLOT_BOX, x + slot.x(), y + slot.y(), SLOT, SLOT);
            drawStack(g, slot.shown(), x + slot.x(), y + slot.y(), mouseX, mouseY);
        }
        int outX = x + recipe.output.x(), outY = y + recipe.output.y(), mid = outY + SLOT / 2;
        drawBrace(g, right + 1, top, bottom);
        g.blitSprite(CHEVRON, right + 7, mid - 4, 7, 9);
        g.blitSprite(OUTPUT_FRAME, outX - 3, outY - 3, 24, 24);
        drawStack(g, recipe.shownOutput(), outX, outY, mouseX, mouseY);
        for (DisplayRecipe.Picture p : recipe.pictures) g.blitSprite(p.sprite(), x + p.x(), y + p.y(), p.width(), p.height());
        if (recipe.note != null) g.drawString(font, recipe.note, x + recipe.noteX, y + recipe.noteY, FAINT_INK, false);
    }

    /** A recipe from JEI, drawn by its category's own code, shrunk if wider than the page. */
    private void drawExternal(GuiGraphics g, ExternalRecipes.Entry entry, int x, int y, int width, int mouseX, int mouseY) {
        float scale = entry.scale(width);
        int top = y + DisplayRecipe.CAPTION;
        entry.draw(g, x, top, scale, mouseX, mouseY);
        if (inside(mouseX, mouseY, x, top, (int) (entry.width() * scale), (int) (entry.height() * scale))) {
            hoveredExternal = entry;
            hovered = entry.itemAt(mouseX, mouseY);
        }
    }

    /** A "}" as tall as the input panel, pointing at the chevron. */
    private static void drawBrace(GuiGraphics g, int x, int top, int bottom) {
        int mid = (top + bottom) / 2;
        g.fill(x, top + 1, x + 2, top + 2, BRACE);
        g.fill(x + 1, top + 2, x + 2, mid - 1, BRACE);
        g.fill(x + 2, mid - 1, x + 3, mid + 1, BRACE);
        g.fill(x + 1, mid + 1, x + 2, bottom - 2, BRACE);
        g.fill(x, bottom - 2, x + 2, bottom - 1, BRACE);
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
        if (search.isMouseOver(mouseX, mouseY)) {
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
    public void tick() {
        // JEI layouts cycle their ingredients on tick; only the two visible pages are ticked
        if (view instanceof RecipeView v && ready()) {
            List<List<RecipeEntry>> pages = recipePages(v);
            for (int page = v.spread() * 2; page < Math.min(pages.size(), v.spread() * 2 + 2); page++) {
                for (RecipeEntry entry : pages.get(page)) if (entry instanceof ExternalRecipes.Entry external) external.tick();
            }
        }
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
