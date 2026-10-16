package com.aydep.thecatalog.client;

import com.aydep.thecatalog.TheCatalog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;

/** A recipe laid out for a book page: item slots and pictures at fixed spots inside a width x height box. */
public final class DisplayRecipe implements RecipeEntry {
    /** Space above the recipe panel. (The category name is on the bookmark, not above each recipe.) */
    public static final int CAPTION = 2;
    private static final ResourceLocation BREWING = ResourceLocation.withDefaultNamespace("brewing");
    private static final ResourceLocation TRADING = ResourceLocation.fromNamespaceAndPath(TheCatalog.MODID, "trading");
    private static final int S = BookLayout.SLOT;
    /** Padding between the input panel's edge and its slots. */
    public static final int PAD = 3;
    /** From the panel's right edge: brace at +1, chevron at +7, output frame at +16 (24px wide), output slot at +19. */
    public static final int OUTPUT_FROM_PANEL = 19, AFTER_PANEL = 40;
    static final ResourceLocation FLAME = sprite("flame");

    /** A slot that cycles through its stacks once a second, e.g. "any planks". */
    public record Slot(int x, int y, List<ItemStack> stacks) {
        public ItemStack shown() {
            return stacks.isEmpty() ? ItemStack.EMPTY : stacks.get((int) (Util.getMillis() / ClientConfig.INGREDIENT_CYCLE_MILLIS.get() % stacks.size()));
        }
    }

    public record Picture(ResourceLocation sprite, int x, int y, int width, int height) {}

    public final Component caption;
    private @Nullable ResourceLocation categoryId;
    private @Nullable RecipeHolder<?> holder;
    private ItemStack icon = ItemStack.EMPTY;
    /** What this recipe is listed under as "recipes"; empty for ones listed only under "uses". */
    final List<ItemStack> results;
    public final List<Slot> inputs = new ArrayList<>();
    public final List<Picture> pictures = new ArrayList<>();
    public Slot output;
    private @Nullable Supplier<ItemStack> liveOutput;
    public @Nullable Component note;
    public int noteX, noteY, panelWidth, panelHeight, height;

    private DisplayRecipe(Component caption, List<ItemStack> results) {
        this.caption = caption;
        this.results = results;
    }

    @Override
    public int pageHeight(int width) {
        return height;
    }

    @Override
    public Object category() {
        return categoryId != null ? categoryId : caption.getString();
    }

    @Override
    public Component categoryTitle() {
        return caption;
    }

    @Override
    public @Nullable RecipeHolder<?> holder() {
        return holder;
    }

    @Override
    public void drawCategoryIcon(GuiGraphics g, int x, int y) {
        g.renderItem(icon, x, y);
    }

    public ItemStack shownOutput() {
        return liveOutput != null ? liveOutput.get() : output.shown();
    }

    public boolean produces(ItemStack stack) {
        return results.stream().anyMatch(r -> sameKind(r, stack));
    }

    public boolean uses(ItemStack stack) {
        return inputs.stream().anyMatch(slot -> slot.stacks().stream().anyMatch(s -> sameKind(s, stack)));
    }

    /** A plain recipe stack stands for every variant of its item; one with components (a potion) only for itself. */
    private static boolean sameKind(ItemStack recipeStack, ItemStack stack) {
        return recipeStack.is(stack.getItem())
                && (recipeStack.getComponentsPatch().isEmpty() || ItemStack.isSameItemSameComponents(recipeStack, stack));
    }

    static @Nullable DisplayRecipe of(RecipeHolder<?> holder, RegistryAccess registries, List<ItemStack> allItems) {
        Recipe<?> recipe = holder.value();
        DisplayRecipe d = layout(recipe, registries, allItems);
        if (d == null) return null;
        d.holder = holder;
        d.categoryId = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        ItemStack station = recipe.getToastSymbol();
        // types that don't name their station report a crafting table; show what they make instead
        d.icon = recipe.getType() != RecipeType.CRAFTING && station.is(Items.CRAFTING_TABLE) ? d.output.shown() : station;
        return d;
    }

    private static @Nullable DisplayRecipe layout(Recipe<?> recipe, RegistryAccess registries, List<ItemStack> allItems) {
        if (recipe instanceof SmithingRecipe smithing) return smithing(smithing, registries, allItems);
        ItemStack result = recipe.getResultItem(registries);
        if (result.isEmpty()) return null; // special recipes (map cloning, fireworks...) have no fixed result
        List<List<ItemStack>> ingredients = recipe.getIngredients().stream().map(i -> Arrays.asList(i.getItems())).toList();
        Component caption = caption(recipe);
        if (recipe instanceof AbstractCookingRecipe cooking && !ingredients.isEmpty()) return cooking(caption, ingredients.get(0), result, cooking);
        if (recipe.getType() == RecipeType.CRAFTING) {
            return crafting(caption, ingredients, result, recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 0);
        }
        return simple(caption, ingredients.stream().filter(l -> !l.isEmpty()).toList(), result, List.of(result));
    }

    static DisplayRecipe brewing(ItemStack input, ItemStack reagent, ItemStack output) {
        DisplayRecipe d = simple(Items.BREWING_STAND.getDescription(), List.of(List.of(input), List.of(reagent)), output, List.of(output));
        d.categoryId = BREWING;
        d.icon = new ItemStack(Items.BREWING_STAND);
        return d;
    }

