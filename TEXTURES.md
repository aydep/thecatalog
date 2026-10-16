# The Catalog: textures to draw

Replace the placeholder PNGs in place. All GUI sprites live in
`src/main/resources/assets/thecatalog/textures/gui/sprites/`. Sizes are in pixels and must stay the same,
except for the two pages, which are 9-slice textures and stretch to fit the screen.

| File | Size | What it is |
|---|---|---|
| `page_left.png` | 32×32 (9-slice, 6px border) | Left page with the cover edge on the left and the spine on the right. Corners/edges stay crisp; the middle 20×20 is stretched. Change `border` in `page_left.png.mcmeta` if your art needs a different border (also update `width`/`height` there if you change the size). |
| `page_right.png` | 32×32 (9-slice, 6px border) | Right page, mirrored. |
| `tab.png` | 26×22 | Bookmark tab, on the left edge of the book. The right 4px are hidden under the page. The item icon is drawn at x=3, y=3. |
| `tab_selected.png` | 26×22 | The tab that's open. It's drawn 3px further out (to the left). |
| `tab_up.png`, `tab_down.png` | 12×7 | Arrows that scroll the bookmark column to the previous / next group. |
| `page_forward.png` / `_highlighted` | 18×10 | Next page arrow (normal / mouse over). |
| `page_backward.png` / `_highlighted` | 18×10 | Previous page arrow. |
| `back.png` / `back_highlighted` | 14×10 | "Back" (history) button on recipe pages. |
| `arrow.png` | 22×15 | Ink arrow between ingredients and result. |
| `flame.png` | 14×14 | Flame under furnace inputs. |

Item: `src/main/resources/assets/thecatalog/textures/item/catalog.png`, 16×16.

Text colors are set in `CatalogScreen.java`: `INK = 0xFF3B2A1A` and `FAINT_INK = 0xFF8A7458`. Change them if they don't
suit your paper color.

Regenerate the placeholders with:
`python tools/placeholders.py src/main/resources/assets/thecatalog/textures` (the script is not part of the mod).
