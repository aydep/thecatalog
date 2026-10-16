package com.aydep.thecatalog.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Recipes supplied by another recipe viewer's plugins (JEI) and drawn by their own code. When a source is active, recipe
 * pages show its recipes instead of The Catalog's own, so every mod that supports that viewer works without changes.
 */
public final class ExternalRecipes {
    public interface Source {
        /** The stack's recipes, or its uses. Must be cheap: entries build anything heavy only when drawn. */
        List<Entry> lookup(ItemStack stack, boolean uses);
    }

    public interface Entry extends RecipeEntry {
        /** Unscaled size of the recipe drawing. */
        int width();

        int height();

        /** Draws the recipe at x, y scaled by {@code scale}; the mouse is in screen coordinates. */
        void draw(GuiGraphics g, int x, int y, float scale, int mouseX, int mouseY);

        /** The item under the mouse in the last drawn position, or empty. */
        ItemStack itemAt(int mouseX, int mouseY);

        /** Draws the viewer's own tooltip for whatever is under the mouse; false if there is nothing. */
        boolean drawTooltip(GuiGraphics g, int mouseX, int mouseY);

        /** Called every client tick while visible (e.g. to cycle ingredients). */
        void tick();

        @Override
        default int pageHeight(int width) {
            return DisplayRecipe.CAPTION + (int) Math.ceil(height() * scale(width));
        }

        /** Recipes wider than the page are shrunk to fit. */
        default float scale(int width) {
            return Math.min(1f, width / (float) width());
        }
    }

    private static volatile @Nullable Source active;

    private ExternalRecipes() {}

    public static @Nullable Source active() {
        return active;
    }

    public static void setActive(@Nullable Source source) {
        active = source;
    }
}
