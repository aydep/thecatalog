package com.aydep.thecatalog.client;

import com.aydep.thecatalog.TheCatalog;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

/** Per-player settings (config/thecatalog-client.toml), editable in-game under Mods > The Catalog > Config. */
@Mod(value = TheCatalog.MODID, dist = Dist.CLIENT)
public final class ClientConfig {
    public enum TabSide implements TranslatableEnum {
        LEFT, RIGHT;

        @Override
        public Component getTranslatedName() {
            return Component.translatable("thecatalog.configuration.tabSide." + name().toLowerCase(Locale.ROOT));
        }
    }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static final ModConfigSpec.EnumValue<TabSide> TAB_SIDE = B
            .comment("Which edge of the book the bookmark tabs are on.")
            .defineEnum("tabSide", TabSide.LEFT);
    // define() with a validator rather than defineInRange: the config screen shows a slider for any range under 256
    // values, and a number field otherwise.
    static final ModConfigSpec.ConfigValue<Integer> MAX_COLUMNS = B
            .comment("Most item columns on one page (the book gets smaller to fit a small window). Range: "
                    + BookLayout.MIN_COLUMNS + " ~ 30")
            .define("maxColumns", 9, v -> v instanceof Integer i && i >= BookLayout.MIN_COLUMNS && i <= 30);
    static final ModConfigSpec.ConfigValue<Integer> MAX_ROWS = B
            .comment("Most item rows on one page (the book gets smaller to fit a small window). Range: "
                    + BookLayout.MIN_ROWS + " ~ 30")
            .define("maxRows", 10, v -> v instanceof Integer i && i >= BookLayout.MIN_ROWS && i <= 30);
    static final ModConfigSpec.BooleanValue PAGE_TURN_ANIMATION = B
            .comment("Animate pages turning.")
            .define("pageTurnAnimation", true);
    static final ModConfigSpec.IntValue PAGE_TURN_MILLIS = B
            .comment("How long a page turn takes, in milliseconds.")
            .defineInRange("pageTurnMillis", 300, 50, 2000);
    static final ModConfigSpec.BooleanValue PAGE_TURN_SOUND = B
            .comment("Play the page turn sound.")
            .define("pageTurnSound", true);
    static final ModConfigSpec.IntValue INGREDIENT_CYCLE_MILLIS = B
            .comment("How long each option of a slot like \"any planks\" is shown, in milliseconds.")
            .defineInRange("ingredientCycleMillis", 1000, 200, 10000);
    static final ModConfigSpec.BooleanValue PAUSE_GAME = B
            .comment("Pause single-player while the book is open, like vanilla books.")
            .define("pauseGame", false);
    static final ModConfigSpec.BooleanValue REQUIRE_BOOK = B
            .comment("The open key and the recipe/uses keys only work while the Catalog is in your inventory.")
            .define("requireBookInInventory", true);
    static final ModConfigSpec.BooleanValue SHOW_MOD_NAME = B
            .comment("Show the mod name under item tooltips in the book.")
            .define("showModName", true);
    static final ModConfigSpec.BooleanValue HIDE_ITEMS_WITHOUT_RECIPES = B
            .comment("Leave items that have no recipe out of the item grid.")
            .define("hideItemsWithoutRecipes", false);

    private static final ModConfigSpec SPEC = B.build();

    public ClientConfig(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
