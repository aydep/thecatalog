package com.aydep.thecatalog.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CatalogLogicTest {
    @Test
    void searchMatchesWordsAndMods() {
        assertTrue(CatalogSearch.matches("", "Oak Planks", "minecraft", "Minecraft"));
        assertTrue(CatalogSearch.matches("  oak  ", "Oak Planks", "minecraft", "Minecraft"));
        assertTrue(CatalogSearch.matches("plank oak", "Oak Planks", "minecraft", "Minecraft"));
        assertFalse(CatalogSearch.matches("birch", "Oak Planks", "minecraft", "Minecraft"));
        assertTrue(CatalogSearch.matches("@mine", "Oak Planks", "minecraft", "Minecraft"));
        assertTrue(CatalogSearch.matches("@farmer's tomato", "Tomato", "farmersdelight", "Farmer's Delight"));
        assertFalse(CatalogSearch.matches("@create oak", "Oak Planks", "minecraft", "Minecraft"));
        assertTrue(CatalogSearch.matches("ДУБ", "Дубовые доски", "minecraft", "Minecraft"));
    }

    @Test
    void bookFitsScreen() {
        BookLayout small = BookLayout.fit(320, 240, 200, 240, true); // smallest window at GUI scale
        assertEquals(142, small.pageWidth());
        assertEquals(6, small.cols());
        assertTrue(small.x() - BookLayout.TAB_WIDTH >= 0 && small.x() + 2 * small.pageWidth() <= 320);
        BookLayout right = BookLayout.fit(320, 240, 200, 240, false);
        assertTrue(right.x() >= 0 && right.x() + 2 * right.pageWidth() + BookLayout.TAB_WIDTH <= 320);
        assertEquals(160, BookLayout.fit(1000, 600, 160, 240, true).pageWidth()); // config cap
        assertEquals(142, BookLayout.fit(1000, 600, 50, 240, true).pageWidth()); // cap never below the minimum
        BookLayout big = BookLayout.fit(1000, 600, 200, 240, true);
        assertEquals(200, big.pageWidth());
        assertEquals(9, big.cols());
        assertEquals(10, big.rows());
        assertEquals(1, BookLayout.spreads(0));
        assertEquals(1, BookLayout.spreads(2));
        assertEquals(2, BookLayout.spreads(3));
    }
}
