package com.aydep.thecatalog.client;

import com.aydep.thecatalog.OreGeneration;
import com.aydep.thecatalog.OreGeneration.Data;
import com.aydep.thecatalog.OreGeneration.Spread;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Where an ore generates, drawn as a depth chart. The data comes from the server, which reads it from the world's
 * actual generation settings (so modded ores work without any changes). Files at
 * {@code assets/<mod>/thecatalog/worldgen/<item>.json} can override it: give just {@code color} (e.g. "#5FD3E0")
 * and/or {@code peak} to restyle it, or a full {@link Data} (dimension, biomes, spreads) for anything the server can't
 * see. A spread's {@code shape} is "triangle"/"trapezoid" (most common in the middle) or "uniform".
 */
public final class Generation implements RecipeEntry {
    /** Header row, gap below it, chart height, and the three value lines under the chart. */
    static final int HEADER = 18, GAP = 6, CHART = 80, LINES = 3;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final String FOLDER = "thecatalog/worldgen";
    private static volatile Map<Item, Data> fromServer = Map.of();
    private static volatile Map<Item, Data> fromFiles = Map.of();

    final ItemStack stack;
    final Data data;
    private final List<Spread> spreads;

    private Generation(ItemStack stack, Data data) {
        this.stack = stack;
        this.data = data;
        this.spreads = data.spreads();
    }

    /** The server's data for the item with any file overrides applied, or null if nothing is known. */
    static @Nullable Generation of(ItemStack stack) {
        Data server = fromServer.get(stack.getItem()), file = fromFiles.get(stack.getItem());
        if (file != null && file.spreads() != null && !file.spreads().isEmpty()) return new Generation(stack, file);
        if (server == null || server.spreads() == null || server.spreads().isEmpty()) return null;
        if (file == null) return new Generation(stack, server);
        return new Generation(stack, new Data(server.dimension(), server.biomes(), server.bottom(), server.top(),
                file.color() != null ? file.color() : server.color(), file.peak() != null ? file.peak() : server.peak(), server.spreads()));
    }

    /** Payload handler: the server's data, sent on join and after /reload. */
    public static void receive(String json) {
        Map<String, Data> data = GSON.fromJson(json, new TypeToken<Map<String, Data>>() {}.getType());
        fromServer = OreGeneration.byItem(data);
        LOGGER.info("The Catalog received generation data for {} ores", fromServer.size());
    }

    /** Called on every resource reload. */
    static void load(ResourceManager resources) {
        Map<Item, Data> found = new HashMap<>();
        resources.listResources(FOLDER, id -> id.getPath().endsWith(".json")).forEach((file, resource) -> {
            String path = file.getPath().substring(FOLDER.length() + 1, file.getPath().length() - ".json".length());
            ResourceLocation itemId = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path);
            try (Reader reader = resource.openAsReader()) {
                Data data = GSON.fromJson(reader, Data.class);
                BuiltInRegistries.ITEM.getOptional(itemId).ifPresent(item -> found.put(item, data));
            } catch (Exception e) { // one bad file must not break the rest
                LOGGER.warn("The Catalog could not read {}: {}", file, e.toString());
            }
        });
        fromFiles = found;
    }

    private boolean is(String dimension) {
        return dimension.equals(data.dimension());
    }

    /** Lowest and highest Y of the dimension. */
    int bottom() {
        return data.bottom() != null ? data.bottom() : is("minecraft:overworld") ? -64 : 0;
    }

    int top() {
        if (data.top() != null) return data.top();
        return is("minecraft:overworld") ? 319 : is("minecraft:the_nether") ? 127 : 255;
    }

    double density(int y) {
        double sum = 0;
        for (Spread spread : spreads) sum += spread.at(y);
        return sum;
    }

    /** Lowest and highest Y the ore actually appears at, within the dimension. */
    int low() {
        return Math.max(bottom(), spreads.stream().mapToInt(Spread::min).min().orElse(bottom()));
    }

    int high() {
        return Math.min(top(), spreads.stream().mapToInt(Spread::max).max().orElse(top()));
    }

    int peak() {
        if (data.peak() != null) return data.peak();
        int best = low();
        for (int y = low(); y <= high(); y++) if (density(y) > density(best)) best = y;
        return best;
    }

    /** The chart shows only the ore's own range plus a small margin, clamped to the dimension. */
    int chartTop() {
        return Math.min(top(), high() + margin());
    }

    int chartBottom() {
        return Math.max(bottom(), low() - margin());
    }

    private int margin() {
        return Math.max(2, (high() - low()) / 16);
    }

    int color() {
        try {
            return data.color() == null ? 0xFF8A6A4A : 0xFF000000 | Integer.parseInt(data.color().substring(1), 16);
        } catch (NumberFormatException e) {
            return 0xFF8A6A4A;
        }
    }

    /** Column colors: stone above Y 0 and deepslate below in the Overworld; netherrack or end stone elsewhere. */
    int rockColor(int y) {
        if (is("minecraft:the_nether")) return 0xFF8E4A40;
        if (is("minecraft:the_end")) return 0xFFE3DDA6;
        return y >= 0 ? 0xFFB5AC98 : 0xFF6F6A66;
    }

    ItemStack dimensionIcon() {
        return new ItemStack(is("minecraft:the_nether") ? Items.NETHERRACK : is("minecraft:the_end") ? Items.END_STONE
                : is("minecraft:overworld") ? Items.GRASS_BLOCK : Items.COMPASS);
    }

    /** "Overworld · All biomes"; modded dimensions without a translation get a tidied-up id. */
    Component where() {
        ResourceLocation dim = ResourceLocation.parse(data.dimension());
        String key = "gui.thecatalog.dimension." + dim.getPath();
        Component dimension = I18n.exists(key) ? Component.translatable(key) : Component.literal(title(dim.getPath()));
        List<String> biomes = data.biomes();
        Component where;
        if (biomes == null || biomes.isEmpty()) where = Component.translatable("gui.thecatalog.all_biomes");
        else if (biomes.size() > 3) where = Component.translatable("gui.thecatalog.n_biomes", biomes.size());
        else where = Component.literal(String.join(", ", biomes.stream().map(Generation::biomeName).toList()));
        return dimension.copy().append(" · ").append(where);
    }

    /** The biome list for the header's tooltip when it is summarized as "N biomes". */
    @Nullable Component biomeList() {
        List<String> biomes = data.biomes();
        if (biomes == null || biomes.size() <= 3) return null;
        return Component.literal(String.join(", ", biomes.stream().map(Generation::biomeName).toList()));
    }

    private static String biomeName(String id) {
        ResourceLocation biome = ResourceLocation.parse(id);
        String key = "biome." + biome.getNamespace() + "." + biome.getPath();
        return I18n.exists(key) ? I18n.get(key) : title(biome.getPath());
    }

    private static String title(String path) {
        StringBuilder out = new StringBuilder();
        for (String word : path.split("[_/]")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }

    @Override
    public int pageHeight(int width) {
        return HEADER + GAP + CHART + 4 + LINES * 10;
    }

    @Override
    public Object category() {
        return InfoEntry.WORLDGEN;
    }

    @Override
    public Component categoryTitle() {
        return Component.translatable("gui.thecatalog.worldgen");
    }

    @Override
    public void drawCategoryIcon(GuiGraphics g, int x, int y) {
        g.renderItem(new ItemStack(Items.IRON_PICKAXE), x, y);
    }
}
