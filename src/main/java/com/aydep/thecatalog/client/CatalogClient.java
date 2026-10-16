package com.aydep.thecatalog.client;

import com.aydep.thecatalog.TheCatalog;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
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

    /** The book only opens by key while the player carries it, unless the config says otherwise. */
    static boolean hasBook() {
        Player player = Minecraft.getInstance().player;
        return player != null && (!ClientConfig.REQUIRE_BOOK.get() || player.getInventory().contains(stack -> stack.is(TheCatalog.CATALOG.get())));
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        CatalogIndex.tick(Minecraft.getInstance());
        while (OPEN.consumeClick()) {
            if (hasBook()) open();
        }
    }

    /**
     * R / U over an item in any inventory screen shows that item's recipes / uses: in the side panel if it is open,
     * otherwise in the book.
     */
    @SubscribeEvent
    static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;
        if (screen.children().stream().anyMatch(c -> c instanceof EditBox box && box.isVisible() && box.isFocused())) return;
        CatalogScreen panel = CatalogScreen.panelOf(screen);
        if (panel != null && panel.typing()) return;
        InputConstants.Key key = InputConstants.getKey(event.getKeyCode(), event.getScanCode());
        boolean recipes = RECIPES.isActiveAndMatches(key), uses = USES.isActiveAndMatches(key);
        Slot slot = screen.getSlotUnderMouse();
        if (!(recipes || uses) || slot == null || !slot.hasItem()) return;
        ItemStack stack = slot.getItem().copyWithCount(1);
        if (panel != null) panel.open(stack, uses);
        else if (hasBook()) Minecraft.getInstance().setScreen(new CatalogScreen(screen, new CatalogScreen.RecipeView(stack, uses, 0, 0)));
        else return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onRecipes(RecipesUpdatedEvent event) {
        CatalogIndex.markDirty();
    }

    /** Item names are cached in the index for searching; a resource reload (e.g. a language change) reloads them. */
    @SubscribeEvent
    static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            CatalogIndex.loadSearchLanguages(manager);
            Generation.load(manager);
            CatalogIndex.markDirty();
        });
    }

    @SubscribeEvent
    static void onTags(TagsUpdatedEvent event) {
        CatalogIndex.markDirty();
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CatalogIndex.clear();
        CatalogScreen.forget();
    }
}
