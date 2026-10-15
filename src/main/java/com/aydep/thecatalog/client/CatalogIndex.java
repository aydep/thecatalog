package com.aydep.thecatalog.client;

import com.aydep.thecatalog.TheCatalog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** Everything the book shows: the tabs with their items and all recipes, built from data the server synced to the client. */
public final class CatalogIndex {
    public record Tab(Component name, ItemStack icon, List<ItemStack> items) {}

    private static @Nullable CatalogIndex instance;
    private static final Map<String, String> MOD_NAMES = new HashMap<>();

    public final List<Tab> tabs = new ArrayList<>();
    private final Map<Item, List<DisplayRecipe>> byResult = new HashMap<>();
    private final Map<Item, List<DisplayRecipe>> byInput = new HashMap<>();

    public static CatalogIndex get() {
        if (instance == null) instance = new CatalogIndex(Minecraft.getInstance());
        return instance;
    }

    /** Called when recipes or tags change; the index is rebuilt the next time the book opens. */
    public static void invalidate() {
        instance = null;
    }

    private CatalogIndex(Minecraft mc) {
        ClientLevel level = mc.level;
        RegistryAccess registries = level.registryAccess();
        boolean opItems = mc.player.canUseGameMasterBlocks() && mc.options.operatorItemsTab().get();
        CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), opItems, registries);
        List<ItemStack> all = List.copyOf(CreativeModeTabs.searchTab().getDisplayItems());
        addTabs(all);
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) add(DisplayRecipe.of(holder.value(), registries, all));
        addBrewing(level.potionBrewing(), all);
    }

    /** "All", then the vanilla creative tabs, then one tab per mod sorted by name. */
    private void addTabs(List<ItemStack> all) {
        tabs.add(new Tab(Component.translatable("gui.thecatalog.all"), new ItemStack(TheCatalog.CATALOG.get()), all));
        Map<String, ItemStack> modIcons = new HashMap<>();
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
            ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (tab.getType() != CreativeModeTab.Type.CATEGORY || id == null) continue;
            if (!id.getNamespace().equals("minecraft")) {
                modIcons.putIfAbsent(id.getNamespace(), tab.getIconItem());
            } else if (!id.equals(CreativeModeTabs.OP_BLOCKS.location()) && !tab.getDisplayItems().isEmpty()) {
                tabs.add(new Tab(tab.getDisplayName(), tab.getIconItem(), List.copyOf(tab.getDisplayItems())));
            }
        }
        Map<String, List<ItemStack>> byMod = new TreeMap<>();
        for (ItemStack stack : all) {
            String mod = modId(stack);
            if (!mod.equals("minecraft")) byMod.computeIfAbsent(modName(mod), k -> new ArrayList<>()).add(stack);
        }
        byMod.forEach((name, items) -> tabs.add(new Tab(Component.literal(name), modIcons.getOrDefault(modId(items.get(0)), items.get(0)), items)));
    }

    private void addBrewing(PotionBrewing brewing, List<ItemStack> all) {
        List<ItemStack> reagents = all.stream().filter(brewing::isIngredient).toList();
        for (ItemStack input : all) {
            if (!brewing.isInput(input)) continue;
            for (ItemStack reagent : reagents) {
                ItemStack output = brewing.mix(reagent, input);
                if (!output.isEmpty() && !ItemStack.matches(output, input)) add(DisplayRecipe.brewing(input, reagent, output));
            }
        }
    }

    private void add(@Nullable DisplayRecipe recipe) {
        if (recipe == null) return;
        for (ItemStack result : recipe.results) byResult.computeIfAbsent(result.getItem(), k -> new ArrayList<>()).add(recipe);
        Set<Item> seen = new HashSet<>();
        for (DisplayRecipe.Slot slot : recipe.inputs) {
            for (ItemStack stack : slot.stacks()) {
                if (seen.add(stack.getItem())) byInput.computeIfAbsent(stack.getItem(), k -> new ArrayList<>()).add(recipe);
            }
        }
    }

    public List<DisplayRecipe> recipesFor(ItemStack stack) {
        return byResult.getOrDefault(stack.getItem(), List.of()).stream().filter(r -> r.produces(stack)).toList();
    }

    public List<DisplayRecipe> usesFor(ItemStack stack) {
        return byInput.getOrDefault(stack.getItem(), List.of()).stream().filter(r -> r.uses(stack)).toList();
    }

    public static String modId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
    }

    public static String modName(String modId) {
        return MOD_NAMES.computeIfAbsent(modId,
                id -> ModList.get().getModContainerById(id).map(c -> c.getModInfo().getDisplayName()).orElse(id));
    }
}
