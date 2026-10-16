package com.aydep.thecatalog.mixin;

import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Tells the recipe book button apart from other image buttons, by its sprites. */
@Mixin(ImageButton.class)
public interface ImageButtonAccessor {
    @Accessor("sprites")
    WidgetSprites thecatalog$sprites();
}
