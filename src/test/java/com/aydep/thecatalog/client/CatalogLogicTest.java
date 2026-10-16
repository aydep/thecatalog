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
        BookLayout small = BookLayout.fit(320, 240, 9, 10, true); // smallest window at GUI scale
        assertEquals(142, small.pageWidth());
        assertEquals(BookLayout.MIN_COLUMNS, small.cols());
        assertTrue(small.x() - BookLayout.TAB_WIDTH >= 0 && small.x() + 2 * small.pageWidth() <= 320);
        BookLayout right = BookLayout.fit(320, 240, 9, 10, false);
        assertTrue(right.x() >= 0 && right.x() + 2 * right.pageWidth() + BookLayout.TAB_WIDTH <= 320);
        BookLayout big = BookLayout.fit(1000, 600, 9, 10, true); // the configured columns/rows are met exactly
        assertEquals(9, big.cols());
        assertEquals(10, big.rows());
        assertEquals(7, BookLayout.fit(1000, 600, 7, 10, true).cols());
        assertEquals(BookLayout.MIN_COLUMNS, BookLayout.fit(1000, 600, 1, 10, true).cols()); // never below the minimum
        assertEquals(BookLayout.MIN_ROWS, BookLayout.fit(1000, 600, 9, 1, true).rows());
        assertEquals(1, BookLayout.spreads(0));
        assertEquals(1, BookLayout.spreads(2));
        assertEquals(2, BookLayout.spreads(3));
    }
}
