"""Generate a labeled grid view of AE2-CS icons.png so we can identify the gear."""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "tools" / "refs" / "ae2cs_icons.png"
OUT = ROOT / "tools" / "previews" / "_ae2cs_icons_grid.png"

CELL = 16
SCALE = 4  # zoom each icon
LABEL_H = 14
GUTTER = 4

img = np.array(Image.open(SRC).convert("RGBA"))
# Scan full 16×16 cell grid (256/16 = 16 cells in each axis)
COLS = 16
ROWS = 16

cell_px = CELL * SCALE
out_w = COLS * (cell_px + GUTTER) + GUTTER
out_h = ROWS * (cell_px + LABEL_H + GUTTER) + GUTTER

canvas = Image.new("RGBA", (out_w, out_h), (40, 40, 40, 255))
draw = ImageDraw.Draw(canvas)

try:
    font = ImageFont.truetype("arial.ttf", 9)
except Exception:
    font = ImageFont.load_default()

for gy in range(ROWS):
    for gx in range(COLS):
        sx, sy = gx * CELL, gy * CELL
        cell_arr = img[sy:sy + CELL, sx:sx + CELL, :]
        # Skip fully empty cells (so we don't waste preview real estate visually)
        cell_img = Image.fromarray(cell_arr, "RGBA").resize((cell_px, cell_px), Image.NEAREST)
        x = GUTTER + gx * (cell_px + GUTTER)
        y = GUTTER + gy * (cell_px + LABEL_H + GUTTER) + LABEL_H
        canvas.paste(cell_img, (x, y), cell_img)
        # Label = (gx, gy) i.e. grid coord (matches copy_icon_cell semantics)
        label = f"({gx},{gy})"
        draw.rectangle([x, y - LABEL_H, x + cell_px, y], fill=(0, 0, 0, 255))
        draw.text((x + 2, y - LABEL_H), label, fill=(220, 220, 220, 255), font=font)

canvas.save(OUT)
print(f"Wrote {OUT}  size={canvas.size}")
