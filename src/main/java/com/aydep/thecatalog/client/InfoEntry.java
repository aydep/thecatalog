package com.aydep.thecatalog.client;

import com.aydep.thecatalog.TheCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/** A page entry that is text rather than a recipe, such as where an ore generates. */
public final class InfoEntry implements RecipeEntry {
    static final int TEXT_X = 22;
    private static final ResourceLocation WORLDGEN = ResourceLocation.fromNamespaceAndPath(TheCatalog.MODID, "worldgen");

    final ItemStack stack;
    final Component text;

    private InfoEntry(ItemStack stack, Component text) {
        this.stack = stack;
        this.text = text;
    }

    /**
     * Where the item generates, from the translation {@code thecatalog.worldgen.<mod>.<item>} (lines separated by
     * newlines), or null. Vanilla ores ship with one; mods and resource packs can add their own.
     */
    static @Nullable InfoEntry worldgen(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String key = "thecatalog.worldgen." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        return I18n.exists(key) ? new InfoEntry(stack, Component.translatable(key)) : null;
    }

    @Override
    public int pageHeight(int width) {
        int lines = Minecraft.getInstance().font.split(text, width - TEXT_X).size();
        return Math.max(BookLayout.SLOT, lines * Minecraft.getInstance().font.lineHeight) + 2;
    }

    @Override
    public Object category() {
        return WORLDGEN;
    }

    @Override
    public Component categoryTitle() {
        return Component.translatable("gui.thecatalog.worldgen");
    }

    @Override
    public void drawCategoryIcon(GuiGraphics g, int x, int y) {
        g.renderItem(new ItemStack(Items.IRON_PICKAXE), x, y);
    }
}
