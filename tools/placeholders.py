"""Draws the GUI sprites for The Catalog. Usage: python tools/placeholders.py src/main/resources/assets/thecatalog/textures"""
import json, os, struct, sys, zlib

OUT = sys.argv[1]
CLEAR = (0, 0, 0, 0)


def rgb(h):
    return (int(h[1:3], 16), int(h[3:5], 16), int(h[5:7], 16), 255)


# palette
PAPER, PAPER_SPECK = rgb("#FBF3E0"), rgb("#F4EAD2")
EDGE_DARK, EDGE_LIGHT = rgb("#B89C6A"), rgb("#ECDDB8")
SHADE1, SHADE2, SHADE3 = rgb("#F1E5C8"), rgb("#E6D6B0"), rgb("#D8C49A")
DOT, ORNAMENT, STITCH = rgb("#DCC89C"), rgb("#C4A877"), rgb("#6B3A22")
LEATHER, LEATHER_SPECK, LEATHER_LIGHT, LEATHER_DARK, OUTLINE = (
    rgb("#4E3421"), rgb("#553A25"), rgb("#6E4C30"), rgb("#3A2618"), rgb("#22150B"))
GOLD, GOLD_LIGHT, GOLD_DARK = rgb("#C9973A"), rgb("#F2CF6E"), rgb("#8E6623")
PURPLE = dict(body=rgb("#5E3B82"), light=rgb("#8A66B0"), shade=rgb("#46296A"), seam=rgb("#7A55A0"), line=rgb("#2A1840"))
PURPLE_SEL = dict(body=rgb("#7D52AA"), light=rgb("#AC88D2"), shade=rgb("#5E3B82"), seam=rgb("#9670C0"), line=rgb("#2A1840"))
INK, INK_HOT, INK_SOFT = rgb("#6B4A2E"), rgb("#B5452B"), rgb("#9C8463")


def speck(x, y):  # deterministic sparse noise
    return (x * 73 + y * 151 + x * y * 7) % 23 == 0


def save(path, w, h, px):
    raw = b"".join(b"\0" + b"".join(bytes(px(x, y)) for x in range(w)) for y in range(h))
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def sprite(name, w, h, px, nine_slice=None):
    path = os.path.join(OUT, "gui", "sprites", name + ".png")
    for stale in (path + ".mcmeta",):
        if os.path.exists(stale): os.remove(stale)
    save(path, w, h, px)
    if nine_slice:
        with open(path + ".mcmeta", "w") as f:
            json.dump({"gui": {"scaling": {"type": "nine_slice", "width": w, "height": h, "border": nine_slice}}}, f, indent=2)


def page(stitched):
    """48x48 left page (spine on the right), 12px 9-slice border; edges tile, so patterns repeat every 6 / 2 px."""
    def px(x, y):
        if x == 0 or y in (0, 47): return EDGE_DARK
        if x == 1 or y in (1, 46): return EDGE_LIGHT
        if stitched and 3 <= y <= 44 and x in (45, 46):  # "(" stitches along the spine
            return STITCH if (y % 6, x) in ((1, 46), (2, 45), (3, 45), (4, 46)) else SHADE3
        if x >= 45: return SHADE3
        if x >= 42: return SHADE2
        if x >= 38: return SHADE1
        corner = min(x, 47 - x), min(y, 47 - y)
        if x < 12 and corner[1] < 12 and (corner[0], corner[1]) in ((5, 5), (6, 5), (7, 5), (5, 6), (5, 7), (7, 7), (8, 8)):
            return ORNAMENT  # flourish in the outer corners
        if (y in (4, 43) and 4 <= x <= 36) or (x == 4 and 4 <= y <= 43):
            return DOT if (x + y) % 2 == 0 else PAPER  # dotted inner border
        return PAPER_SPECK if speck(x, y) else PAPER
    return px


def mirrored(px, w):
    return lambda x, y: px(w - 1 - x, y)


