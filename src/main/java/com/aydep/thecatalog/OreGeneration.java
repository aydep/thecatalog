package com.aydep.thecatalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import org.jetbrains.annotations.Nullable;

/**
 * Where ores generate. The server reads it from its world-generation registries (vanilla, datapacks and every mod's
 * ores, after biome modifiers) and sends it to players; the client draws it as a depth chart.
 */
public final class OreGeneration {
    /** A height spread: "triangle"/"trapezoid" (most common in the middle, flat for {@code plateau} blocks) or "uniform". */
    public record Spread(String shape, int min, int max, double weight, int plateau) {
        public double at(int y) {
            if (y < min || y > max) return 0;
            if ("uniform".equals(shape)) return weight;
            double middle = (min + max) / 2.0, slope = (max - min - plateau) / 2.0;
            double distance = Math.abs(y - middle) - plateau / 2.0;
            return distance <= 0 || slope <= 0 ? weight : weight * Math.max(0, 1 - distance / slope);
        }
    }

    /**
     * {@code biomes} null means every biome of the dimension; {@code bottom}/{@code top} are the dimension's height
     * limits; {@code color} and {@code peak} are optional display overrides.
     */
    public record Data(String dimension, @Nullable List<String> biomes, @Nullable Integer bottom, @Nullable Integer top,
                       @Nullable String color, @Nullable Integer peak, @Nullable List<Spread> spreads) {}

    private OreGeneration() {}

    /** Every ore item's generation, keyed by item id, from the server's registries. */
    public static Map<String, Data> scan(MinecraftServer server) {
        Map<Item, Map<String, Accumulator>> byItem = new LinkedHashMap<>();
        for (Map.Entry<ResourceKey<LevelStem>, LevelStem> stem : server.registryAccess().registryOrThrow(Registries.LEVEL_STEM).entrySet()) {
            String dimension = stem.getKey().location().toString();
            DimensionType type = stem.getValue().type().value();
            Set<Holder<Biome>> biomes = stem.getValue().generator().getBiomeSource().possibleBiomes();
            // which biomes of this dimension place each feature
            Map<PlacedFeature, Set<String>> usedIn = new IdentityHashMap<>();
            for (Holder<Biome> biome : biomes) {
                String biomeId = biome.unwrapKey().map(k -> k.location().toString()).orElse("?");
                for (HolderSet<PlacedFeature> step : biome.value().getGenerationSettings().features()) {
                    for (Holder<PlacedFeature> feature : step) usedIn.computeIfAbsent(feature.value(), k -> new TreeSet<>()).add(biomeId);
                }
            }
            usedIn.forEach((placed, inBiomes) -> {
                if (!(placed.feature().value().config() instanceof OreConfiguration ore)) return;
                Spread spread = spread(placed.placement(), type, ore.size);
                if (spread == null) return;
                for (OreConfiguration.TargetBlockState target : ore.targetStates) {
                    Item item = target.state.getBlock().asItem();
                    if (item == Items.AIR) continue;
                    byItem.computeIfAbsent(item, k -> new LinkedHashMap<>())
                            .computeIfAbsent(dimension, k -> new Accumulator(type, biomes.size()))
                            .add(spread, inBiomes);
                }
            });
        }
        Map<String, Data> result = new LinkedHashMap<>();
        byItem.forEach((item, dims) -> {
            // an ore placed in several dimensions is shown for the one where it is most plentiful
            Map.Entry<String, Accumulator> main = dims.entrySet().stream()
                    .max((a, b) -> Double.compare(a.getValue().weight(), b.getValue().weight())).orElseThrow();
            result.put(BuiltInRegistries.ITEM.getKey(item).toString(), main.getValue().data(main.getKey()));
        });
        return result;
    }

    private static final class Accumulator {
        final DimensionType type;
        final int biomesInDimension;
        final List<Spread> spreads = new ArrayList<>();
        final Set<String> biomes = new TreeSet<>();
        boolean everywhere;