    /** A villager or wandering trader offer: costs on the left, the result on the right, who sells it below. */
    static DisplayRecipe trade(MerchantOffer offer, Component trader) {
        List<List<ItemStack>> costs = new ArrayList<>();
        costs.add(List.of(offer.getCostA()));
        if (!offer.getCostB().isEmpty()) costs.add(List.of(offer.getCostB()));
        ItemStack result = offer.getResult();
        DisplayRecipe d = grid(Component.translatable("gui.thecatalog.trading"), costs, costs.size(), result, List.of(result));
        d.categoryId = TRADING;
        d.icon = new ItemStack(Items.EMERALD);
        d.note = trader;
        d.noteX = 0;
        d.noteY = d.height + 2;
        d.height += 11;
        return d;
    }

    private static DisplayRecipe crafting(Component caption, List<List<ItemStack>> ingredients, ItemStack result, int shapedWidth) {
        int n = ingredients.size();
        int cols = shapedWidth > 0 ? shapedWidth : n > 4 ? 3 : n > 1 ? 2 : 1;
        List<List<ItemStack>> cells = new ArrayList<>(ingredients);
        while (cells.size() % cols != 0) cells.add(List.of()); // complete the last row so the grid is a rectangle
        return grid(caption, cells, cols, result, List.of(result));
    }

    private static DisplayRecipe cooking(Component caption, List<ItemStack> input, ItemStack result, AbstractCookingRecipe cooking) {
        DisplayRecipe d = grid(caption, List.of(input), 1, result, List.of(result));
        d.pictures.add(new Picture(FLAME, PAD + 2, d.height + 2, 14, 14)); // under the input panel
        d.note = Component.translatable("gui.thecatalog.cooking", number(cooking.getCookingTime() / 20f), number(cooking.getExperience()));
        d.noteX = PAD + 20;
        d.noteY = d.height + 5;
        d.height += 16;
        return d;
    }

    /** Inputs in rows of four. Used for every recipe type without its own layout. */
    private static DisplayRecipe simple(Component caption, List<List<ItemStack>> inputs, ItemStack shownResult, List<ItemStack> results) {
        return grid(caption, inputs, Math.clamp(inputs.size(), 1, 4), shownResult, results);
    }

    /** Input slots in a panel, then (drawn by the screen) a brace, a chevron and the output in a frame, centered on the grid. */
    private static DisplayRecipe grid(Component caption, List<List<ItemStack>> inputs, int cols, ItemStack shownResult, List<ItemStack> results) {
        int rows = Math.max(1, (inputs.size() + cols - 1) / cols);
        DisplayRecipe d = new DisplayRecipe(caption, results);
        for (int i = 0; i < inputs.size(); i++) d.inputs.add(new Slot(PAD + i % cols * S, CAPTION + PAD + i / cols * S, inputs.get(i)));
        d.panelWidth = cols * S + 2 * PAD;
        d.panelHeight = rows * S + 2 * PAD;
        d.output = new Slot(d.panelWidth + OUTPUT_FROM_PANEL, CAPTION + PAD + (rows - 1) * S / 2, List.of(shownResult));
        d.height = CAPTION + d.panelHeight;
        return d;
    }

    private static @Nullable DisplayRecipe smithing(SmithingRecipe recipe, RegistryAccess registries, List<ItemStack> allItems) {
        // Smithing recipes hide their ingredients, so find them by testing every item.
        List<ItemStack> template = filter(allItems, recipe::isTemplateIngredient);
        List<ItemStack> base = filter(allItems, recipe::isBaseIngredient);
        List<ItemStack> addition = filter(allItems, recipe::isAdditionIngredient);
        if (template.isEmpty() || base.isEmpty() || addition.isEmpty()) return null;
        ItemStack result = recipe.getResultItem(registries);
        // A trim fits every armor piece, so trims are listed only under "uses", not as a recipe of each armor.
        DisplayRecipe d = simple(caption(recipe), List.of(template, base, addition), result,
                recipe instanceof SmithingTrimRecipe ? List.of() : List.of(result));
        d.liveOutput = () -> recipe.assemble(
                new SmithingRecipeInput(d.inputs.get(0).shown(), d.inputs.get(1).shown(), d.inputs.get(2).shown()), registries);
        return d;
    }

    private static List<ItemStack> filter(List<ItemStack> items, Predicate<ItemStack> test) {
        return items.stream().filter(test).toList();
    }

    private static Component caption(Recipe<?> recipe) {
        ItemStack station = recipe.getToastSymbol();
        // Types that don't override getToastSymbol report a crafting table, so name those by their type id instead.
        if (recipe.getType() != RecipeType.CRAFTING && station.is(Items.CRAFTING_TABLE)) {
            ResourceLocation id = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            if (id == null) return station.getHoverName();
            return Component.literal(title(id.getPath()) + " (" + CatalogIndex.modName(id.getNamespace()) + ")");
        }
        return station.getHoverName();
    }

    /** "sequenced_assembly" -> "Sequenced Assembly". */
    private static String title(String path) {
        StringBuilder out = new StringBuilder();
        for (String word : path.split("[_/]")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }

    private static String number(float value) {
        return value == (int) value ? String.valueOf((int) value) : String.valueOf(value);
    }

    static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath(TheCatalog.MODID, name);
    }
}
