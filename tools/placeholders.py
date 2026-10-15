"""Writes placeholder GUI sprites for The Catalog. Usage: python placeholders.py <resources/assets/thecatalog/textures>"""
import json, os, struct, sys, zlib

OUT = sys.argv[1]
PAPER, EDGE, SPINE, COVER = (233, 221, 184, 255), (196, 176, 130, 255), (170, 148, 105, 255), (90, 58, 30, 255)
INK, RED, CLEAR = (59, 42, 26, 255), (140, 40, 26, 255), (0, 0, 0, 0)


def png(path, w, h, px):
    raw = b"".join(b"\0" + b"".join(bytes(px(x, y)) for x in range(w)) for y in range(h))
    chunk = lambda t, d: struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def sprite(name, w, h, px, nine_slice=None):
    path = os.path.join(OUT, "gui", "sprites", name + ".png")
    png(path, w, h, px)
    if nine_slice:
        with open(path + ".mcmeta", "w") as f:
            json.dump({"gui": {"scaling": {"type": "nine_slice", "width": w, "height": h, "border": nine_slice}}}, f, indent=2)


def page(spine_on_right):
    def px(x, y):
        x = x if spine_on_right else 31 - x
        if x < 2 or y < 1 or y > 30: return COVER
        if x < 3 or y < 2 or y > 29: return EDGE
        if x > 28: return SPINE
        return PAPER
    return px


def triangle(w, h, color, pointing):  # an arrow head pointing left/right/up/down filling the box
    def px(x, y):
        if pointing in ("left", "right"):
            d = x if pointing == "right" else w - 1 - x
            return color if abs(y - (h - 1) / 2) <= (h / 2) * (1 - d / w) else CLEAR
        d = y if pointing == "down" else h - 1 - y
        return color if abs(x - (w - 1) / 2) <= (w / 2) * (1 - d / h) else CLEAR
    return px


def page_arrow(color, right):  # 18x10: shaft + head
    head = triangle(8, 10, color, "right" if right else "left")
    def px(x, y):
        hx = x - 10 if right else x
        if 0 <= hx < 8 and head(hx, y) != CLEAR: return color
        sx = x if right else x - 8
        return color if 0 <= sx < 10 and 4 <= y <= 5 else CLEAR
    return px


def back(color):  # 14x10: hook arrow pointing left
    head = triangle(5, 7, color, "left")
    def px(x, y):
        if x < 5 and 3 <= y < 10 and head(x, y - 3) != CLEAR: return color
        if 4 <= x <= 11 and 6 <= y <= 7: return color
        if 10 <= x <= 11 and 1 <= y <= 7: return color
        if 6 <= x <= 11 and 0 <= y <= 1: return color
        return CLEAR
    return px


def tab(fill):
    return lambda x, y: COVER if x in (0, 25) or y in (0, 21) else fill


def flame(x, y):
    cx = abs(x - 6.5)
    return RED if y > 3 and cx <= (y - 3) * 0.6 and cx < 6 else CLEAR


def book(x, y):
    if 2 <= x <= 13 and 1 <= y <= 14:
        if x == 2 or y in (1, 14): return COVER
        if x == 3: return (60, 38, 20, 255)
        if 6 <= x <= 11 and 4 <= y <= 6: return (212, 175, 55, 255)
        return (120, 78, 40, 255)
    if 13 < x <= 14 and 2 <= y <= 13: return PAPER
    return CLEAR


sprite("page_left", 32, 32, page(True), nine_slice=6)
sprite("page_right", 32, 32, page(False), nine_slice=6)
sprite("tab", 26, 22, tab((139, 90, 43, 255)))
sprite("tab_selected", 26, 22, tab((176, 122, 62, 255)))
sprite("page_forward", 18, 10, page_arrow(INK, True))
sprite("page_forward_highlighted", 18, 10, page_arrow(RED, True))
sprite("page_backward", 18, 10, page_arrow(INK, False))
sprite("page_backward_highlighted", 18, 10, page_arrow(RED, False))
sprite("tab_up", 12, 7, triangle(12, 7, INK, "up"))
sprite("tab_down", 12, 7, triangle(12, 7, INK, "down"))
sprite("back", 14, 10, back(INK))
sprite("back_highlighted", 14, 10, back(RED))
sprite("arrow", 22, 15, lambda x, y: INK if (x < 14 and 6 <= y <= 8) or (x >= 14 and abs(y - 7) <= (22 - x)) else CLEAR)
sprite("flame", 14, 14, flame)
png(os.path.join(OUT, "item", "catalog.png"), 16, 16, book)
print("ok")