def cover(x, y):
    """32x32 leather cover with gold corner caps, 8px 9-slice border."""
    for cx, cy in ((x, y), (31 - x, y), (x, 31 - y), (31 - x, 31 - y)):
        if cx + cy <= 6:
            return GOLD_LIGHT if cx + cy <= 2 else GOLD_DARK if cx + cy == 6 else GOLD
    if x in (0, 31) or y in (0, 31): return OUTLINE
    if x == 1 or y == 1: return LEATHER_LIGHT
    if x == 30 or y == 30: return LEATHER_DARK
    return LEATHER_SPECK if speck(x, y) else LEATHER


def ribbon(c):
    """40x22 bookmark ribbon for the left edge: swallowtail notch on the outer (left) end; the right 12px sit under the page."""
    def px(x, y):
        depth = 5 - int(abs(y - 10.5) / 2.2)
        if x < depth: return CLEAR
        if x == depth or y in (0, 21): return c["line"]
        if y == 1: return c["light"]
        if y >= 19: return c["shade"]
        if y in (4, 17) and x % 3: return c["seam"]
        return c["body"]
    return px


def outlined(fill, inside, w, h):
    def px(x, y):
        if inside(x, y): return fill
        near = any(0 <= x + dx < w and 0 <= y + dy < h and inside(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        return OUTLINE if near else CLEAR
    return px


def triangle(w, h, pointing):
    def inside(x, y):
        if pointing in ("left", "right"):
            d = x if pointing == "right" else w - 1 - x
            return abs(y - (h - 1) / 2) <= (h / 2) * (1 - d / w)
        d = y if pointing == "down" else h - 1 - y
        return abs(x - (w - 1) / 2) <= (w / 2) * (1 - d / h)
    return inside


def page_arrow(color, right):  # 18x10: shaft + head
    head = triangle(8, 10, "right" if right else "left")
    def px(x, y):
        hx = x - 10 if right else x
        if 0 <= hx < 8 and head(hx, y): return color
        sx = x if right else x - 8
        return color if 0 <= sx < 10 and 4 <= y <= 5 else CLEAR
    return px


def back(color):  # 14x10: hook arrow pointing left
    head = triangle(5, 7, "left")
    def px(x, y):
        if x < 5 and 3 <= y < 10 and head(x, y - 3): return color
        if (4 <= x <= 11 and 6 <= y <= 7) or (10 <= x <= 11 and 1 <= y <= 7) or (6 <= x <= 11 and 0 <= y <= 1): return color
        return CLEAR
    return px


SLOT_FILL, SLOT_DARK, SLOT_LIGHT, SLOT_RIM, PANEL_FILL = (
    rgb("#EFE3C6"), rgb("#D2BD92"), rgb("#FFF9EC"), rgb("#E6D7B4"), rgb("#F7EEDB"))


def slot(x, y):  # 18x18 inset slot box
    if x in (0, 17) or y in (0, 17): return SLOT_RIM
    if x == 1 or y == 1: return SLOT_DARK
    if x == 16 or y == 16: return SLOT_LIGHT
    return SLOT_FILL


def rounded(w, h, fill):
    def px(x, y):
        cx, cy = min(x, w - 1 - x), min(y, h - 1 - y)
        if cx + cy < 2: return CLEAR
        if cx == 0 or cy == 0 or cx + cy == 2: return SLOT_DARK
        if cx == 1 or cy == 1: return SLOT_LIGHT
        return fill
    return px


def output_frame(x, y):  # 24x24: rounded frame with a slot inside
    return slot(x - 3, y - 3) if 3 <= x <= 20 and 3 <= y <= 20 else rounded(24, 24, PANEL_FILL)(x, y)


def chevron(x, y):  # 7x9 ">"
    d = 4 - abs(y - 4)
    return ORNAMENT if x in (d, d + 1) else CLEAR


def search_tab(x, y):  # 32x24, 8px 9-slice border; the top hides behind the cover, the bottom corners are rounded
    cx, cy = min(x, 31 - x), 23 - y
    if cx + cy < 2: return CLEAR
    if cx == 0 or cy == 0 or cx + cy == 2: return EDGE_DARK
    if cx == 1 or cy == 1: return EDGE_LIGHT
    return PAPER_SPECK if speck(x, y) else PAPER


def flame(x, y):
    cx = abs(x - 6.5)
    if not (y > 2 and cx <= (y - 2) * 0.55 and cx < 6): return CLEAR
    return rgb("#F5C542") if y < 7 else rgb("#E8862B") if y < 11 else rgb("#C0392B")


def magnifier(x, y):  # 9x9
    r = ((x - 3.5) ** 2 + (y - 3.5) ** 2) ** 0.5
    return INK_SOFT if 2.2 <= r <= 3.4 or (x == y and x >= 6) else CLEAR


def inside(poly, x, y):  # pixel centre inside a convex polygon given in pixel-corner coordinates
    px, py, sign = x + .5, y + .5, 0
    for (x1, y1), (x2, y2) in zip(poly, poly[1:] + poly[:1]):
        c = (x2 - x1) * (py - y1) - (y2 - y1) * (px - x1)
        if c:
            if sign and (c > 0) != (sign > 0): return False
            sign = c
    return True


# An isometric tome: leather top cover A-B-C-D; below the two front edges a side band of
# cover / pages / back cover; three bookmarks hang out of the long front side.
A, B, C, D, SIDE = (0, 6), (10, 1), (16, 4), (6, 9), 6
TOP = [A, B, C, D]
LEFT_SIDE, RIGHT_SIDE = [A, D, (D[0], D[1] + SIDE), (A[0], A[1] + SIDE)], [D, C, (C[0], C[1] + SIDE), (D[0], D[1] + SIDE)]
COVER_RED, COVER_RED_LIGHT, COVER_RED_DARK, BACK = rgb("#7A4528"), rgb("#9A5C36"), rgb("#4E2C1A"), rgb("#3A2012")
GEM, GEM_DARK = rgb("#5FD3E0"), rgb("#2E8C9A")
RIBBONS = {9: (rgb("#C04040"), rgb("#8E2A2A")), 11: (rgb("#3AA86A"), rgb("#2A7A4C")), 13: (rgb("#4A7AC8"), rgb("#2A4E8A"))}


def edge_y(x):  # y of the top cover's front edge (A-D then D-C) at pixel column x
    cx = x + .5
    return A[1] + (cx - A[0]) * 0.5 if cx <= D[0] else D[1] - (cx - D[0]) * 0.5


def tome_fill(x, y):
    if inside(TOP, x, y):
        for cx, cy in TOP:  # gold corner caps
            if abs(x + .5 - cx) / 2 + abs(y + .5 - cy) < 1.3: return GOLD
        if 6 <= x <= 9 and 4 <= y <= 5:  # a gem in the middle, the knowledge inside, lit from the top left
            return GEM_DARK if x >= 8 and y == 5 else GEM
        if (6 <= x <= 9 and y in (3, 6)) or (x in (5, 10) and 4 <= y <= 5): return GOLD_LIGHT  # gold setting all around
        back_edge = A[1] - (x + .5 - A[0]) * 0.5  # A-B line, lit like a rounded spine
        return COVER_RED_LIGHT if y + .5 - back_edge < 1.5 else COVER_RED
    if inside(LEFT_SIDE, x, y) or inside(RIGHT_SIDE, x, y):
        row = int(y + .5 - edge_y(x))
        ribbon = RIBBONS.get(x)
        if ribbon and row >= SIDE - 2: return ribbon[0]  # bookmarks come out between the lowest pages
        if row == 0: return COVER_RED_DARK
        if row == SIDE - 1: return BACK
        if x + .5 < D[0]: return SHADE3 if row == 3 else SHADE2  # left side is in shadow
        return SHADE2 if row == 3 else PAPER
    x0 = x
    if x0 in RIBBONS and 0 <= y + .5 - (edge_y(x0) + SIDE) < 2: return RIBBONS[x0][1]  # ribbon tails below the book
    return None


def tome(x, y):
    fill = tome_fill(x, y)
    if fill is None: return CLEAR
    if fill in (r[1] for r in RIBBONS.values()) and not (inside(LEFT_SIDE, x, y) or inside(RIGHT_SIDE, x, y)):
        return fill  # tails have no outline
    edge = any(not (0 <= x + dx < 16 and 0 <= y + dy < 16) or tome_fill(x + dx, y + dy) is None
               for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    return OUTLINE if edge else fill


left = page(stitched=True)
sprite("page_left", 48, 48, left, nine_slice=12)
sprite("page_right", 48, 48, mirrored(page(stitched=False), 48), nine_slice=12)
sprite("cover", 32, 32, cover, nine_slice=8)
sprite("tab", 40, 22, ribbon(PURPLE))
sprite("tab_selected", 40, 22, ribbon(PURPLE_SEL))
sprite("tab_right", 40, 22, mirrored(ribbon(PURPLE), 40))
sprite("tab_right_selected", 40, 22, mirrored(ribbon(PURPLE_SEL), 40))
# category bookmarks along the top edge: the side ribbon stood upright, notch on top, bottom 12px under the page
sprite("tab_top", 22, 40, lambda x, y: ribbon(PURPLE)(y, x))
sprite("tab_top_selected", 22, 40, lambda x, y: ribbon(PURPLE_SEL)(y, x))
sprite("tab_prev", 7, 12, outlined(GOLD_LIGHT, triangle(7, 12, "left"), 7, 12))
sprite("tab_next", 7, 12, outlined(GOLD_LIGHT, triangle(7, 12, "right"), 7, 12))
sprite("tab_up", 12, 7, outlined(GOLD_LIGHT, triangle(12, 7, "up"), 12, 7))
sprite("tab_down", 12, 7, outlined(GOLD_LIGHT, triangle(12, 7, "down"), 12, 7))
sprite("page_forward", 18, 10, page_arrow(INK, True))
sprite("page_forward_highlighted", 18, 10, page_arrow(INK_HOT, True))
sprite("page_backward", 18, 10, page_arrow(INK, False))
sprite("page_backward_highlighted", 18, 10, page_arrow(INK_HOT, False))
sprite("back", 14, 10, back(INK))
sprite("back_highlighted", 14, 10, back(INK_HOT))
sprite("slot", 18, 18, slot)
sprite("recipe_panel", 16, 16, rounded(16, 16, PANEL_FILL), nine_slice=4)
sprite("output_frame", 24, 24, output_frame)
sprite("chevron", 7, 9, chevron)
sprite("flame", 14, 14, flame)
sprite("search_tab", 32, 24, search_tab, nine_slice=8)
sprite("search_icon", 9, 9, magnifier)
save(os.path.join(OUT, "item", "catalog.png"), 16, 16, tome)


def craft(color):  # 11x11 "+" on a small paper button
    box = rounded(11, 11, PANEL_FILL)
    return lambda x, y: color if (x == 5 and 3 <= y <= 7) or (y == 5 and 3 <= x <= 7) else box(x, y)


def tree_icon(color):  # 11x11: a root box with lines down to two children
    def px(x, y):
        boxes = ((4, 0), (0, 8), (8, 8))
        if any(bx <= x <= bx + 2 and by <= y <= by + 2 for bx, by in boxes): return color
        if (x == 5 and 3 <= y <= 5) or (y == 5 and 1 <= x <= 9) or (x in (1, 9) and 6 <= y <= 7): return color
        return CLEAR
    return px


sprite("craft", 11, 11, craft(INK))
sprite("craft_highlighted", 11, 11, craft(INK_HOT))
sprite("tree", 11, 11, tree_icon(INK))
sprite("tree_highlighted", 11, 11, tree_icon(INK_HOT))
# the vanilla recipe book button (20x18, overridden in the minecraft namespace) shows the Catalog instead
button = lambda x, y: tome(x - 2, y - 1) if 2 <= x < 18 and 1 <= y < 17 else CLEAR
VANILLA = os.path.join(OUT, "..", "..", "minecraft", "textures", "gui", "sprites", "recipe_book")
save(os.path.join(VANILLA, "button.png"), 20, 18, button)
save(os.path.join(VANILLA, "button_highlighted.png"), 20, 18, lambda x, y: (255, 255, 255, 255) if button(x, y) == OUTLINE else button(x, y))
# mod logo for the Mods list: the item icon at 8x (OUT is .../assets/thecatalog/textures, the logo goes in resources/)
save(os.path.join(OUT, "..", "..", "..", "logo.png"), 128, 128, lambda x, y: tome(x // 8, y // 8))
print("ok")
