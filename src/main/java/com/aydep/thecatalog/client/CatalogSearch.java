package com.aydep.thecatalog.client;

import java.util.Locale;

public final class CatalogSearch {
    private CatalogSearch() {}

    /** True if every word of the query is in the item name and every "@word" is in the mod id or mod name. */
    public static boolean matches(String query, String itemName, String modId, String modName) {
        for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (word.isEmpty()) continue;
            boolean found = word.startsWith("@")
                    ? lower(modId).contains(word.substring(1)) || lower(modName).contains(word.substring(1))
                    : lower(itemName).contains(word);
            if (!found) return false;
        }
        return true;
    }

    private static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
