package com.aydep.thecatalog.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

/** Something shown on a recipe page: one of The Catalog's own recipes, or one from another recipe viewer's plugin. */
public interface RecipeEntry {
    /** Height on a page whose content area is {@code width} wide. */
    int pageHeight(int width);

    /** Recipes with equal keys share a category bookmark (Crafting, Smelting, a mod's machine...). */
    Object category();

    Component categoryTitle();

    /** The 16x16 icon on the category bookmark. */
    void drawCategoryIcon(GuiGraphics g, int x, int y);

    /** The game recipe behind it, if there is one (needed to place it in a crafting grid or furnace). */
    default @Nullable RecipeHolder<?> holder() {
        return null;
    }
}