        Accumulator(DimensionType type, int biomesInDimension) {
            this.type = type;
            this.biomesInDimension = biomesInDimension;
        }

        void add(Spread spread, Set<String> inBiomes) {
            spreads.add(spread);
            biomes.addAll(inBiomes);
            everywhere |= inBiomes.size() >= biomesInDimension;
        }

        double weight() {
            return spreads.stream().mapToDouble(Spread::weight).sum();
        }

        Data data(String dimension) {
            List<String> listed = everywhere || biomes.size() >= biomesInDimension ? null : List.copyOf(biomes);
            return new Data(dimension, listed, type.minY(), type.minY() + type.height() - 1, null, null, List.copyOf(spreads));
        }
    }

    /** The height spread of a placed ore, read from its placement modifiers; null if it has no height range. */
    private static @Nullable Spread spread(List<PlacementModifier> modifiers, DimensionType type, int veinSize) {
        double count = 1, chance = 1;
        Spread height = null;
        for (PlacementModifier modifier : modifiers) {
            // the modifiers keep their values private, so read them through their codecs
            JsonElement json = PlacementModifier.CODEC.encodeStart(JsonOps.INSTANCE, modifier).result().orElse(null);
            if (json == null || !json.isJsonObject() || !json.getAsJsonObject().has("type")) continue;
            JsonObject o = json.getAsJsonObject();
            switch (o.get("type").getAsString()) {
                case "minecraft:count" -> count = average(o.get("count"));
                case "minecraft:rarity_filter" -> chance = o.get("chance").getAsDouble();
                case "minecraft:height_range" -> height = height(o.get("height"), type);
                default -> { }
            }
        }
        if (height == null) return null;
        return new Spread(height.shape(), height.min(), height.max(), count / chance * veinSize, height.plateau());
    }

    private static @Nullable Spread height(JsonElement json, DimensionType type) {
        if (!json.isJsonObject()) return null;
        JsonObject o = json.getAsJsonObject();
        if (!o.has("type")) { // a constant height is written as a bare anchor
            int y = anchor(o, type);
            return new Spread("uniform", y, y, 1, 0);
        }
        int min = anchor(o.get("min_inclusive").getAsJsonObject(), type), max = anchor(o.get("max_inclusive").getAsJsonObject(), type);
        return switch (o.get("type").getAsString()) {
            case "minecraft:trapezoid" -> new Spread("trapezoid", min, max, 1, o.has("plateau") ? o.get("plateau").getAsInt() : 0);
            default -> new Spread("uniform", min, max, 1, 0); // uniform, and a fair stand-in for the biased shapes
        };
    }

    private static int anchor(JsonObject o, DimensionType type) {
        if (o.has("absolute")) return o.get("absolute").getAsInt();
        if (o.has("above_bottom")) return type.minY() + o.get("above_bottom").getAsInt();
        if (o.has("below_top")) return type.minY() + type.height() - 1 - o.get("below_top").getAsInt();
        return 0;
    }

    /** The average of an integer provider: a plain number, or {min_inclusive, max_inclusive}. */
    private static double average(JsonElement json) {
        if (json.isJsonPrimitive()) return json.getAsDouble();
        JsonObject o = json.getAsJsonObject();
        if (o.has("value")) return o.get("value").getAsDouble();
        if (o.has("min_inclusive") && o.has("max_inclusive")) return (o.get("min_inclusive").getAsDouble() + o.get("max_inclusive").getAsDouble()) / 2;
        return 1;
    }

    /** Keyed by item, for the client side. */
    public static Map<Item, Data> byItem(Map<String, Data> data) {
        Map<Item, Data> map = new HashMap<>();
        data.forEach((id, d) -> BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.ResourceLocation.parse(id)).ifPresent(item -> map.put(item, d)));
        return map;
    }
}
