package com.aydep.thecatalog;

import com.aydep.thecatalog.client.CatalogClient;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class CatalogItem extends Item {
    public CatalogItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // CatalogClient is only loaded when this line runs, which never happens on a dedicated server.
        if (level.isClientSide) CatalogClient.open();
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
