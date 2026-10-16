package com.aydep.thecatalog.client;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
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
 * Where an ore generates, drawn as a depth chart. Data comes from {@code assets/<mod>/thecatalog/worldgen/<item>.json}:
 * <pre>{"dimension": "minecraft:overworld", "biomes": ["minecraft:badlands"], "color": "#5FD3E0", "peak": -59,
 *  "spreads": [{"shape": "triangle", "min": -144, "max": 16, "weight": 11}]}</pre>
 * {@code biomes} left out means every biome; {@code shape} is "triangle" (most common in the middle of min..max, like
 * vanilla's trapezoid height providers) or "uniform"; {@code weight} is roughly how many veins that spread places.
 */
public final class Generation implements RecipeEntry {
    public record Spread(String shape, int min, int max, double weight) {
        double at(int y) {
            if (y < min || y > max) return 0;
            if (!"triangle".equals(shape)) return weight;
            double middle = (min + max) / 2.0, half = (max - min) / 2.0;
            return weight * (1 - Math.abs(y - middle) / half);
        }
    }

    public record Data(String dimension, @Nullable List<String> biomes, @Nullable String color, @Nullable Integer peak, List<Spread> spreads) {}

    static final int HEADER = 18, CHART = 80, FOOTER = 12;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final String FOLDER = "thecatalog/worldgen";
    private static volatile Map<Item, Data> loaded = Map.of();

    final ItemStack stack;
    final Data data;

    private Generation(ItemStack stack, Data data) {
        this.stack = stack;
        this.data = data;
    }

    static @Nullable Generation of(ItemStack stack) {
        Data data = loaded.get(stack.getItem());
        return data == null ? null : new Generation(stack, data);
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
        loaded = found;
    }

    /** Lowest and highest Y of the dimension. */
    int bottom() {
        return data.dimension().equals("minecraft:overworld") ? -64 : 0;
    }

    int top() {
        return switch (data.dimension()) {
            case "minecraft:overworld" -> 320;
            case "minecraft:the_nether" -> 128;
            default -> 256;
        };
    }

    double density(int y) {
        double sum = 0;
        for (Spread spread : data.spreads()) sum += spread.at(y);
        return sum;
    }

    /** Lowest and highest Y the ore actually appears at, within the dimension. */
    int low() {
        return Math.max(bottom(), data.spreads().stream().mapToInt(Spread::min).min().orElse(bottom()));
    }

    int high() {
        return Math.min(top(), data.spreads().stream().mapToInt(Spread::max).max().orElse(top()));
    }

    int peak() {
        if (data.peak() != null) return data.peak();
        int best = low();
        for (int y = low(); y <= high(); y++) if (density(y) > density(best)) best = y;
        return best;
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
        return switch (data.dimension()) {
            case "minecraft:overworld" -> y >= 0 ? 0xFFB5AC98 : 0xFF6F6A66;
            case "minecraft:the_nether" -> 0xFF8E4A40;
            default -> 0xFFE3DDA6;
        };
    }

    ItemStack dimensionIcon() {
        return new ItemStack(switch (data.dimension()) {
            case "minecraft:overworld" -> Items.GRASS_BLOCK;
            case "minecraft:the_nether" -> Items.NETHERRACK;
            default -> Items.END_STONE;
        });
    }

    Component where() {
        ResourceLocation dim = ResourceLocation.parse(data.dimension());
        Component biomes = data.biomes() == null || data.biomes().isEmpty()
                ? Component.translatable("gui.thecatalog.all_biomes")
                : Component.literal(String.join(", ", data.biomes().stream().map(b -> {
                    ResourceLocation id = ResourceLocation.parse(b);
                    return Component.translatable("biome." + id.getNamespace() + "." + id.getPath()).getString();
                }).toList()));
        return Component.translatable("gui.thecatalog.dimension." + dim.getPath()).append(" · ").append(biomes);
    }

    @Override
    public int pageHeight(int width) {
        return HEADER + CHART + FOOTER;
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
