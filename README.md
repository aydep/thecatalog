# The Catalog

A vanilla-style recipe book for Minecraft 1.21.1 (NeoForge).

The Catalog adds a single book that holds every item and recipe in the game. Open it and you'll find a two-page spread
with a grid of all items, purple bookmarks for the vanilla creative tabs and for every installed mod, and a search bar
tucked under the cover. Click an item and the page turns to show how to make it; right-click to see what it's used in.
Crafting, smelting, stonecutting, smithing, brewing and modded recipe types all work out of the box. It's meant to feel
like something you'd find in a library, not a spreadsheet: a quieter alternative to JEI- or EMI-style overlays.

**JEI is an optional dependency.** If [Just Enough Items](https://www.curseforge.com/minecraft/mc-mods/jei) is installed,
The Catalog reads every recipe category JEI knows (from every mod that ships a JEI plugin: machines, fluids, info pages,
fuel and more) and draws them right inside the book, so other mods need no changes to show up. Without JEI, The Catalog
works on its own using Minecraft's recipe data. JEI's item list beside inventories can only be shown in
Creative mode; in Survival and Adventure the book replaces it.

## Features

- One book, **The Catalog**: given once on first join and craftable from a book and a crafting table.
- Item grid over two pages, with bookmarks for vanilla creative tabs and one per mod (scrollable when there are many).
- Search with `@mod` to filter by mod. Right-click the search box to clear it.
- Recipe pages with a page-turn animation and sound, Recipes / Uses toggle, and Back history.
- Lookup from any inventory: hover an item and press `R` (recipes) or `U` (uses).
- The recipe book button beside inventories, crafting tables and furnaces opens a one-page Catalog in place of the
  vanilla recipe book (item grid, search and recipes; `R` / `U` show items there while it is open).
- Recipe index built in the background, so opening the book never freezes the game, even in large modpacks.
- English and Russian.

## Controls

| Key / action | What it does |
|---|---|
| Right-click the book, or `B` | Open the Catalog (`B` works while the book is in your inventory) |
| Left-click an item | Its recipes |
| Right-click an item | Its uses |
| `R` / `U` over an item | Recipes / uses, also from inventories and chests |
| Right-click empty space, `Backspace` | Back |
| `←` `→`, `PgUp` `PgDn`, mouse wheel | Turn pages |

Keys can be changed in **Options → Controls → The Catalog**. JEI uses `R` / `U` too; rebind one pair if both react.

## Configuration

**Mods → The Catalog → Config**, or `config/thecatalog-client.toml`: bookmark side, max item rows and columns per page,
page-turn animation, speed and sound, ingredient cycle time, pause game while open, require the book for keys,
mod name in tooltips, hide items without recipes.

`config/thecatalog-common.toml` (server): `giveOnFirstJoin`.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x
- Optional: JEI 19.x for 1.21.1

The mod is needed on both the server and the client.

## Building

```
./gradlew build        # jar in build/libs/
./gradlew runClient    # dev game (includes JEI)
```

Textures are drawn by `tools/placeholders.py`; see [TEXTURES.md](TEXTURES.md) for every sprite and its size.

## License

All Rights Reserved © aydep
