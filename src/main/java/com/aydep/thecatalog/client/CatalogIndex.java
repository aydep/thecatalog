package com.aydep.thecatalog.client;

import com.aydep.thecatalog.TheCatalog;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Everything the book shows: the tabs with their items and all recipes, built from data the server synced to the client.
 * Built on a background thread whenever recipes or tags change, so opening the book never freezes the game.
 */
public final class CatalogIndex {
    /** {@code names} holds each item's lowercase search text (see {@link #searchText}), parallel to {@code items}. */
    public record Tab(Component name, ItemStack icon, List<ItemStack> items, List<String> names) {}

    /** Search always matches item names in these languages too, whatever language the game is set to. */
    private static final List<String> SEARCH_LANGUAGES = List.of("en_us", "ru_ru");
    private static volatile List<Language> searchLanguages = List.of();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, String> MOD_NAMES = new ConcurrentHashMap<>();
    private static volatile @Nullable CatalogIndex current;
    private static volatile boolean dirty;
    // main thread only: which build is the newest, and whether one is running
    private static int generation;
    private static boolean building;

    public final List<Tab> tabs = new ArrayList<>();
    /** The Natural Blocks tab: found in the world as they are, so recipe trees don't break them down. */
    public final Set<Item> natural = new HashSet<>();
    private final Map<Item, List<DisplayRecipe>> byResult = new HashMap<>();
    private final Map<Item, List<DisplayRecipe>> byInput = new HashMap<>();
    private final Map<Item, List<DisplayRecipe>> tradesByResult = new HashMap<>();
    private final Map<Item, List<DisplayRecipe>> tradesByCost = new HashMap<>();
    private int recipeCount;

    /** The latest finished index, or null while the first one is still being built. */
    public static @Nullable CatalogIndex current() {
        return current;
    }

    /** Loads the search languages; called on every resource reload, which includes changing the game's language. */
    static void loadSearchLanguages(ResourceManager resources) {
        searchLanguages = SEARCH_LANGUAGES.stream()
                .map(code -> (Language) ClientLanguage.loadFrom(resources, List.of("en_us", code), false)) // en_us fills gaps, as in vanilla
                .toList();
    }

    /** Recipes or tags changed (any thread); a rebuild starts on the next client tick. The old index stays usable until then. */
    public static void markDirty() {
        dirty = true;
    }

    /** Asks for a build if there is no index and none is running, e.g. when the book opens before any sync event. */
    public static void requestIfMissing() {
        if (current == null && !building) dirty = true;
    }

    /** Leaving the world: drop the index and ignore any build still running. */
    public static void clear() {
        current = null;
        dirty = false;
        building = false;
        generation++;
    }

