# The Catalog: textures

All GUI sprites live in `src/main/resources/assets/thecatalog/textures/gui/sprites/` and are drawn by
`tools/placeholders.py`. To repaint one, replace its PNG. Keep the size, except for the 9-slice sprites (see below).

**9-slice sprites** (`cover`, `page_left`, `page_right`, `recipe_panel`, `search_tab`) keep their corners as drawn. Their edges and
middle are **tiled** (repeated) to fit the screen, not stretched, so patterns along an edge (stitches, dotted lines)
should repeat cleanly. The border width is set in each `.png.mcmeta`. If you change a sprite's size, update `width`
and `height` there too.

| File | Size | What it is |
|---|---|---|
| `cover.png` | 32×32 (9-slice, 8px border) | Leather cover behind the pages. It shows 7px left/right and 6px top/bottom of the pages. |
| `page_left.png` | 48×48 (9-slice, 12px border) | Left page: outer edge on the left, spine gutter with stitches on the right. The stitch pattern repeats every 6px. |
| `page_right.png` | 48×48 (9-slice, 12px border) | Right page, mirrored, without stitches. |
| `tab.png` | 40×22 | Bookmark ribbon for bookmarks on the left: swallowtail notch on the outer (left) end; the right 12px are hidden under the page (so the open one, shifted 6px out, still reaches under it). The item icon is drawn at x=8, y=3. |
| `tab_selected.png` | 40×22 | The open bookmark. It's drawn 6px further out. |
| `tab_right.png`, `tab_right_selected.png` | 40×22 | The same two, mirrored, for bookmarks on the right (config). |
| `tab_top.png`, `tab_top_selected.png` | 22×40 | Recipe-category bookmarks along the top edge of the right page: notch on top, bottom 12px hidden under the page. Icon at x=3, y=6. The open one is drawn 6px higher. |
| `tab_prev.png`, `tab_next.png` | 7×12 | Arrows that scroll the category bookmarks left / right. |
| `tab_up.png`, `tab_down.png` | 12×7 | Arrows that scroll the bookmark column to the previous / next group. |
| `page_forward.png` / `_highlighted` | 18×10 | Next page arrow (normal / mouse over). |
| `page_backward.png` / `_highlighted` | 18×10 | Previous page arrow. |
| `back.png` / `back_highlighted` | 14×10 | "Back" (history) button on recipe pages. |
| `search_tab.png` | 32×24 (9-slice, 8px border) | Paper tab hanging under the left page that holds the search. Its top 14px are hidden behind the page and cover. |
| `search_icon.png` | 9×9 | Magnifier at the left of the search field. |
| `slot.png` | 18×18 | Box behind each recipe ingredient. |
| `recipe_panel.png` | 16×16 (9-slice, 4px border) | Rounded panel around a recipe's ingredient slots. |
| `chevron.png` | 7×9 | ">" between the ingredient panel and the result. (The "}" brace before it is drawn in code, color `BRACE`.) |
| `output_frame.png` | 24×24 | Framed slot for the recipe's result; the item sits at x=4, y=4. |
| `flame.png` | 14×14 | Flame under furnace inputs. |
| `craft.png` / `craft_highlighted` | 11×11 | "+" beside a recipe in the side panel: puts its ingredients into the crafting grid or furnace. |
| `tree.png` / `tree_highlighted` | 11×11 | Recipe tree button at the right of the item's name on recipe pages. (Tree steps are framed with `recipe_panel`.) |

Item: `src/main/resources/assets/thecatalog/textures/item/catalog.png`, 16×16.
Recipe book button (replaces vanilla's, so every inventory shows the Catalog): `src/main/resources/assets/minecraft/textures/gui/sprites/recipe_book/button.png` and `button_highlighted.png`, 20×18.
Mod logo in the Mods list: `src/main/resources/logo.png`, 128×128 (the item icon at 8×, drawn without blur).

Colors in `CatalogScreen.java`: `INK`, `FAINT_INK`, `SEARCH_TEXT`, `SEARCH_HINT` and `BRACE`.

Regenerate all sprites with:
`python tools/placeholders.py src/main/resources/assets/thecatalog/textures`
