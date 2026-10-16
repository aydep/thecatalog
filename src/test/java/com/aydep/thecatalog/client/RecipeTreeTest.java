package com.aydep.thecatalog.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aydep.thecatalog.client.RecipeTree.Amount;
import com.aydep.thecatalog.client.RecipeTree.Step;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecipeTreeTest {
    private final Map<String, List<Step<String>>> recipes = new HashMap<>();
    private final Set<String> natural = new HashSet<>();

    private void recipe(String output, int count, Object... inputs) {
        List<Amount<String>> list = new ArrayList<>();
        for (int i = 0; i < inputs.length; i += 2) list.add(new Amount<>((String) inputs[i], (Integer) inputs[i + 1]));
        recipes.computeIfAbsent(output, k -> new ArrayList<>()).add(new Step<>(output, count, list, "table"));
    }

    private RecipeTree<String> tree(String root) {
        return new RecipeTree<>(root, key -> recipes.getOrDefault(key, List.of()), natural::contains);
    }

    @Test
    void leftoversFromRoundingUp() {
        recipe("stick", 4, "planks", 2);
        recipe("planks", 4, "log", 1);
        RecipeTree<String> sticks = tree("stick"); // one craft: 4 sticks from 2 of the 4 planks one log makes
        assertEquals(List.of(new Amount<>("log", 1)), sticks.totalCost());
        assertEquals(List.of(new Amount<>("planks", 2)), sticks.leftovers());
        sticks.setAmount(3); // 12 sticks need 6 planks, so 2 logs
        assertEquals(List.of(new Amount<>("log", 2)), sticks.totalCost());
        assertEquals(List.of(new Amount<>("planks", 2)), sticks.leftovers());
    }

    @Test
    void leftoversAreSharedAcrossBranches() {
        recipe("stick", 4, "planks", 2);
        recipe("planks", 4, "log", 1);
        recipe("sign", 1, "stick", 1, "planks", 1);
        RecipeTree<String> sign = tree("sign");
        // the sticks' 4 planks leave 2; the sign's own plank comes out of those, not from another log
        assertEquals(List.of(new Amount<>("log", 1)), sign.totalCost());
        assertEquals(List.of(new Amount<>("stick", 3), new Amount<>("planks", 1)), sign.leftovers());
    }

    @Test
    void reversibleRecipesAreNotFollowed() {
        recipe("ingot", 9, "block", 1);
        recipe("ingot", 1, "nugget", 9);
        recipe("ingot", 1, "ore", 1);
        recipe("block", 1, "ingot", 9);
        recipe("nugget", 9, "ingot", 1);
        recipe("bars", 16, "ingot", 6);
        RecipeTree<String> bars = tree("bars");
        assertEquals(List.of(new Amount<>("ore", 6)), bars.totalCost()); // smelted, not from blocks or nuggets
        bars.toggleBase("ingot");
        assertEquals(List.of(new Amount<>("ingot", 6)), bars.totalCost());
        bars.toggleBase("ingot");
        assertEquals(List.of(new Amount<>("ore", 6)), bars.totalCost());
        bars.cycle("ingot"); // from a block; the block is not made from ingots again inside its own branch
        assertEquals(List.of(new Amount<>("block", 1)), bars.totalCost());
        assertEquals(List.of(new Amount<>("ingot", 3)), bars.leftovers());
    }

    @Test
    void shortestWayToWhatIsFoundInTheWorld() {
        recipe("glass", 1, "framed_glass", 1); // a crafting recipe, so listed first
        recipe("glass", 1, "sand", 1); // smelting
        recipe("framed_glass", 1, "glass", 1);
        recipe("sand", 1, "gravel", 1); // a mod's crushing
        recipe("gravel", 1, "cobblestone", 1);
        natural.addAll(List.of("sand", "gravel"));
        recipe("bottle", 3, "glass", 3);
        assertEquals(List.of(new Amount<>("sand", 3)), tree("bottle").totalCost());
    }

    @Test
    void itemsOnlyMadeFromEachOtherAreBase() {
        recipe("raw_iron", 9, "raw_iron_block", 1);
        recipe("raw_iron_block", 1, "raw_iron", 9);
        recipe("ingot", 1, "raw_iron", 1);
        recipe("rail", 16, "ingot", 6);
        assertEquals(List.of(new Amount<>("raw_iron", 6)), tree("rail").totalCost());
    }

    @Test
    void noRecipeMeansNoTree() {
        assertEquals(null, tree("dirt").top());
    }
}
