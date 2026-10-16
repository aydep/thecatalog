package com.aydep.thecatalog.mixin;

import com.aydep.thecatalog.CraftPayload;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The Catalog's craft button may place any recipe, not only ones unlocked in the player's recipe book. */
@Mixin(ServerPlaceRecipe.class)
public abstract class ServerPlaceRecipeMixin {
    @Redirect(method = "recipeClicked", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/stats/ServerRecipeBook;contains(Lnet/minecraft/world/item/crafting/RecipeHolder;)Z"))
    private boolean thecatalog$contains(ServerRecipeBook book, RecipeHolder<?> recipe) {
        return CraftPayload.placing || book.contains(recipe);
    }
}
