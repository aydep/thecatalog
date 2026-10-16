"""Adds "where it generates" text (thecatalog.worldgen.<mod>.<item>) for vanilla ores to the mod's language files.

Usage: python tools/worldgen.py
Each entry is three lines: dimension and biomes, height range, where it is most common (Minecraft 1.21.1).
"""
import collections, json, os

OW_EN, OW_RU = "Overworld, all biomes.", "Верхний мир, все биомы."
NE_EN, NE_RU = "Nether, all biomes.", "Незер, все биомы."
DEEP_EN = "Below Y 0, where stone turns to deepslate, ores generate in this form."
DEEP_RU = "Ниже Y 0, где камень сменяется глубинным сланцем, руда появляется в этом виде."

ORES = {
    "coal_ore": ((OW_EN, "Height: Y 0 to 320.", "Most common: around Y 96, and high up in mountains."),
                 (OW_RU, "Высота: от Y 0 до 320.", "Чаще всего: около Y 96 и высоко в горах.")),
    "deepslate_coal_ore": ((OW_EN, "Height: only around Y 0 to 8.", "Rare: coal mostly forms above the deepslate layer."),
                           (OW_RU, "Высота: только около Y 0–8.", "Редкая: уголь в основном лежит выше слоя глубинного сланца.")),
    "iron_ore": ((OW_EN, "Height: Y -64 to 320.", "Most common: around Y 16, and around Y 232 in mountains."),
                 (OW_RU, "Высота: от Y -64 до 320.", "Чаще всего: около Y 16, а в горах — около Y 232.")),
    "deepslate_iron_ore": ((OW_EN, "Height: Y -64 to 0.", DEEP_EN),
                           (OW_RU, "Высота: от Y -64 до 0.", DEEP_RU)),
    "copper_ore": ((OW_EN, "Height: Y -16 to 112.", "Most common: around Y 48; extra-large deposits in dripstone caves."),
                   (OW_RU, "Высота: от Y -16 до 112.", "Чаще всего: около Y 48; особенно много в капельниковых пещерах.")),
    "deepslate_copper_ore": ((OW_EN, "Height: Y -16 to 0.", DEEP_EN),
                             (OW_RU, "Высота: от Y -16 до 0.", DEEP_RU)),
    "gold_ore": ((OW_EN + " Extra gold in badlands.", "Height: Y -64 to 32; in badlands also Y 32 to 256.", "Most common: around Y -16."),
                 (OW_RU + " В бесплодных землях золота больше.", "Высота: от Y -64 до 32; в бесплодных землях ещё от Y 32 до 256.", "Чаще всего: около Y -16.")),
    "deepslate_gold_ore": ((OW_EN, "Height: Y -64 to 0.", "Most common: around Y -16. " + DEEP_EN),
                           (OW_RU, "Высота: от Y -64 до 0.", "Чаще всего: около Y -16. " + DEEP_RU)),
    "redstone_ore": ((OW_EN, "Height: Y -64 to 15.", "Most common: the deeper the better, near the bottom of the world."),
                     (OW_RU, "Высота: от Y -64 до 15.", "Чаще всего: чем глубже, тем больше, у самого дна мира.")),
    "deepslate_redstone_ore": ((OW_EN, "Height: Y -64 to 0.", "Most common: near the bottom of the world. " + DEEP_EN),
                               (OW_RU, "Высота: от Y -64 до 0.", "Чаще всего: у самого дна мира. " + DEEP_RU)),
    "emerald_ore": (("Overworld, mountain biomes only: peaks, slopes, groves, meadows, windswept hills and cherry groves.", "Height: Y -16 to 320.", "Most common: high up, around Y 236."),
                    ("Верхний мир, только горные биомы: пики, склоны, рощи, луга, ветреные холмы и вишнёвые рощи.", "Высота: от Y -16 до 320.", "Чаще всего: высоко, около Y 236.")),
    "deepslate_emerald_ore": (("Overworld, mountain biomes only.", "Height: Y -16 to 0.", "Very rare. " + DEEP_EN),
                              ("Верхний мир, только горные биомы.", "Высота: от Y -16 до 0.", "Очень редкая. " + DEEP_RU)),
    "lapis_ore": ((OW_EN, "Height: Y -64 to 64.", "Most common: around Y 0; some is buried away from air."),
                  (OW_RU, "Высота: от Y -64 до 64.", "Чаще всего: около Y 0; часть руды скрыта в толще камня.")),
    "deepslate_lapis_ore": ((OW_EN, "Height: Y -64 to 0.", "Most common: around Y 0. " + DEEP_EN),
                            (OW_RU, "Высота: от Y -64 до 0.", "Чаще всего: около Y 0. " + DEEP_RU)),
    "diamond_ore": ((OW_EN, "Height: Y -64 to 16.", "Most common: near the bottom, around Y -59; usually not exposed to air."),
                    (OW_RU, "Высота: от Y -64 до 16.", "Чаще всего: у дна, около Y -59; обычно не соприкасается с воздухом.")),
    "deepslate_diamond_ore": ((OW_EN, "Height: Y -64 to 0.", "Most common: around Y -59. Nearly all diamonds are found in this form."),
                              (OW_RU, "Высота: от Y -64 до 0.", "Чаще всего: около Y -59. Почти все алмазы встречаются именно в этом виде.")),
    "nether_gold_ore": ((NE_EN + " Twice as common in basalt deltas.", "Height: Y 10 to 117.", "Spread evenly through the netherrack."),
                        (NE_RU + " В базальтовых дельтах вдвое чаще.", "Высота: от Y 10 до 117.", "Равномерно рассеяна в незераке.")),
    "nether_quartz_ore": ((NE_EN + " Twice as common in basalt deltas.", "Height: Y 10 to 117.", "Spread evenly through the netherrack."),
                          (NE_RU + " В базальтовых дельтах вдвое чаще.", "Высота: от Y 10 до 117.", "Равномерно рассеяна в незераке.")),
    "ancient_debris": ((NE_EN, "Height: Y 8 to 119.", "Most common: around Y 15; never exposed to air, so dig or blow up tunnels."),
                       (NE_RU, "Высота: от Y 8 до 119.", "Чаще всего: около Y 15; никогда не соприкасается с воздухом, поэтому прокапывайте или взрывайте тоннели.")),
    "raw_iron_block": ((OW_EN, "Height: Y -60 to -8, inside large iron ore veins.", "The veins are long ribbons of tuff mixed with iron ore."),
                       (OW_RU, "Высота: от Y -60 до -8, внутри крупных железных жил.", "Жилы — длинные ленты туфа вперемешку с железной рудой.")),
    "raw_copper_block": ((OW_EN, "Height: Y 0 to 50, inside large copper ore veins.", "The veins are long ribbons of granite mixed with copper ore."),
                         (OW_RU, "Высота: от Y 0 до 50, внутри крупных медных жил.", "Жилы — длинные ленты гранита вперемешку с медной рудой.")),
}
UI = {"en_us": {"gui.thecatalog.worldgen": "Generation", "gui.thecatalog.trading": "Trading"},
      "ru_ru": {"gui.thecatalog.worldgen": "Генерация", "gui.thecatalog.trading": "Торговля"}}

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
for i, lang in enumerate(("en_us", "ru_ru")):
    path = os.path.join(ROOT, "src", "main", "resources", "assets", "thecatalog", "lang", f"{lang}.json")
    keys = json.load(open(path, encoding="utf-8"), object_pairs_hook=collections.OrderedDict)
    keys.update(UI[lang])
    for item, texts in ORES.items():
        keys[f"thecatalog.worldgen.minecraft.{item}"] = "\n".join(texts[i])
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(json.dumps(keys, ensure_ascii=False, indent=2) + "\n")
    print(lang, len(ORES), "worldgen entries")
