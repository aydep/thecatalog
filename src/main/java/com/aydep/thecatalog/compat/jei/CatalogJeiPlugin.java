package com.aydep.thecatalog.compat.jei;

import com.aydep.thecatalog.TheCatalog;
import com.aydep.thecatalog.client.ExternalRecipes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * Found and loaded by JEI only, so nothing here runs without JEI installed. Once JEI has loaded every mod's plugin,
 * The Catalog's recipe pages read their categories and recipes from JEI.
 */
@JeiPlugin
public class CatalogJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(TheCatalog.MODID, "catalog");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        ExternalRecipes.setActive(new JeiRecipes(runtime));
    }

    @Override
    public void onRuntimeUnavailable() {
        ExternalRecipes.setActive(null);
    }
}
