package com.aydep.thecatalog;

import com.aydep.thecatalog.compat.akashic.AkashicCompat;
import com.google.gson.Gson;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(TheCatalog.MODID)
public class TheCatalog {
    public static final String MODID = "thecatalog";
    private static final String RECEIVED_TAG = MODID + ":received";
    private static final Gson GSON = new Gson();

    public static final boolean AKASHIC_TOME = ModList.get().isLoaded("akashictome");
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredItem<Item> CATALOG = ITEMS.register("catalog", () -> AKASHIC_TOME
            ? AkashicCompat.catalogItem(new Item.Properties().stacksTo(1)) : new CatalogItem(new Item.Properties().stacksTo(1)));

    /** The Catalog itself, or an Akashic Tome with it inside: either counts as carrying the book. */
    public static boolean isCatalog(ItemStack stack) {
        return stack.is(CATALOG.get()) || AKASHIC_TOME && AkashicCompat.holdsCatalog(stack);
    }

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
        modBus.addListener(TheCatalog::registerPayloads);
        NeoForge.EVENT_BUS.addListener(TheCatalog::sendOreGeneration);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // the handler only runs on the client, so the client class is never loaded on a dedicated server
        event.registrar("1").playToClient(GenerationPayload.TYPE, GenerationPayload.STREAM_CODEC,
                (payload, context) -> com.aydep.thecatalog.client.Generation.receive(payload.json()));
        event.registrar("1").playToServer(CraftPayload.TYPE, CraftPayload.STREAM_CODEC, CraftPayload::handle);
    }

    /** Fires when a player joins and after /reload: send them where every ore generates. */
    private static void sendOreGeneration(OnDatapackSyncEvent event) {
        GenerationPayload payload = new GenerationPayload(GSON.toJson(OreGeneration.scan(event.getPlayerList().getServer())));
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
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
