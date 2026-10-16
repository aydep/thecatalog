package com.aydep.thecatalog.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * Breaks a craft down into every step: how many crafts each recipe takes, the base ingredients it all costs, and what
 * is left over (a recipe that makes 4 sticks when 1 is needed leaves 3, which later steps use up first). Generic over
 * the item key so the arithmetic can be tested without the game.
 *
 * <p>By default each ingredient is broken down along the shortest way to things found in the world: items with no
 * recipe, "natural" ones (sand, logs, ores...), and ones only made from each other (raw iron and its block). So glass
 * comes from smelting sand, not from some glass block made of glass. Ties go to the earlier recipe (crafting-table
 * recipes come first), and an item is never broken down inside its own branch. The player can cycle an item to its
 * next recipe, or keep it as a base ingredient.
 */
public final class RecipeTree<K> {
    static final int MAX_DEPTH = 12, MAX_NODES = 200, MAX_ITEMS = 4000, FAR = Integer.MAX_VALUE / 2;

    public record Amount<K>(K key, int count) {}

    /** One recipe: {@code count} of {@code output} per craft from {@code inputs}; {@code station} is what to draw for it. */
    public record Step<K>(K output, int count, List<Amount<K>> inputs, Object station) {}

    /** {@code step} is null for a base ingredient; {@code need} is how many the parent asked for. */
    public record Node<K>(K key, int need, @Nullable Step<K> step, int crafts, List<Node<K>> children) {}

    private final K root;
    private final Function<K, List<Step<K>>> lookup; // an item's recipes, the preferred ones first
    private final Predicate<K> natural;
    private final Map<K, List<Step<K>>> recipes = new HashMap<>();
    private @Nullable Map<K, Integer> heights; // steps down to base items, worked out once
    private final Map<K, Integer> chosen = new HashMap<>(); // the player's choice per item: a recipe index, or -1 for base
    private int amount = 1;
    private int nodes;
    private Map<K, Integer> cost = new LinkedHashMap<>(), leftovers = new LinkedHashMap<>();
    private @Nullable Node<K> top;

    /** @param natural items found in the world as they are, kept as base ingredients even if they have recipes */
    public RecipeTree(K root, Function<K, List<Step<K>>> lookup, Predicate<K> natural) {
        this.root = root;
        this.lookup = lookup;
        this.natural = natural;
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
        chosen.put(key, (Math.max(-1, choice(key, key.equals(root), Set.of())) + 1) % list.size());
        solve();
    }

    /** Right-click: stop breaking the item down, or break it down again. The top item always is. */
    public void toggleBase(K key) {
        if (key.equals(root) || recipes(key).isEmpty()) return;
        if (choice(key, false, Set.of()) >= 0) chosen.put(key, -1);
        else chosen.put(key, Math.max(0, shortest(key, Set.of())));
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
        top = list.isEmpty() ? null : node(root, amount * list.get(Math.max(0, choice(root, true, Set.of()))).count(), new HashSet<>(), 0);
    }

    /** The recipe the item uses: the player's choice, or the default; -1 to keep it as a base ingredient. */
    private int choice(K key, boolean isRoot, Set<K> branch) {
        Integer picked = chosen.get(key);
        if (picked != null) return picked < recipes(key).size() ? picked : -1;
        return !isRoot && height(key) == 0 ? -1 : shortest(key, branch); // height 0: found in the world
    }

    /** Found in the world as it is: no recipe, or natural. (Items only made from each other are found by their height.) */
    private boolean base(K key) {
        return recipes(key).isEmpty() || natural.test(key);
    }

    /** The recipe with the shortest way down to base items; ties go to the earlier one. -1 if there is none. */
    /**
     * The recipe with the shortest way down to base items; ties go to the earlier one. Recipes using an item further up
     * the branch (an iron block made from the ingots it is being broken into) are left out. -1 if none is left.
     */
    private int shortest(K key, Set<K> branch) {
        List<Step<K>> list = recipes(key);
        int best = -1, bestHeight = Integer.MAX_VALUE;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).inputs().stream().anyMatch(input -> branch.contains(input.key()))) continue;
            int height = stepHeight(list.get(i));
            if (height < bestHeight) {
                best = i;
                bestHeight = height;
            }
        }
        return best;
    }


    private int stepHeight(Step<K> step) {
        int height = 0;
        for (Amount<K> input : step.inputs()) height = Math.max(height, height(input.key()));
        return height >= FAR ? FAR : height + 1;
    }

    private int height(K key) {
        if (heights == null) computeHeights();
        return heights.getOrDefault(key, 0);
    }

    /**
     * How many steps each item reachable from the top is above base items, along its shortest recipe: base items are
     * 0, and the rest settle by repeating "1 + the highest input" until nothing gets shorter.
     */
    private void computeHeights() {
        heights = new HashMap<>();
        List<K> items = new ArrayList<>(List.of(root));
        Set<K> seen = new HashSet<>(items);
        // shortcut: collects at most MAX_ITEMS items (huge modpacks); ones past that count as base when choosing recipes
        for (int i = 0; i < items.size() && seen.size() < MAX_ITEMS; i++) {
            if (i > 0 && base(items.get(i))) continue;
            for (Step<K> step : recipes(items.get(i))) {
                for (Amount<K> input : step.inputs()) if (seen.add(input.key())) items.add(input.key());
            }
        }
        for (int i = 0; i < items.size(); i++) heights.put(items.get(i), i > 0 && base(items.get(i)) ? 0 : FAR);
        settle(items);
        // items with no way down that are made only from each other (raw iron and its block) are found in the world
        List<K> loops = items.stream().filter(key -> heights.get(key) >= FAR && madeFromItself(key)).toList();
        loops.forEach(key -> heights.put(key, 0));
        settle(items);
        heights.replaceAll((key, height) -> height >= FAR ? 0 : height);
    }

    private void settle(List<K> items) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (K key : items) {
                int height = heights.get(key);
                for (Step<K> step : recipes(key)) height = Math.min(height, stepHeight(step));
                if (height < heights.get(key)) {
                    heights.put(key, height);
                    changed = true;
                }
            }
        }
    }

    /** Whether the item's recipes lead back to it through other items that have no way down either. */
    private boolean madeFromItself(K key) {
        List<K> todo = new ArrayList<>(List.of(key));
        Set<K> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            for (Step<K> step : recipes(todo.remove(todo.size() - 1))) {
                for (Amount<K> input : step.inputs()) {
                    if (input.key().equals(key)) return true;
                    if (heights.getOrDefault(input.key(), 0) >= FAR && seen.add(input.key())) todo.add(input.key());
                }
            }
        }
        return false;
    }

    private Node<K> node(K key, int need, Set<K> branch, int depth) {
        nodes++;
        int fromLeftovers = Math.min(need, leftovers.getOrDefault(key, 0));
        if (fromLeftovers > 0) leftovers.merge(key, -fromLeftovers, Integer::sum);
        int rest = need - fromLeftovers;
        boolean isRoot = depth == 0;
        int choice = rest == 0 || branch.contains(key) || depth >= MAX_DEPTH || nodes >= MAX_NODES ? -1 : choice(key, isRoot, branch);
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
