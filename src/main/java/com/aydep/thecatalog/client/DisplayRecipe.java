package com.aydep.thecatalog.client;

import com.aydep.thecatalog.TheCatalog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import org.jetbrains.annotations.Nullable;

/** A recipe laid out for a book page: item slots and pictures at fixed spots inside a width x height box. */
public final class DisplayRecipe {
    public static final int CAPTION = 11;
    private static final int S = BookLayout.SLOT;
    static final ResourceLocation ARROW = sprite("arrow"), FLAME = sprite("flame");

    /** A slot that cycles through its stacks once a second, e.g. "any planks". */
    public record Slot(int x, int y, List<ItemStack> stacks) {
        public ItemStack shown() {
            return stacks.isEmpty() ? ItemStack.EMPTY : stacks.get((int) (Util.getMillis() / ClientConfig.INGREDIENT_CYCLE_MILLIS.get() % stacks.size()));
        }
    }

    public record Picture(ResourceLocation sprite, int x, int y, int width, int height) {}

    public final Component caption;
    /** What this recipe is listed under as "recipes"; empty for ones listed only under "uses". */
    final List<ItemStack> results;
    public final List<Slot> inputs = new ArrayList<>();
    public final List<Picture> pictures = new ArrayList<>();
    public Slot output;
    private @Nullable Supplier<ItemStack> liveOutput;
    public @Nullable Component note;
    public int noteX, noteY, width, height;

    private DisplayRecipe(Component caption, List<ItemStack> results) {
        this.caption = caption;
        this.results = results;
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

    static @Nullable DisplayRecipe of(Recipe<?> recipe, RegistryAccess registries, List<ItemStack> allItems) {
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
        return simple(Items.BREWING_STAND.getDescription(), List.of(List.of(input), List.of(reagent)), output, List.of(output));
    }

    private static DisplayRecipe crafting(Component caption, List<List<ItemStack>> ingredients, ItemStack result, int shapedWidth) {
        int n = ingredients.size();
        int w = shapedWidth > 0 ? shapedWidth : n > 4 ? 3 : n > 1 ? 2 : 1;
        int h = Math.max(1, (n + w - 1) / w);
        DisplayRecipe d = new DisplayRecipe(caption, List.of(result));
        for (int i = 0; i < n; i++) {
            if (!ingredients.get(i).isEmpty()) d.inputs.add(new Slot(i % w * S, CAPTION + i / w * S, ingredients.get(i)));
        }
        int mid = CAPTION + (h - 1) * S / 2;
        d.pictures.add(new Picture(ARROW, 3 * S + 6, mid + 1, 22, 15));
        d.output = new Slot(3 * S + 34, mid, List.of(result));
        d.width = 3 * S + 34 + S;
        d.height = CAPTION + h * S;
        return d;
    }

    private static DisplayRecipe cooking(Component caption, List<ItemStack> input, ItemStack result, AbstractCookingRecipe cooking) {
        DisplayRecipe d = new DisplayRecipe(caption, List.of(result));
        d.inputs.add(new Slot(0, CAPTION, input));
        d.pictures.add(new Picture(FLAME, 2, CAPTION + S + 1, 14, 14));
        d.pictures.add(new Picture(ARROW, S + 6, CAPTION + 10, 22, 15));
        d.output = new Slot(S + 34, CAPTION + 9, List.of(result));
        d.note = Component.translatable("gui.thecatalog.cooking", number(cooking.getCookingTime() / 20f), number(cooking.getExperience()));
        d.noteX = S + 6;
        d.noteY = CAPTION + 2 * S + 1;
        d.width = S + 34 + S;
        d.height = CAPTION + 2 * S + 10;
        return d;
    }

    /** Inputs in rows of four, then an arrow and the output. Used for every recipe type without its own layout. */
    private static DisplayRecipe simple(Component caption, List<List<ItemStack>> inputs, ItemStack shownResult, List<ItemStack> results) {
        int cols = Math.clamp(inputs.size(), 1, 4);
        int rows = Math.max(1, (inputs.size() + 3) / 4);
        DisplayRecipe d = new DisplayRecipe(caption, results);
        for (int i = 0; i < inputs.size(); i++) d.inputs.add(new Slot(i % 4 * S, CAPTION + i / 4 * S, inputs.get(i)));
        int mid = CAPTION + (rows - 1) * S / 2;
        d.pictures.add(new Picture(ARROW, cols * S + 2, mid + 1, 22, 15));
        d.output = new Slot(cols * S + 28, mid, List.of(shownResult));
        d.width = cols * S + 28 + S;
        d.height = CAPTION + rows * S;
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
