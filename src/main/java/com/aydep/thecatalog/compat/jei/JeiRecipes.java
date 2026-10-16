package com.aydep.thecatalog.compat.jei;

import com.aydep.thecatalog.TheCatalog;
import com.aydep.thecatalog.client.ExternalRecipes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IScalableDrawable;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Every recipe JEI knows, from every mod's JEI plugin, looked up by item and drawn by the category's own code. */
final class JeiRecipes implements ExternalRecipes.Source {
    private static final ResourceLocation PANEL = ResourceLocation.fromNamespaceAndPath(TheCatalog.MODID, "recipe_panel");
    private static final int BORDER = 3;
    // The book's paper panel instead of JEI's grey box.
    private static final IScalableDrawable BACKGROUND = (g, x, y, width, height) -> g.blitSprite(PANEL, x, y, width, height);

    private final IRecipeManager recipes;
    private final IFocusFactory focuses;
    private final Map<RecipeType<?>, ItemStack> catalysts = new HashMap<>();

    JeiRecipes(IJeiRuntime runtime) {
        this.recipes = runtime.getRecipeManager();
        this.focuses = runtime.getJeiHelpers().getFocusFactory();
    }

    @Override
    public List<ExternalRecipes.Entry> lookup(ItemStack stack, boolean uses) {
        // "Uses" also covers being the machine a recipe runs in (a furnace is used by every smelting recipe).
        List<IFocus<ItemStack>> foci = uses
                ? List.of(focus(RecipeIngredientRole.INPUT, stack), focus(RecipeIngredientRole.CATALYST, stack))
                : List.of(focus(RecipeIngredientRole.OUTPUT, stack));
        IFocusGroup group = focuses.createFocusGroup(foci);
        List<ExternalRecipes.Entry> entries = new ArrayList<>();
        recipes.createRecipeCategoryLookup().limitFocus(foci).get().forEach(category -> addRecipes(entries, category, foci, group));
        return entries;
    }

    /** The first machine of a category (a furnace for smelting), or empty. */
    private ItemStack catalyst(RecipeType<?> type) {
        return catalysts.computeIfAbsent(type, t -> recipes.createRecipeCatalystLookup(t).get()
                .flatMap(i -> i.getIngredient(VanillaTypes.ITEM_STACK).stream()).findFirst().orElse(ItemStack.EMPTY));
    }

    private IFocus<ItemStack> focus(RecipeIngredientRole role, ItemStack stack) {
        return focuses.createFocus(role, VanillaTypes.ITEM_STACK, stack);
    }

    private <T> void addRecipes(List<ExternalRecipes.Entry> entries, IRecipeCategory<T> category, List<IFocus<ItemStack>> foci, IFocusGroup group) {
        recipes.createRecipeLookup(category.getRecipeType()).limitFocus(foci).get()
                .forEach(recipe -> entries.add(new Entry<>(category, recipe, group)));
    }

    /** One JEI recipe. Its layout is built the first time its page is drawn. */
    private final class Entry<T> implements ExternalRecipes.Entry {
        private final IRecipeCategory<T> category;
        private final T recipe;
        private final IFocusGroup group;
        private @Nullable IRecipeLayoutDrawable<T> layout;
        private boolean failed;
        private int x, y;
        private float scale = 1;

        Entry(IRecipeCategory<T> category, T recipe, IFocusGroup group) {
            this.category = category;
            this.recipe = recipe;
            this.group = group;
        }

        private @Nullable IRecipeLayoutDrawable<T> layout() {
            if (layout == null && !failed) {
                layout = recipes.createRecipeLayoutDrawable(category, recipe, group, BACKGROUND, BORDER).orElse(null);
                failed = layout == null;
                if (layout != null) layout.setPosition(BORDER, BORDER);
            }
            return layout;
        }

        @Override
        public Object category() {
            return category.getRecipeType();
        }

        @Override
        public Component categoryTitle() {
            return category.getTitle();
        }

        @Override
        public void drawCategoryIcon(GuiGraphics g, int x, int y) {
            IDrawable icon = category.getIcon();
            if (icon != null) icon.draw(g, x + (16 - icon.getWidth()) / 2, y + (16 - icon.getHeight()) / 2);
            else g.renderItem(catalyst(category.getRecipeType()), x, y); // what JEI itself shows when a category has no icon
        }

        @Override
        public int width() {
            return category.getWidth() + 2 * BORDER;
        }

        @Override
        public int height() {
            return category.getHeight() + 2 * BORDER;
        }

        @Override
        public void draw(GuiGraphics g, int x, int y, float scale, int mouseX, int mouseY) {
            this.x = x;
            this.y = y;
            this.scale = scale;
            IRecipeLayoutDrawable<T> layout = layout();
            if (layout == null) return;
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(scale, scale, 1);
            layout.drawRecipe(g, localX(mouseX), localY(mouseY));
            g.pose().popPose();
        }

        @Override
        public ItemStack itemAt(int mouseX, int mouseY) {
            return layout == null ? ItemStack.EMPTY : layout.getItemStackUnderMouse(localX(mouseX), localY(mouseY)).orElse(ItemStack.EMPTY);
        }

        @Override
        public boolean drawTooltip(GuiGraphics g, int mouseX, int mouseY) {
            if (layout == null) return false;
            return layout.getSlotUnderMouse(localX(mouseX), localY(mouseY)).map(under -> {
                under.slot().drawTooltip(g, mouseX, mouseY);
                return true;
            }).orElse(false);
        }

        @Override
        public void tick() {
            if (layout != null) layout.tick();
        }

        // the layout is drawn at (0, 0) inside a translated and scaled pose, so the mouse is moved into that space
        private int localX(int mouseX) {
            return (int) ((mouseX - x) / scale);
        }

        private int localY(int mouseY) {
            return (int) ((mouseY - y) / scale);
        }
    }
}
