package com.aydep.thecatalog.mixin;

import com.aydep.thecatalog.client.CatalogClient;
import com.aydep.thecatalog.client.CatalogScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The recipe book button stays, but opens The Catalog's one-page panel in the recipe book's place. The container
 * screens still move aside for it exactly as for the recipe book; only its drawing and input are replaced. Without the
 * book in the inventory (if the config asks for it) the button is hidden and the book stays shut.
 */
@Mixin(RecipeBookComponent.class)
public abstract class RecipeBookComponentMixin implements CatalogScreen.PanelHost {
    @Shadow private int width;
    @Shadow private int height;
    @Shadow private int xOffset;
    @Shadow protected Minecraft minecraft;

    @Shadow public abstract boolean isVisible();

    @Unique private @Nullable CatalogScreen thecatalog$panel;
    // shortcut: checked when the screen opens; picking the book up or dropping it with the screen open counts from the next opening
    @Unique private boolean thecatalog$allowed = true;

    @Override
    public @Nullable CatalogScreen thecatalog$panel() {
        return isVisible() ? thecatalog$panel : null;
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void thecatalog$init(CallbackInfo ci) {
        thecatalog$allowed = CatalogClient.hasBook();
    }

    /** Shut without the book; the player's open/closed setting is kept for when they carry it again. */
    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    private void thecatalog$isVisible(CallbackInfoReturnable<Boolean> cir) {
        if (!thecatalog$allowed) cir.setReturnValue(false);
    }

    /** Runs whenever the book is opened or its screen resized. */
    @Inject(method = "initVisuals", at = @At("TAIL"))
    private void thecatalog$initVisuals(CallbackInfo ci) {
        thecatalog$panel = CatalogScreen.panel(minecraft, width, height, (width - 147) / 2 - xOffset, (height - 166) / 2);
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void thecatalog$render(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        CatalogScreen panel = thecatalog$panel();
        if (panel == null) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 100); // like the recipe book: above the container's slots
        panel.render(g, mouseX, mouseY, partialTick);
        g.pose().popPose();
        ci.cancel();
    }

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void thecatalog$renderTooltip(CallbackInfo ci) {
        if (thecatalog$panel() != null) ci.cancel(); // the panel draws its own tooltips
    }

    @Inject(method = "renderGhostRecipe", at = @At("HEAD"), cancellable = true)
    private void thecatalog$renderGhostRecipe(CallbackInfo ci) {
        if (thecatalog$panel() != null) ci.cancel();
    }

    /** Without the book the vanilla tick would keep reopening it to match the player's setting, so it is skipped. */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void thecatalog$tick(CallbackInfo ci) {
        if (!thecatalog$allowed) ci.cancel();
        CatalogScreen panel = thecatalog$panel();
        if (panel != null) panel.tick();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void thecatalog$mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        CatalogScreen panel = thecatalog$panel();
        if (panel != null) cir.setReturnValue(panel.mouseClicked(mouseX, mouseY, button));
    }

    /** Clicking on the panel must not count as clicking outside the inventory, which would drop the held item. */
    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void thecatalog$hasClickedOutside(double mouseX, double mouseY, int x, int y, int width, int height, int button,
                                              CallbackInfoReturnable<Boolean> cir) {
        CatalogScreen panel = thecatalog$panel();
        if (panel == null) return;
        boolean outsideContainer = mouseX < x || mouseY < y || mouseX >= x + width || mouseY >= y + height;
        cir.setReturnValue(outsideContainer && !panel.isOver(mouseX, mouseY));
    }

    /** Escape goes on to the recipe book's own handling (which closes it where it covers the inventory). */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void thecatalog$keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        CatalogScreen panel = thecatalog$panel();
        if (panel == null) return;
        if (panel.keyPressed(keyCode, scanCode, modifiers)) cir.setReturnValue(true);
        else if (keyCode != 256) cir.setReturnValue(false);
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void thecatalog$charTyped(char codePoint, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        CatalogScreen panel = thecatalog$panel();
        if (panel != null) cir.setReturnValue(panel.charTyped(codePoint, modifiers));
    }

    /** So the screen sends mouse scrolling over the panel here. */
    @Inject(method = "isMouseOver", at = @At("HEAD"), cancellable = true)
    private void thecatalog$isMouseOver(double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
        CatalogScreen panel = thecatalog$panel();
        if (panel != null) cir.setReturnValue(panel.isOver(mouseX, mouseY));
    }

    /** The recipe book has no scrolling of its own, so this is added to it. */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        CatalogScreen panel = thecatalog$panel();
        return panel != null && panel.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
