package com.aydep.thecatalog;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: move a recipe's ingredients from the inventory into the open crafting grid or furnace, as a click
 * in the vanilla recipe book does ({@code placeAll}: as many crafts as the inventory allows). Unlike the recipe book it
 * works for recipes the player hasn't unlocked; crafting the result still goes through vanilla's own rules.
 */
public record CraftPayload(int containerId, ResourceLocation recipe, boolean placeAll) implements CustomPacketPayload {
    public static final Type<CraftPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TheCatalog.MODID, "craft"));
    public static final StreamCodec<ByteBuf, CraftPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CraftPayload::containerId, ResourceLocation.STREAM_CODEC, CraftPayload::recipe,
            ByteBufCodecs.BOOL, CraftPayload::placeAll, CraftPayload::new);

    /** Set while placing, so the recipe-book unlock check is skipped (see ServerPlaceRecipeMixin). Server thread only. */
    public static boolean placing;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Whether the menu can take the recipe: a crafting recipe that fits its grid, or its furnace's cooking type. */
    public static boolean fits(RecipeBookMenu<?, ?> menu, RecipeHolder<?> holder) {
        Recipe<?> recipe = holder.value();
        return switch (menu.getRecipeBookType()) {
            case CRAFTING -> recipe instanceof CraftingRecipe crafting && crafting.canCraftInDimensions(menu.getGridWidth(), menu.getGridHeight());
            case FURNACE -> recipe.getType() == RecipeType.SMELTING;
            case BLAST_FURNACE -> recipe.getType() == RecipeType.BLASTING;
            case SMOKER -> recipe.getType() == RecipeType.SMOKING;
            default -> false; // a mod's own recipe book type
        };
    }

    /** The same checks as vanilla's recipe book packet, plus that the recipe suits the menu (the client could send any id). */
    static void handle(CraftPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator()) return;
        if (player.containerMenu.containerId != payload.containerId() || !(player.containerMenu instanceof RecipeBookMenu<?, ?> menu)
                || !menu.stillValid(player)) return;
        player.resetLastActionTime();
        player.server.getRecipeManager().byKey(payload.recipe()).filter(holder -> fits(menu, holder)).ifPresent(holder -> {
            placing = true;
            try {
                menu.handlePlacement(payload.placeAll(), holder, player);
            } finally {
                placing = false;
            }
        });
    }
}
