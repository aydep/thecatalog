package com.aydep.thecatalog;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(TheCatalog.MODID)
public class TheCatalog {
    public static final String MODID = "thecatalog";
    private static final String RECEIVED_TAG = MODID + ":received";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredItem<CatalogItem> CATALOG =
            ITEMS.register("catalog", () -> new CatalogItem(new Item.Properties().stacksTo(1)));

    private static final ModConfigSpec.Builder CONFIG = new ModConfigSpec.Builder();
    private static final ModConfigSpec.BooleanValue GIVE_ON_FIRST_JOIN = CONFIG
            .comment("Give every player one Catalog the first time they join a world.")
            .define("giveOnFirstJoin", true);
    private static final ModConfigSpec CONFIG_SPEC = CONFIG.build();

    public TheCatalog(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        modBus.addListener(TheCatalog::addToCreativeTab);
        container.registerConfig(ModConfig.Type.COMMON, CONFIG_SPEC);
        NeoForge.EVENT_BUS.addListener(TheCatalog::giveOnFirstJoin);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(CATALOG);
    }

    private static void giveOnFirstJoin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        // The "PlayerPersisted" sub-tag survives death, so the book is only ever given once.
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (!GIVE_ON_FIRST_JOIN.get() || persisted.getBoolean(RECEIVED_TAG)) return;
        persisted.putBoolean(RECEIVED_TAG, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        ItemStack book = new ItemStack(CATALOG.get());
        if (!player.getInventory().add(book)) player.drop(book, false);
    }
}
