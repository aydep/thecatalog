package com.aydep.thecatalog.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/**
 * Breaks a craft down into every step: how many crafts each recipe takes, the base ingredients it all costs, and what
 * is left over (a recipe that makes 4 sticks when 1 is needed leaves 3, which later steps use up first). Generic over
 * the item key so the arithmetic can be tested without the game.
 *
 * <p>By default every ingredient with a recipe is broken down, crafting-table recipes first, except for recipes that
 * undo another (ingot from block while the block is made from ingots), and an item never inside its own branch.
 * The player can cycle an item to its next recipe, or keep it as a base ingredient.
 */
public final class RecipeTree<K> {
    static final int MAX_DEPTH = 12, MAX_NODES = 200;

    public record Amount<K>(K key, int count) {}

    /** One recipe: {@code count} of {@code output} per craft from {@code inputs}; {@code station} is what to draw for it. */
    public record Step<K>(K output, int count, List<Amount<K>> inputs, Object station) {}

    /** {@code step} is null for a base ingredient; {@code need} is how many the parent asked for. */
    public record Node<K>(K key, int need, @Nullable Step<K> step, int crafts, List<Node<K>> children) {}

    private final K root;
    private final Function<K, List<Step<K>>> lookup; // an item's recipes, the preferred ones first
    private final Map<K, List<Step<K>>> recipes = new HashMap<>();
    private final Map<K, Integer> chosen = new HashMap<>(); // the player's choice per item: a recipe index, or -1 for base
    private int amount = 1;
    private int nodes;
    private Map<K, Integer> cost = new LinkedHashMap<>(), leftovers = new LinkedHashMap<>();
    private @Nullable Node<K> top;

    public RecipeTree(K root, Function<K, List<Step<K>>> lookup) {
        this.root = root;
        this.lookup = lookup;
        solve();
    }

    /** The whole tree, or null if the item has no recipe. */
    public @Nullable Node<K> top() {
        return top;
    }

    /** How many times the top recipe is crafted. */
    public int amount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = Math.clamp(amount, 1, 999);
        solve();
    }

    /** Left-click: the item's next recipe. */
    public void cycle(K key) {
        List<Step<K>> list = recipes(key);
        if (list.isEmpty()) return;
        chosen.put(key, (Math.max(-1, choice(key, key.equals(root))) + 1) % list.size());
        solve();
    }

    /** Right-click: stop breaking the item down, or break it down again. The top item always is. */
    public void toggleBase(K key) {
        if (key.equals(root) || recipes(key).isEmpty()) return;
        if (choice(key, false) >= 0) chosen.put(key, -1);
        else chosen.put(key, Math.max(0, defaultChoice(key)));
        solve();
    }

    public List<Amount<K>> totalCost() {
        return amounts(cost);
    }

    public List<Amount<K>> leftovers() {
        return amounts(leftovers);
    }

    private static <K> List<Amount<K>> amounts(Map<K, Integer> map) {
        List<Amount<K>> list = new ArrayList<>();
        map.forEach((key, count) -> {
            if (count > 0) list.add(new Amount<>(key, count));
        });
        return list;
    }

    private List<Step<K>> recipes(K key) {
        List<Step<K>> list = recipes.get(key); // not computeIfAbsent: the lookup may be slow, but never reenters
        if (list == null) recipes.put(key, list = lookup.apply(key));
        return list;
    }

    private void solve() {
        cost = new LinkedHashMap<>();
        leftovers = new LinkedHashMap<>();
        nodes = 0;
        List<Step<K>> list = recipes(root);
        top = list.isEmpty() ? null : node(root, amount * list.get(Math.max(0, choice(root, true))).count(), new HashSet<>(), 0);
    }

    /** The recipe the item uses: the player's choice, or the default; -1 to keep it as a base ingredient. */
    private int choice(K key, boolean isRoot) {
        Integer picked = chosen.get(key);
        if (picked != null) return picked < recipes(key).size() ? picked : -1;
        int found = defaultChoice(key);
        return found < 0 && isRoot ? 0 : found;
    }

    /** The first recipe that doesn't undo another: none of its inputs is made from the item it makes. */
    private int defaultChoice(K key) {
        List<Step<K>> list = recipes(key);
        for (int i = 0; i < list.size(); i++) {
            boolean undoes = false;
            for (Amount<K> input : list.get(i).inputs()) {
                for (Step<K> inputRecipe : recipes(input.key())) {
                    undoes |= inputRecipe.inputs().stream().anyMatch(a -> a.key().equals(key));
                }
            }
            if (!undoes) return i;
        }
        return -1;
    }

    private Node<K> node(K key, int need, Set<K> branch, int depth) {
        nodes++;
        int fromLeftovers = Math.min(need, leftovers.getOrDefault(key, 0));
        if (fromLeftovers > 0) leftovers.merge(key, -fromLeftovers, Integer::sum);
        int rest = need - fromLeftovers;
        boolean isRoot = depth == 0;
        int choice = rest == 0 || branch.contains(key) || depth >= MAX_DEPTH || nodes >= MAX_NODES ? -1 : choice(key, isRoot);
        if (choice < 0) {
            if (rest > 0) cost.merge(key, rest, Integer::sum);
            return new Node<>(key, need, null, 0, List.of());
        }
        Step<K> step = recipes(key).get(choice);
        int crafts = (rest + step.count() - 1) / step.count();
        int extra = crafts * step.count() - rest;
        if (extra > 0) leftovers.merge(key, extra, Integer::sum);
        Map<K, Integer> perCraft = new LinkedHashMap<>(); // the same item in several slots counts once, added up
        for (Amount<K> input : step.inputs()) perCraft.merge(input.key(), input.count(), Integer::sum);
        branch.add(key);
        List<Node<K>> children = new ArrayList<>();
        perCraft.forEach((input, count) -> children.add(node(input, count * crafts, branch, depth + 1)));
        branch.remove(key);
        return new Node<>(key, need, step, crafts, children);
    }
}
