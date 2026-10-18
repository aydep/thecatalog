package com.aydep.thecatalog.compat.akashic;

import com.aydep.thecatalog.CatalogItem;
import com.aydep.thecatalog.TheCatalog;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import vazkii.akashictome.IModdedBook;
import vazkii.akashictome.Registries;
import vazkii.akashictome.data_components.ToolContentComponent;

/** Akashic Tome support. Only loaded when the tome is installed: every call is behind {@link TheCatalog#AKASHIC_TOME}. */
public final class AkashicCompat {
    /** The tome only takes items marked as modded books (or with "book" in their id, which "catalog" lacks). */
    private static final class TomeCatalogItem extends CatalogItem implements IModdedBook {
        TomeCatalogItem(Properties properties) {
            super(properties);
        }
    }

    private AkashicCompat() {}

    public static Item catalogItem(Item.Properties properties) {
        return new TomeCatalogItem(properties);
    }

    /** A tome holding the Catalog, or another book the tome is turned into, which carries the rest of the tome. */
    public static boolean holdsCatalog(ItemStack stack) {
        ToolContentComponent content = stack.get(Registries.TOOL_CONTENT.get());
        return content != null && content.getItems().stream().anyMatch(book -> book.is(TheCatalog.CATALOG.get()));
    }
}
