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
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * Found and loaded by JEI only, so nothing here runs without JEI installed. Once JEI has loaded every mod's plugin,
 * The Catalog's recipe pages read their categories and recipes from JEI. JEI's item list is shown only in Creative;
 * in other game modes the book takes its place.
 */
@JeiPlugin
public class CatalogJeiPlugin implements IModPlugin {
    /** The game mode JEI's item list was last shown or hidden for; null until a world's runtime is up. */
    private static @Nullable Boolean creative;
    private static boolean running;

    public CatalogJeiPlugin() {
        NeoForge.EVENT_BUS.addListener(CatalogJeiPlugin::onTick);
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

    /** Only on a game mode change, so Ctrl+O still works in between. */
    private static void onTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!running || player == null || Boolean.valueOf(player.isCreative()).equals(creative)) return;
        creative = player.isCreative();
        try {
            Internal.getClientToggleState().setOverlayEnabled(creative);
        } catch (LinkageError | RuntimeException e) { // JEI internals; a future JEI may change them
            running = false;
            LogUtils.getLogger().warn("The Catalog could not hide JEI's item list: {}", e.toString());
        }
    }
}
