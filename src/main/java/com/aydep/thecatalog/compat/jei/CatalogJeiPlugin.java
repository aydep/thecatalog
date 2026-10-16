package com.aydep.thecatalog.compat.jei;

import com.aydep.thecatalog.TheCatalog;
import com.aydep.thecatalog.client.ExternalRecipes;
import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.Internal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Found and loaded by JEI only, so nothing here runs without JEI installed. Once JEI has loaded every mod's plugin,
 * The Catalog's recipe pages read their categories and recipes from JEI. JEI's item list can only be shown in
 * Creative; in other game modes the book takes its place.
 */
@JeiPlugin
public class CatalogJeiPlugin implements IModPlugin {
    /** Whether the player was in Creative when last checked; null until a world's runtime is up. */
    private static @Nullable Boolean creative;
    private static boolean running;

    public CatalogJeiPlugin() {
        NeoForge.EVENT_BUS.addListener(CatalogJeiPlugin::onRender);
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(TheCatalog.MODID, "catalog");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        ExternalRecipes.setActive(new JeiRecipes(runtime));
        running = true;
    }

    @Override
    public void onRuntimeUnavailable() {
        ExternalRecipes.setActive(null);
        running = false;
        creative = null;
    }

    /** Checked before every frame, so turning the list on (Ctrl+O) outside Creative is undone before it is drawn. */
    private static void onRender(ScreenEvent.Render.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!running || player == null) return;
        boolean now = player.isCreative();
        try {
            var toggle = Internal.getClientToggleState();
            if (!now && toggle.isOverlayEnabled()) toggle.setOverlayEnabled(false);
            else if (now && Boolean.FALSE.equals(creative)) toggle.setOverlayEnabled(true); // back in Creative: show it again
            creative = now;
        } catch (LinkageError | RuntimeException e) { // JEI internals; a future JEI may change them
            running = false;
            LogUtils.getLogger().warn("The Catalog could not hide JEI's item list: {}", e.toString());
        }
    }
}
