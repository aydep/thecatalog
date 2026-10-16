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
| `tab.png` | 26×22 | Bookmark ribbon lying on the cover. 4px of the inner end are hidden under the page, so both ends are notched and the same sprite works for bookmarks on the left or right. The item icon is drawn at x=5, y=3. |
| `tab_selected.png` | 26×22 | The open bookmark. It's drawn 3px further out. |
| `tab_up.png`, `tab_down.png` | 12×7 | Arrows that scroll the bookmark column to the previous / next group. |
| `page_forward.png` / `_highlighted` | 18×10 | Next page arrow (normal / mouse over). |
| `page_backward.png` / `_highlighted` | 18×10 | Previous page arrow. |
| `back.png` / `back_highlighted` | 14×10 | "Back" (history) button on recipe pages. |
| `search_field.png` | 16×12 (9-slice, 3px border) | Inset field behind the search text. |
| `search_icon.png` | 9×9 | Magnifier at the left of the search field. |
| `arrow.png` | 22×15 | Ink arrow between ingredients and result. |
| `flame.png` | 14×14 | Flame under furnace inputs. |

Item: `src/main/resources/assets/thecatalog/textures/item/catalog.png`, 16×16.

Text colors are in `CatalogScreen.java`: `INK`, `FAINT_INK`, `SEARCH_TEXT` and `SEARCH_HINT`.

Regenerate all sprites with:
`python tools/placeholders.py src/main/resources/assets/thecatalog/textures`
