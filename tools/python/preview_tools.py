#!/usr/bin/env python3
"""VirtualCraft preview tools — Python port of 7 Java preview helpers.

Subcommands (each maps 1:1 to a Java tool):

    reference       Ae2csReference.java       Tile AE2-CS sample sprites into a reference sheet
    assets          AssetPreview.java          Tile the 13 generated VirtualCraft assets at 8× zoom
    arrow_sprite    ArrowSpritePreview.java   Crop & zoom the progress arrow sprite
    cells           CellPreview.java          Crop & zoom arbitrary 16×16 cells from icons.png
    on_bg           PreviewOnWhite.java       Alpha-blend a transparent PNG onto any solid bg + zoom
    sprite_v_fill   SpritePreview.java        Crop & zoom the vertical FE-fill sprite from icons.png
    annotated       WidgetsAnnotated.java     Show icons.png with grid + section labels

Examples:

    python tools/preview_tools.py reference
    python tools/preview_tools.py assets
    python tools/preview_tools.py arrow_sprite
    python tools/preview_tools.py cells out.png 0,7 1,7
    python tools/preview_tools.py on_bg widgets.png out.png 2 CBCCD4
    python tools/preview_tools.py sprite_v_fill
    python tools/preview_tools.py annotated

Note: text rendering uses Pillow's font system; visual layout matches Java but
exact font metrics may differ by 1-2 px depending on Pillow/system fonts. These
tools produce previews for human inspection, NOT pixel-comparable artifacts.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

# Layout: workspace_root/tools/python/preview_tools.py
#         ROOT = .parents[2], TOOLS_DIR = .parents[1]
ROOT = Path(__file__).resolve().parents[2]
TOOLS_DIR = Path(__file__).resolve().parents[1]
PREVIEWS_DIR = TOOLS_DIR / "previews"
GUI_DIR = ROOT / "src" / "main" / "resources" / "assets" / "virtual_craft" / "textures" / "gui"
ITEM_DIR = ROOT / "src" / "main" / "resources" / "assets" / "virtual_craft" / "textures" / "item"
BLOCK_DIR = ROOT / "src" / "main" / "resources" / "assets" / "virtual_craft" / "textures" / "block"


# ============================================================
# Shared helpers
# ============================================================
def _load_font(size: int, bold: bool = False) -> ImageFont.ImageFont:
    """Try Consolas (Windows) first, fall back to DejaVu Sans Mono, then PIL default."""
    candidates = []
    if bold:
        candidates += [
            "C:/Windows/Fonts/consolab.ttf",
            "C:/Windows/Fonts/arialbd.ttf",
            "DejaVuSansMono-Bold.ttf",
        ]
    else:
        candidates += [
            "C:/Windows/Fonts/consola.ttf",
            "C:/Windows/Fonts/arial.ttf",
            "DejaVuSansMono.ttf",
        ]
    for path in candidates:
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()


def _alpha_blend_scale(src_arr: np.ndarray, scale: int, bg_rgb: tuple[int, int, int]) -> np.ndarray:
    """Alpha-blend an RGBA array onto a solid bg + nearest-neighbour ``scale`` zoom.

    Matches the Java per-pixel ``Math.round(c * a + bg * (1 - a))`` formula exactly,
    then nearest-neighbour expands each src pixel to ``scale × scale`` block. Returns
    an opaque RGBA array (alpha=255 everywhere).
    """
    h, w = src_arr.shape[:2]
    bg = np.array([*bg_rgb, 255], dtype=np.float32)
    src_f = src_arr.astype(np.float32)
    a = src_f[..., 3:4] / 255.0
    blended_rgb = src_f[..., :3] * a + bg[:3] * (1.0 - a)
    # Java uses Math.round (half-away-from-zero for positive); numpy floor(x+0.5) matches
    blended_rgb = np.floor(blended_rgb + 0.5).astype(np.uint8)
    out = np.empty((h, w, 4), dtype=np.uint8)
    out[..., :3] = blended_rgb
    out[..., 3] = 255
    if scale > 1:
        out = np.repeat(np.repeat(out, scale, axis=0), scale, axis=1)
    return out


def _draw_checkerboard(arr: np.ndarray, x: int, y: int, w: int, h: int,
                       sq: int = 8,
                       dark: tuple = (0x60, 0x60, 0x68, 0xFF),
                       light: tuple = (0x80, 0x80, 0x88, 0xFF)) -> None:
    """Paint an 8×8 checkerboard pattern onto ``arr`` at ``(x, y)`` size ``w × h``."""
    for yy in range(0, h, sq):
        for xx in range(0, w, sq):
            color = dark if ((xx // sq) + (yy // sq)) % 2 == 0 else light
            x_max = min(xx + sq, w)
            y_max = min(yy + sq, h)
            arr[y + yy:y + y_max, x + xx:x + x_max] = color


def _truncate_label(name: str, max_len: int = 22) -> str:
    return name[: max_len - 1] + "…" if len(name) > max_len else name


def _save(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    print(f"Wrote {path} ({img.width}×{img.height})")


# ============================================================
# Subcommand: reference  (Ae2csReference.java)
# ============================================================
AE2_REFERENCE_ITEM_DIR = TOOLS_DIR / "ae2cs" / "extracted" / "assets" / "ae2cs" / "textures" / "item"
AE2_REFERENCE_BLOCK_DIR = TOOLS_DIR / "ae2cs" / "extracted" / "assets" / "ae2cs" / "textures" / "block"

AE2_REFERENCE_SAMPLES = [
    # cards / upgrades / tools
    ("crystal_growth_card", AE2_REFERENCE_ITEM_DIR),
    ("integrated_interface_upgrade", AE2_REFERENCE_ITEM_DIR),
    ("ender_linker", AE2_REFERENCE_ITEM_DIR),
    ("resonating_pattern_converter", AE2_REFERENCE_ITEM_DIR),
    ("meteor_pattern_provider_upgrade", AE2_REFERENCE_ITEM_DIR),
    ("crystal_growth_card", AE2_REFERENCE_ITEM_DIR),
    # processors
    ("simple_processor", AE2_REFERENCE_ITEM_DIR),
    ("resonating_processor", AE2_REFERENCE_ITEM_DIR),
    ("resonating_pattern", AE2_REFERENCE_ITEM_DIR),
    ("resonating_circuit_print", AE2_REFERENCE_ITEM_DIR),
    ("resonating_print_press", AE2_REFERENCE_ITEM_DIR),
    ("blank_print_press", AE2_REFERENCE_ITEM_DIR),
    # crystals
    ("purified_fluix_crystal", AE2_REFERENCE_ITEM_DIR),
    ("purified_resonating_crystal", AE2_REFERENCE_ITEM_DIR),
    ("purified_certus_quartz_crystal", AE2_REFERENCE_ITEM_DIR),
    ("purified_quantum_crystal", AE2_REFERENCE_ITEM_DIR),
    ("purified_redstone_crystal", AE2_REFERENCE_ITEM_DIR),
    ("purified_ember_crystal", AE2_REFERENCE_ITEM_DIR),
    # blocks
    ("silicon_block", AE2_REFERENCE_BLOCK_DIR),
    ("resonating_crystal_block", AE2_REFERENCE_BLOCK_DIR),
    ("redstone_crystal_block", AE2_REFERENCE_BLOCK_DIR),
    ("meteor_crystal_block", AE2_REFERENCE_BLOCK_DIR),
    ("quantum_crystal_block", AE2_REFERENCE_BLOCK_DIR),
    ("rose_quartz_block", AE2_REFERENCE_BLOCK_DIR),
]


def cmd_reference(_args) -> int:
    """Tile AE2-CS reference sprites into an 8× zoomed sheet."""
    return _tile_sheet(
        title="AE2 Crystal Science Reference (8x scale)",
        bg=(0x1A, 0x1A, 0x22),
        cols=6,
        samples=AE2_REFERENCE_SAMPLES,
        out_path=PREVIEWS_DIR / "ae2cs_reference.png",
    )


# ============================================================
# Subcommand: assets  (AssetPreview.java)
# ============================================================
ASSET_PREVIEW_ITEMS = [
    ("electric_ingot", ITEM_DIR),
    ("hash_voucher", ITEM_DIR),
    ("electric_absorber", ITEM_DIR),
    ("electric_absorber_on", ITEM_DIR),
    ("mining_surveyor", ITEM_DIR),
    ("speed_upgrade_card", ITEM_DIR),
    ("wireless_transfer_upgrade_card", ITEM_DIR),
    ("link_card", ITEM_DIR),
    ("electric_ball_generator", BLOCK_DIR),
    ("wireless_power_tower", BLOCK_DIR),
    ("holographic_shop", BLOCK_DIR),
    ("pow_miner", BLOCK_DIR),
]


def cmd_assets(_args) -> int:
    """Tile the 12 active VirtualCraft assets at 8× zoom + label each cell."""
    return _tile_sheet(
        title="VirtualCraft Asset Preview (8x scale)",
        bg=(0x20, 0x20, 0x28),
        cols=5,
        samples=ASSET_PREVIEW_ITEMS,
        out_path=PREVIEWS_DIR / "asset_preview.png",
        cell_border=(0x30, 0x30, 0x40),
    )


# ============================================================
# Shared sheet tiler (powers reference & assets)
# ============================================================
def _tile_sheet(*, title: str, bg: tuple[int, int, int], cols: int,
                samples: list, out_path: Path,
                cell_border: tuple[int, int, int] | None = None,
                scale: int = 8) -> int:
    cell = 16 * scale  # 128
    label_h = 18
    cell_total_h = cell + label_h
    padding = 8

    rows = (len(samples) + cols - 1) // cols
    W = cols * cell + (cols + 1) * padding
    H = rows * cell_total_h + (rows + 1) * padding + 24

    out = Image.new("RGBA", (W, H), (*bg, 255))
    draw = ImageDraw.Draw(out)

    title_font = _load_font(14, bold=True)
    label_font = _load_font(10, bold=False)

    draw.text((padding, 4), title, fill=(255, 255, 255, 255), font=title_font)

    out_arr = np.array(out)

    for i, (name, src_dir) in enumerate(samples):
        col = i % cols
        row = i // cols
        x = padding + col * (cell + padding)
        y = 24 + padding + row * (cell_total_h + padding)

        if cell_border is not None:
            border_color = (*cell_border, 255)
            out_arr[y - 2:y - 2 + cell_total_h + 4, x - 2:x - 2 + cell + 4] = border_color

        _draw_checkerboard(out_arr, x, y, cell, cell)

        f = src_dir / f"{name}.png"
        if not f.exists():
            # Leave the checkerboard visible; "MISSING" label gets stamped later.
            pass
        else:
            src = np.array(Image.open(f).convert("RGBA"))
            sh_src, sw_src = src.shape[:2]
            # Java's drawImage(src, x, y, CELL, CELL, null) with NEAREST hint
            # auto-scales any source size to fit the cell. We compute the integer
            # zoom factor per-sprite (16×16 → 8×, 32×32 → 4×, etc.).
            sprite_scale = max(1, cell // max(sh_src, sw_src))
            bg_pixel = (int(out_arr[y, x, 0]), int(out_arr[y, x, 1]), int(out_arr[y, x, 2]))
            scaled = _alpha_blend_scale(src, sprite_scale, bg_rgb=bg_pixel)
            sh, sw = scaled.shape[:2]
            # If the scaled sprite is smaller than the cell (e.g. odd sizes), center it.
            ox = (cell - sw) // 2
            oy = (cell - sh) // 2
            out_arr[y + oy:y + oy + sh, x + ox:x + ox + sw] = scaled

    # Push numpy edits back to PIL Image so we can render text with ImageDraw
    out = Image.fromarray(out_arr, "RGBA")
    draw = ImageDraw.Draw(out)
    for i, (name, src_dir) in enumerate(samples):
        col = i % cols
        row = i // cols
        x = padding + col * (cell + padding)
        y = 24 + padding + row * (cell_total_h + padding)
        f = src_dir / f"{name}.png"
        if not f.exists():
            draw.text((x + 4, y + 4), "MISSING", fill=(255, 0, 0, 255), font=title_font)
            continue
        draw.text((x + 2, y + cell + 2),
                  _truncate_label(name),
                  fill=(0xC8, 0xC8, 0xD8, 255),
                  font=label_font)

    _save(out, out_path)
    return 0


# ============================================================
# Subcommand: arrow_sprite  (ArrowSpritePreview.java)
# ============================================================
def cmd_arrow_sprite(_args) -> int:
    """Crop electric_condenser.png (180, 0, 23, 17) → 12× zoom on AE2 GUI bg."""
    src_path = GUI_DIR / "electric_condenser.png"
    src = np.array(Image.open(src_path).convert("RGBA"))
    src_x, src_y, w, h, scale = 180, 0, 23, 17, 12
    crop = src[src_y:src_y + h, src_x:src_x + w]
    out_arr = _alpha_blend_scale(crop, scale, bg_rgb=(0xCB, 0xCC, 0xD4))
    out = Image.fromarray(out_arr, "RGBA").convert("RGB")
    out_path = PREVIEWS_DIR / "preview_arrow_sprite.png"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out.save(out_path)
    print(f"Wrote {out_path} ({w * scale}×{h * scale}, "
          f"sprite ({src_x},{src_y}) {w}×{h} scaled {scale}x)")
    return 0


# ============================================================
# Subcommand: cells  (CellPreview.java)
# ============================================================
def cmd_cells(args) -> int:
    """Crop arbitrary 16×16 cells from icons.png at given grid (gx,gy) coords + 16× zoom."""
    cells = []
    for spec in args.cells:
        gx, gy = (int(x) for x in spec.split(","))
        cells.append((gx, gy))
    if not cells:
        print("error: at least one cell coordinate required (e.g. 0,7 1,7)")
        return 2

    icons = np.array(Image.open(GUI_DIR / "icons.png").convert("RGBA"))
    scale = 16
    cell_size = 16 * scale  # 256
    gap = 16
    n = len(cells)
    dest_w = (cell_size + gap) * n + gap
    dest_h = cell_size + 2 * gap

    bg = (0xCB, 0xCC, 0xD4)
    out_arr = np.full((dest_h, dest_w, 4), (*bg, 255), dtype=np.uint8)

    for i, (gx, gy) in enumerate(cells):
        src_x, src_y = gx * 16, gy * 16
        dst_x0 = gap + i * (cell_size + gap)
        dst_y0 = gap
        print(f"cell ({gx},{gy}) px=({src_x},{src_y})")
        crop = icons[src_y:src_y + 16, src_x:src_x + 16]
        scaled = _alpha_blend_scale(crop, scale, bg_rgb=bg)
        out_arr[dst_y0:dst_y0 + cell_size, dst_x0:dst_x0 + cell_size] = scaled

    Image.fromarray(out_arr, "RGBA").convert("RGB").save(args.out)
    print(f"Wrote {args.out}")
    return 0


# ============================================================
# Subcommand: on_bg  (PreviewOnWhite.java)
# ============================================================
def cmd_on_bg(args) -> int:
    """Alpha-blend a transparent PNG onto a solid bg + ``--scale`` integer zoom."""
    src_path = Path(args.input)
    out_path = Path(args.output)
    scale = args.scale
    bg_hex = int(args.bg, 16)
    bg = ((bg_hex >> 16) & 0xFF, (bg_hex >> 8) & 0xFF, bg_hex & 0xFF)

    src = np.array(Image.open(src_path).convert("RGBA"))
    out_arr = _alpha_blend_scale(src, scale, bg_rgb=bg)
    Image.fromarray(out_arr, "RGBA").convert("RGB").save(out_path)
    h, w = out_arr.shape[:2]
    print(f"Wrote {out_path} ({w}×{h}, scale={scale}, bg=#{bg_hex:06X})")
    return 0


# ============================================================
# Subcommand: sprite_v_fill  (SpritePreview.java)
# ============================================================
def cmd_sprite_v_fill(_args) -> int:
    """Crop icons.png (240, 0, 16, 64) and write 10× zoom to mc_pixel_perfect_generated_preview.png."""
    src_path = GUI_DIR / "icons.png"
    src = np.array(Image.open(src_path).convert("RGBA"))
    crop_w, crop_h, sprite_src_x, scale = 16, 64, 240, 10
    crop = src[0:crop_h, sprite_src_x:sprite_src_x + crop_w]
    # SpritePreview.java does NOT alpha-blend onto a bg — it preserves alpha + nearest-zoom only
    if scale > 1:
        crop = np.repeat(np.repeat(crop, scale, axis=0), scale, axis=1)
    out_path = PREVIEWS_DIR / "mc_pixel_perfect_generated_preview.png"
    Image.fromarray(crop, "RGBA").save(out_path)
    print(f"Wrote {out_path} ({crop_w * scale}×{crop_h * scale})")
    return 0


# ============================================================
# Subcommand: annotated  (WidgetsAnnotated.java)
# ============================================================
def cmd_annotated(_args) -> int:
    """Show icons.png at 6× zoom on AE2 GUI bg + 16×16 grid + section labels."""
    src_path = GUI_DIR / "icons.png"
    src = np.array(Image.open(src_path).convert("RGBA"))
    H, W = src.shape[:2]
    scale = 6
    bg = (0xCB, 0xCC, 0xD4)

    # Step 1+2: alpha-blend src onto bg at scale
    out_arr = _alpha_blend_scale(src, scale, bg_rgb=bg)
    out = Image.fromarray(out_arr, "RGBA").convert("RGB")
    draw = ImageDraw.Draw(out)
    dest_w, dest_h = W * scale, H * scale

    # Step 3: 16×16 thin green grid
    grid_color = (0x00, 0xAA, 0x00)
    for gx in range(W // 16 + 1):
        px = gx * 16 * scale
        draw.line([(px, 0), (px, dest_h)], fill=grid_color, width=2)
    for gy in range(H // 16 + 1):
        py = gy * 16 * scale
        draw.line([(0, py), (dest_w, py)], fill=grid_color, width=2)

    # Step 4: section boundaries (thick red) + labels
    label_color = (0xD0, 0x10, 0x10)
    label_font = _load_font(20, bold=True)

    def section(x: int, y: int, w: int, h: int, label: str) -> None:
        x0, y0 = x * scale, y * scale
        x1, y1 = (x + w) * scale, (y + h) * scale
        for offset in range(4):
            draw.rectangle([x0 + offset, y0 + offset, x1 - offset, y1 - offset],
                           outline=label_color)
        draw.text((x0 + 2, max(0, y0 - 26)), label, fill=label_color, font=label_font)

    section(0, 0, 23, 17, "arrow")
    section(0, 24, 92, 8, "burn_h_amber")
    section(0, 40, 128, 16, "icons(gear/back/?/IN/OUT/dis/link)")
    section(240, 0, 16, 64, "energy_v_full")
    section(0, 64, 64, 64, "ae2_palette_4x4")

    out_path = PREVIEWS_DIR / "widgets_v10_annotated.png"
    out.save(out_path)
    print(f"Wrote {out_path} ({dest_w}×{dest_h})")
    return 0


# ============================================================
# CLI dispatch
# ============================================================
def cli(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="preview_tools.py",
        description=__doc__.split("\n\n")[0],
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    sub = parser.add_subparsers(dest="cmd", required=True)

    sub.add_parser("reference", help="Tile AE2-CS sample sprites into a reference sheet")
    sub.add_parser("assets", help="Tile the 12 generated VirtualCraft assets at 8× zoom")
    sub.add_parser("arrow_sprite", help="Crop & zoom the progress arrow sprite from electric_condenser.png")

    p_cells = sub.add_parser("cells", help="Crop & zoom arbitrary 16×16 icon cells")
    p_cells.add_argument("out", help="Output PNG path")
    p_cells.add_argument("cells", nargs="+", help="One or more 'gx,gy' grid coords (e.g. 0,7 1,7)")

    p_on_bg = sub.add_parser("on_bg", help="Alpha-blend a transparent PNG onto solid bg + zoom")
    p_on_bg.add_argument("input", help="Input PNG (any RGBA texture)")
    p_on_bg.add_argument("output", help="Output PNG path")
    p_on_bg.add_argument("scale", type=int, nargs="?", default=2, help="Integer zoom factor (default 2)")
    p_on_bg.add_argument("bg", nargs="?", default="FFFFFF", help="Background hex RRGGBB (default white)")

    sub.add_parser("sprite_v_fill", help="Crop & zoom the vertical FE-fill sprite from icons.png")
    sub.add_parser("annotated", help="Show icons.png with grid + section labels")

    args = parser.parse_args(argv)
    handler = {
        "reference": cmd_reference,
        "assets": cmd_assets,
        "arrow_sprite": cmd_arrow_sprite,
        "cells": cmd_cells,
        "on_bg": cmd_on_bg,
        "sprite_v_fill": cmd_sprite_v_fill,
        "annotated": cmd_annotated,
    }[args.cmd]
    return handler(args)


if __name__ == "__main__":
    sys.exit(cli())
