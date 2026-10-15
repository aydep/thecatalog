package com.aydep.thecatalog.client;

import com.aydep.thecatalog.TheCatalog;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = TheCatalog.MODID, value = Dist.CLIENT)
public final class CatalogClient {
    private static final String CATEGORY = "key.categories.thecatalog";
    static final KeyMapping OPEN = new KeyMapping("key.thecatalog.open", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);
    static final KeyMapping RECIPES = new KeyMapping("key.thecatalog.recipes", KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    static final KeyMapping USES = new KeyMapping("key.thecatalog.uses", KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, CATEGORY);

    private CatalogClient() {}

    @SubscribeEvent
    static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
        event.register(RECIPES);
        event.register(USES);
    }

    /** Opens the book where it was left. */
    public static void open() {
        Minecraft.getInstance().setScreen(new CatalogScreen(null, null));
    }

    /** The book only opens by key while the player carries it. */
    static boolean hasBook() {
        Player player = Minecraft.getInstance().player;
        return player != null && player.getInventory().contains(stack -> stack.is(TheCatalog.CATALOG.get()));
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        while (OPEN.consumeClick()) {
            if (hasBook()) open();
        }
    }

    /** R / U over an item in any inventory screen opens the book on that item's recipes / uses. */
    @SubscribeEvent
    static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (screen.children().stream().anyMatch(c -> c instanceof EditBox box && box.isVisible() && box.isFocused())) return;
        InputConstants.Key key = InputConstants.getKey(event.getKeyCode(), event.getScanCode());
        boolean recipes = RECIPES.isActiveAndMatches(key), uses = USES.isActiveAndMatches(key);
        Slot slot = screen.getSlotUnderMouse();
        if (!(recipes || uses) || slot == null || !slot.hasItem() || !hasBook()) return;
        Minecraft.getInstance().setScreen(new CatalogScreen(screen, new CatalogScreen.RecipeView(slot.getItem().copyWithCount(1), uses, 0)));
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onRecipes(RecipesUpdatedEvent event) {
        CatalogIndex.invalidate();
    }

    @SubscribeEvent
    static void onTags(TagsUpdatedEvent event) {
        CatalogIndex.invalidate();
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CatalogIndex.invalidate();
        CatalogScreen.forget();
    }
}