    /** Called every client tick. Snapshots what must be read on the main thread, then builds in the background. */
    static void tick(Minecraft mc) {
        if (!dirty || mc.level == null || mc.player == null) return;
        dirty = false;
        building = true;
        int build = ++generation;
        long start = Util.getMillis();
        ClientLevel level = mc.level;
        RegistryAccess registries = level.registryAccess();
        // Creative tab contents are built on the main thread, as the creative screen does: mods fill them through events.
        boolean opItems = mc.player.canUseGameMasterBlocks() && mc.options.operatorItemsTab().get();
        CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), opItems, registries);
        List<ItemStack> all = List.copyOf(CreativeModeTabs.searchTab().getDisplayItems());
        List<CreativeModeTab> creativeTabs = CreativeModeTabs.tabs();
        Map<CreativeModeTab, List<ItemStack>> tabItems = new HashMap<>();
        for (CreativeModeTab tab : creativeTabs) {
            tab.getIconItem(); // icons are created lazily by mod code; do it here, not on the worker thread
            tabItems.put(tab, List.copyOf(tab.getDisplayItems()));
        }
        List<RecipeHolder<?>> recipes = List.copyOf(level.getRecipeManager().getRecipes());
        PotionBrewing brewing = level.potionBrewing();
        List<DisplayRecipe> trades = collectTrades(level); // needs trader entities, so it runs here on the main thread

        CompletableFuture.supplyAsync(() -> new CatalogIndex(all, creativeTabs, tabItems, recipes, brewing, registries, trades), Util.backgroundExecutor())
                .whenCompleteAsync((index, error) -> {
                    if (build != generation) return; // a newer build started, or the player left
                    building = false;
                    if (error != null) {
                        LOGGER.error("The Catalog failed to index recipes", error);
                        return;
                    }
                    current = index;
                    LOGGER.info("The Catalog indexed {} items and {} recipes in {} ms", all.size(), index.recipeCount, Util.getMillis() - start);
                }, mc);
    }

    private CatalogIndex(List<ItemStack> all, List<CreativeModeTab> creativeTabs, Map<CreativeModeTab, List<ItemStack>> tabItems,
                         Collection<RecipeHolder<?>> recipes, PotionBrewing brewing, RegistryAccess registries, List<DisplayRecipe> trades) {
        addTabs(all, creativeTabs, tabItems);
        tabItems.getOrDefault(BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.NATURAL_BLOCKS), List.of()).forEach(s -> natural.add(s.getItem()));
        for (RecipeHolder<?> holder : recipes) {
            try {
                add(DisplayRecipe.of(holder, registries, all));
            } catch (RuntimeException e) { // one broken modded recipe must not cost the whole book
                LOGGER.warn("The Catalog skipped recipe {}: {}", holder.id(), e.toString());
            }
        }
        addBrewing(brewing, all);
        for (DisplayRecipe trade : trades) {
            for (ItemStack result : trade.results) tradesByResult.computeIfAbsent(result.getItem(), k -> new ArrayList<>()).add(trade);
            for (DisplayRecipe.Slot cost : trade.inputs) {
                for (ItemStack stack : cost.stacks()) tradesByCost.computeIfAbsent(stack.getItem(), k -> new ArrayList<>()).add(trade);
            }
        }
    }

    /**
     * Every villager and wandering trader offer, sampled once with a fixed seed (some offers are randomized).
     * Offers that need a server (treasure maps) or fail in a modded listing are skipped.
     */
    private static List<DisplayRecipe> collectTrades(ClientLevel level) {
        List<DisplayRecipe> trades = new ArrayList<>();
        RandomSource random = RandomSource.create(0);
        Villager villager = new Villager(EntityType.VILLAGER, level);
        VillagerTrades.TRADES.forEach((profession, levels) -> {
            ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
            for (int lvl : levels.keySet().toIntArray()) {
                Component who = Component.translatable("entity." + id.getNamespace() + ".villager." + id.getPath())
                        .append(" · ").append(Component.translatable("merchant.level." + lvl));
                for (VillagerTrades.ItemListing listing : levels.get(lvl)) addTrade(trades, listing, villager, random, who);
            }
        });
        WanderingTrader wanderer = new WanderingTrader(EntityType.WANDERING_TRADER, level);
        for (VillagerTrades.ItemListing[] listings : VillagerTrades.WANDERING_TRADER_TRADES.values()) {
            for (VillagerTrades.ItemListing listing : listings) addTrade(trades, listing, wanderer, random, EntityType.WANDERING_TRADER.getDescription());
        }
        return trades;
    }

    private static void addTrade(List<DisplayRecipe> trades, VillagerTrades.ItemListing listing, Entity trader, RandomSource random, Component who) {
        try {
            MerchantOffer offer = listing.getOffer(trader, random);
            if (offer != null && !offer.getResult().isEmpty()) trades.add(DisplayRecipe.trade(offer, who));
        } catch (RuntimeException e) {
            LOGGER.debug("The Catalog skipped a trade: {}", e.toString());
        }
    }

    /** "All", then the vanilla creative tabs, then one tab per mod sorted by name. */
    private void addTabs(List<ItemStack> all, List<CreativeModeTab> creativeTabs, Map<CreativeModeTab, List<ItemStack>> tabItems) {
        tabs.add(tab(Component.translatable("gui.thecatalog.all"), new ItemStack(TheCatalog.CATALOG.get()), all));
        Map<String, ItemStack> modIcons = new HashMap<>();
        for (CreativeModeTab tab : creativeTabs) {
            ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (tab.getType() != CreativeModeTab.Type.CATEGORY || id == null) continue;
            List<ItemStack> items = tabItems.get(tab);
            if (!id.getNamespace().equals("minecraft")) {
                modIcons.putIfAbsent(id.getNamespace(), tab.getIconItem());
            } else if (!id.equals(CreativeModeTabs.OP_BLOCKS.location()) && !items.isEmpty()) {
                tabs.add(tab(tab.getDisplayName(), tab.getIconItem(), items));
            }
        }
        Map<String, List<ItemStack>> byMod = new TreeMap<>();
        for (ItemStack stack : all) {
            String mod = modId(stack);
            if (!mod.equals("minecraft")) byMod.computeIfAbsent(modName(mod), k -> new ArrayList<>()).add(stack);
        }
        byMod.forEach((name, items) -> tabs.add(tab(Component.literal(name), modIcons.getOrDefault(modId(items.get(0)), items.get(0)), items)));
    }

    private static Tab tab(Component name, ItemStack icon, List<ItemStack> items) {
        List<Language> languages = searchLanguages;
        return new Tab(name, icon, items, items.stream().map(s -> searchText(s, languages)).toList());
    }

    /** The item's displayed name (custom names included) plus its name in each search language, lowercase, one per line. */
    static String searchText(ItemStack stack, List<Language> languages) {
        StringBuilder text = new StringBuilder(stack.getHoverName().getString());
        for (Language language : languages) text.append('\n').append(language.getOrDefault(stack.getDescriptionId()));
        return text.toString().toLowerCase(Locale.ROOT);
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
        recipeCount++;
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

    /** Trades that give the stack, or (uses) that take it as payment. */
    public List<DisplayRecipe> tradesFor(ItemStack stack, boolean uses) {
        Map<Item, List<DisplayRecipe>> map = uses ? tradesByCost : tradesByResult;
        return map.getOrDefault(stack.getItem(), List.of()).stream().filter(t -> uses ? t.uses(stack) : t.produces(stack)).toList();
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
