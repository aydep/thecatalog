# The Catalog: textures

All GUI sprites live in `src/main/resources/assets/thecatalog/textures/gui/sprites/` and are drawn by
`tools/placeholders.py`. To repaint one, replace its PNG. Keep the size, except for the 9-slice sprites (see below).

**9-slice sprites** (`cover`, `page_left`, `page_right`, `search_field`) keep their corners as drawn. Their edges and
middle are **tiled** (repeated) to fit the screen, not stretched, so patterns along an edge (stitches, dotted lines)
should repeat cleanly. The border width is set in each `.png.mcmeta`. If you change a sprite's size, update `width`
and `height` there too.

| File | Size | What it is |
|---|---|---|
| `cover.png` | 32×32 (9-slice, 8px border) | Leather cover behind the pages. It shows 7px left/right and 6px top/bottom of the pages. |
| `page_left.png` | 48×48 (9-slice, 12px border) | Left page: outer edge on the left, spine gutter with stitches on the right. The stitch pattern repeats every 6px. |
| `page_right.png` | 48×48 (9-slice, 12px border) | Right page, mirrored, without stitches. |
| `tab.png` | 32×22 | Bookmark ribbon for bookmarks on the left: swallowtail notch on the outer (left) end; the right 4px are hidden under the page. The item icon is drawn at x=8, y=3. |
| `tab_selected.png` | 32×22 | The open bookmark. It's drawn 6px further out. |
| `tab_right.png`, `tab_right_selected.png` | 32×22 | The same two, mirrored, for bookmarks on the right (config). |
| `tab_up.png`, `tab_down.png` | 12×7 | Arrows that scroll the bookmark column to the previous / next group. |
| `page_forward.png` / `_highlighted` | 18×10 | Next page arrow (normal / mouse over). |
| `page_backward.png` / `_highlighted` | 18×10 | Previous page arrow. |
| `back.png` / `back_highlighted` | 14×10 | "Back" (history) button on recipe pages. |
| `search_field.png` | 16×12 (9-slice, 3px border) | Inset field behind the search text. |
| `search_icon.png` | 9×9 | Magnifier at the left of the search field. |
| `arrow.png` | 22×15 | Ink arrow between ingredients and result. |
| `flame.png` | 14×14 | Flame under furnace inputs. |

Item: `src/main/resources/assets/thecatalog/textures/item/catalog.png`, 16×16.
Mod logo in the Mods list: `src/main/resources/logo.png`, 128×128 (the item icon at 8×, drawn without blur).

Text colors are in `CatalogScreen.java`: `INK`, `FAINT_INK`, `SEARCH_TEXT` and `SEARCH_HINT`.

Regenerate all sprites with:
`python tools/placeholders.py src/main/resources/assets/thecatalog/textures`
