package com.aydep.thecatalog.client;

/** Something shown on a recipe page: one of The Catalog's own recipes, or one from another recipe viewer's plugin. */
public interface RecipeEntry {
    /** Height on a page whose content area is {@code width} wide, caption included. */
    int pageHeight(int width);
}
