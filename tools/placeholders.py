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
    """26x22 bookmark ribbon, swallowtail notch at both ends (one end is hidden under the page)."""
    def px(x, y):
        depth = 5 - int(abs(y - 10.5) / 2.2)
        end = min(x, 25 - x)
        if end < depth: return CLEAR
        if end == depth or y in (0, 21): return c["line"]
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


def flame(x, y):
    cx = abs(x - 6.5)
    if not (y > 2 and cx <= (y - 2) * 0.55 and cx < 6): return CLEAR
    return rgb("#F5C542") if y < 7 else rgb("#E8862B") if y < 11 else rgb("#C0392B")


def search_field(x, y):  # 16x12, 3px 9-slice border
    if x in (0, 15) or y in (0, 11): return SHADE3
    return SHADE2 if y == 1 else PAPER_SPECK


def magnifier(x, y):  # 9x9
    r = ((x - 3.5) ** 2 + (y - 3.5) ** 2) ** 0.5
    return INK_SOFT if 2.2 <= r <= 3.4 or (x == y and x >= 6) else CLEAR


def book_item(x, y):
    if x in (10, 11) and y >= 14: return PURPLE_SEL["body"]  # ribbon
    if not (2 <= x <= 14 and 1 <= y <= 14): return CLEAR
    if x == 14: return EDGE_LIGHT if 2 <= y <= 13 else CLEAR  # page edges
    if x == 13 and y in (1, 14): return CLEAR
    if (x >= 12 and y <= 2) or (x >= 12 and y >= 13): return GOLD
    if x == 2 or y in (1, 14) or x == 13: return OUTLINE
    if x == 3: return LEATHER_DARK
    if abs(x - 8) + abs(y - 7.5) in (2.5,): return GOLD_LIGHT  # diamond emblem
    return LEATHER


left = page(stitched=True)
sprite("page_left", 48, 48, left, nine_slice=12)
sprite("page_right", 48, 48, mirrored(page(stitched=False), 48), nine_slice=12)
sprite("cover", 32, 32, cover, nine_slice=8)
sprite("tab", 26, 22, ribbon(PURPLE))
sprite("tab_selected", 26, 22, ribbon(PURPLE_SEL))
sprite("tab_up", 12, 7, outlined(GOLD_LIGHT, triangle(12, 7, "up"), 12, 7))
sprite("tab_down", 12, 7, outlined(GOLD_LIGHT, triangle(12, 7, "down"), 12, 7))
sprite("page_forward", 18, 10, page_arrow(INK, True))
sprite("page_forward_highlighted", 18, 10, page_arrow(INK_HOT, True))
sprite("page_backward", 18, 10, page_arrow(INK, False))
sprite("page_backward_highlighted", 18, 10, page_arrow(INK_HOT, False))
sprite("back", 14, 10, back(INK))
sprite("back_highlighted", 14, 10, back(INK_HOT))
sprite("arrow", 22, 15, lambda x, y: INK_SOFT if (x < 14 and 6 <= y <= 8) or (x >= 14 and abs(y - 7) <= (22 - x)) else CLEAR)
sprite("flame", 14, 14, flame)
sprite("search_field", 16, 12, search_field, nine_slice=3)
sprite("search_icon", 9, 9, magnifier)
save(os.path.join(OUT, "item", "catalog.png"), 16, 16, book_item)
print("ok")
