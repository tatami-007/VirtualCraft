#!/usr/bin/env python3
"""VirtualCraft GUI texture generator — Python port of GuiTextureGenerator.java.

Pixel-perfect equivalent of the Java version. Verifiable via ``--diff``:

    python tools/gui_texture_generator.py --diff

prints which (if any) generated PNGs differ from those currently on disk.

For interactive pixel debugging, import the module in Jupyter and call any
``Machines.xxx()`` — each returns a ``numpy.ndarray`` of shape ``(H, W, 4)``
that you can pass directly to ``PIL.Image.fromarray(arr, 'RGBA')`` and display.

Requirements: ``pip install pillow numpy``
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image

# ============================================================
# Paths — anchored on the workspace root so the script can be invoked
# from anywhere (cwd does not matter, unlike the Java entry point).
# Layout:  workspace_root/tools/python/gui_texture_generator.py
#                ^^^^^^^^^^^^^^^                ROOT = .parents[2]
# ============================================================
ROOT = Path(__file__).resolve().parents[2]
OUT_DIR = ROOT / "src" / "main" / "resources" / "assets" / "virtual_craft" / "textures" / "gui"
ICONS_PATH = ROOT / "tools" / "refs" / "ae2cs_icons.png"
GEAR_PATH = ROOT / "tools" / "ae2cs" / "extracted" / "assets" / "ae2cs" / "textures" / "item" / "wooden_gear.png"


# ============================================================
# Palette — sampled from ae2cs_*_menu.png (matches Java Palette class)
# ============================================================
def _c(hex_rgb: int) -> tuple[int, int, int, int]:
    """Hex 0xRRGGBB -> opaque RGBA tuple matching ``new Color(0xRRGGBB).getRGB()``."""
    return (
        (hex_rgb >> 16) & 0xFF,
        (hex_rgb >> 8) & 0xFF,
        hex_rgb & 0xFF,
        0xFF,
    )


class Palette:
    # === Window ===
    WINDOW_BORDER = _c(0x413F54)
    WINDOW_HIGH = _c(0xF2F2F2)
    WINDOW_BG = _c(0xCBCCD4)
    # === Recessed panel (slot group / inventory / energy / progress track) ===
    PANEL_TOP = _c(0x9A9FB4)
    PANEL_FILL = _c(0xADB0C4)
    PANEL_SHADOW = _c(0x878FA5)
    PANEL_LINE = _c(0x4D4D67)
    # === Accent ===
    ACCENT_PURPLE = _c(0x915DCD)
    ACCENT_VIOLET = _c(0x6054A6)
    ACCENT_BLUE = _c(0x373B72)
    ACCENT_HEADER = _c(0x696D88)
    GLOW_PINK = _c(0xFF80D7)
    GLOW_VIOLET = _c(0xB06FDD)
    # === Icon palette ===
    ICON_PURPLE_LIGHT = _c(0xA79CFF)
    ICON_RED_LIGHT = _c(0xFFA59C)
    ICON_RED = _c(0xDB7573)
    ICON_RED_DARK = _c(0xAC4339)
    ICON_FG = _c(0x4D4D67)
    # === FE Thermal-Expansion energy bar ===
    FE_TE_EDGE = _c(0x000000)
    FE_TE_INNER_DARK = _c(0x2A2A2A)
    FE_TE_INNER_LIGHT = _c(0x6E6E6E)
    FE_TE_EMPTY_TOP = _c(0x383838)
    FE_TE_EMPTY_BOTTOM = _c(0x4F4F4F)
    FE_TE_RED_MID = _c(0xD22A2A)
    FE_TE_RED_BOTTOM = _c(0x801010)
    FE_TE_RED_HIGH = _c(0xFFC8B8)
    FE_TE_RED_LEFT = _c(0xFF9090)
    FE_TE_RED_RIGHT = _c(0x6A0808)
    # === MC pixel-perfect 7-step red mirror ===
    MCPP_EDGE_DARK = _c(0x373737)
    MCPP_EDGE_LIGHT = _c(0xFFFFFF)
    MCPP_RED_LIGHT = [
        _c(0x420000), _c(0x5C0000), _c(0x7A0000), _c(0x990000),
        _c(0xB80000), _c(0xD60000), _c(0xF50000),
    ]
    MCPP_RED_DARK = [
        _c(0x2B0000), _c(0x3C0000), _c(0x500000), _c(0x640000),
        _c(0x780000), _c(0x8C0000), _c(0xA00000),
    ]
    # === Burn / Flame ===
    BURN_AMBER = _c(0xC89466)
    BURN_AMBER_DARK = _c(0xAA702F)
    FLAME_BRIGHT = _c(0xFFD955)
    FLAME_LIGHT = _c(0xFFB347)
    FLAME_DARK = _c(0xDD5500)


# ============================================================
# Primitives — numpy-backed pixel ops (matches Java setRGB semantics)
# ============================================================
def _java_round(x: float) -> int:
    """Match Java's ``Math.round(float)``: round half away from -infinity.

    Differs from Python ``round`` (banker's rounding) for ``.5`` boundary cases
    such as ``66.5`` (Java -> 67, Python -> 66). Critical for ``lerp_color``
    where banker's rounding would produce off-by-one pixel diffs vs Java output.
    """
    import math

    return int(math.floor(x + 0.5))


class Primitives:
    """Core pixel operations. All mutate ``np.ndarray`` shape ``(H, W, 4)`` in-place."""

    @staticmethod
    def canvas(w: int, h: int) -> np.ndarray:
        """New transparent RGBA canvas (Java ``TYPE_INT_ARGB`` initializes to 0x00000000)."""
        return np.zeros((h, w, 4), dtype=np.uint8)

    @staticmethod
    def fill_rect(img: np.ndarray, x: int, y: int, w: int, h: int, color) -> None:
        """Fill rect with solid color. Out-of-bounds pixels silently clipped (matches Java)."""
        H, W = img.shape[:2]
        x0, y0 = max(0, x), max(0, y)
        x1, y1 = min(W, x + w), min(H, y + h)
        if x0 < x1 and y0 < y1:
            img[y0:y1, x0:x1] = color

    @staticmethod
    def clear_rect(img: np.ndarray, x: int, y: int, w: int, h: int) -> None:
        """Clear rect to fully transparent (matches Java ``setRGB(0)``)."""
        Primitives.fill_rect(img, x, y, w, h, (0, 0, 0, 0))

    @staticmethod
    def copy_rect(src: np.ndarray, sx: int, sy: int, w: int, h: int,
                  dst: np.ndarray, dx: int, dy: int) -> None:
        """Raw pixel copy preserving alpha (matches Java ``setRGB(getRGB(...))``)."""
        dst[dy:dy + h, dx:dx + w] = src[sy:sy + h, sx:sx + w]

    @staticmethod
    def copy_icon_cell(src: np.ndarray, src_gx: int, src_gy: int,
                       dst: np.ndarray, dst_x: int, dst_y: int) -> None:
        """Copy a 16×16 cell at grid position ``(src_gx, src_gy)`` from the icons sheet."""
        Primitives.copy_rect(src, src_gx * 16, src_gy * 16, 16, 16, dst, dst_x, dst_y)

    @staticmethod
    def lerp_color(a, b, t: float):
        """RGB linear interpolation matching Java ``lerpColor`` (uses Java round semantics)."""
        t = max(0.0, min(1.0, t))
        r = _java_round(a[0] + t * (b[0] - a[0]))
        g = _java_round(a[1] + t * (b[1] - a[1]))
        bl = _java_round(a[2] + t * (b[2] - a[2]))
        return (r, g, bl, 0xFF)

    @staticmethod
    def load_icons_sheet() -> np.ndarray:
        """Load AE2-CS reference ``icons.png`` as numpy RGBA array."""
        if not ICONS_PATH.exists():
            raise FileNotFoundError(
                f"icons.png not found at {ICONS_PATH} — "
                "please ensure AE2-CS icons.png is present in tools/refs/"
            )
        return np.array(Image.open(ICONS_PATH).convert("RGBA"))

# ============================================================
# Shared widgets — strict AE2-CS pixel patterns
# ============================================================
class Widgets:
    """``drawWindow`` / ``drawSlotPanel`` / ``drawRecessedTrack`` / ``drawEnergyTrackTE`` / ``drawEnergyTrackMCPP``."""

    @staticmethod
    def window(img: np.ndarray, x: int, y: int, w: int, h: int) -> None:
        """AE2-CS window: top+left 1px highlight, 1px outer border, no bottom/right shadow."""
        P = Primitives
        # 1) Main bg
        P.fill_rect(img, x, y, w, h, Palette.WINDOW_BG)
        # 2) Outer 1px border (top/bottom/left/right)
        P.fill_rect(img, x, y, w, 1, Palette.WINDOW_BORDER)
        P.fill_rect(img, x, y + h - 1, w, 1, Palette.WINDOW_BORDER)
        P.fill_rect(img, x, y, 1, h, Palette.WINDOW_BORDER)
        P.fill_rect(img, x + w - 1, y, 1, h, Palette.WINDOW_BORDER)
        # 3) Top 1px highlight (inside border)
        P.fill_rect(img, x + 1, y + 1, w - 2, 1, Palette.WINDOW_HIGH)
        # 4) Left 1px highlight (inside border)
        P.fill_rect(img, x + 1, y + 1, 1, h - 2, Palette.WINDOW_HIGH)

    @staticmethod
    def slot_panel(img: np.ndarray, first_slot_x: int, first_slot_y: int,
                   cols: int, rows: int) -> None:
        """AE2-CS recessed slot panel with 4-side WINDOW_HIGH border (v20.4)."""
        P = Primitives
        gx, gy = first_slot_x, first_slot_y
        inner_w = cols * 18 - 2
        inner_h = rows * 18 - 2
        # 4-side 1px WINDOW_HIGH border (top / left / bottom / right)
        P.fill_rect(img, gx - 1, gy - 1, inner_w + 2, 1, Palette.WINDOW_HIGH)
        P.fill_rect(img, gx - 1, gy - 1, 1, inner_h + 2, Palette.WINDOW_HIGH)
        P.fill_rect(img, gx - 1, gy + inner_h, inner_w + 2, 1, Palette.WINDOW_HIGH)
        P.fill_rect(img, gx + inner_w, gy - 1, 1, inner_h + 2, Palette.WINDOW_HIGH)
        # Each slot: 16×16 PANEL_FILL body + 1px PANEL_TOP transition
        for row in range(rows):
            for col in range(cols):
                sx = gx + col * 18
                sy = gy + row * 18
                P.fill_rect(img, sx, sy, 16, 16, Palette.PANEL_FILL)
                P.fill_rect(img, sx, sy, 16, 1, Palette.PANEL_TOP)

    @staticmethod
    def recessed_track(img: np.ndarray, x: int, y: int, w: int, h: int) -> None:
        """v20.9 4-side WINDOW_HIGH track for processing bars (PowMiner purple etc.)."""
        P = Primitives
        P.fill_rect(img, x, y, w, h, Palette.PANEL_FILL)
        P.fill_rect(img, x, y, w, 1, Palette.WINDOW_HIGH)
        P.fill_rect(img, x, y + h - 1, w, 1, Palette.WINDOW_HIGH)
        P.fill_rect(img, x, y, 1, h, Palette.WINDOW_HIGH)
        P.fill_rect(img, x + w - 1, y, 1, h, Palette.WINDOW_HIGH)

    @staticmethod
    def energy_track_te(img: np.ndarray, x: int, y: int, w: int, h: int) -> None:
        """Thermal-Expansion RF energy bar empty track (3D recessed pipe)."""
        P = Primitives
        # Layer 1: black outer 1px
        P.fill_rect(img, x, y, w, 1, Palette.FE_TE_EDGE)
        P.fill_rect(img, x, y + h - 1, w, 1, Palette.FE_TE_EDGE)
        P.fill_rect(img, x, y, 1, h, Palette.FE_TE_EDGE)
        P.fill_rect(img, x + w - 1, y, 1, h, Palette.FE_TE_EDGE)
        # Layer 2: vertical gradient inside (top dark -> bottom light)
        inner_y = y + 1
        inner_h = h - 2
        for dy in range(inner_h):
            t = dy / (inner_h - 1) if inner_h > 1 else 0.0
            row_color = P.lerp_color(Palette.FE_TE_EMPTY_TOP, Palette.FE_TE_EMPTY_BOTTOM, t)
            P.fill_rect(img, x + 1, inner_y + dy, w - 2, 1, row_color)
        # Layer 3: 3D recess inner borders (top+left dark, bottom+right light)
        P.fill_rect(img, x + 1, y + 1, w - 2, 1, Palette.FE_TE_INNER_DARK)
        P.fill_rect(img, x + 1, y + 1, 1, h - 2, Palette.FE_TE_INNER_DARK)
        P.fill_rect(img, x + 1, y + h - 2, w - 2, 1, Palette.FE_TE_INNER_LIGHT)
        P.fill_rect(img, x + w - 2, y + 1, 1, h - 2, Palette.FE_TE_INNER_LIGHT)

    @staticmethod
    def energy_track_mcpp(img: np.ndarray, x: int, y: int, w: int, h: int) -> None:
        """v20.6 MCPP dark inside + 4-side WINDOW_HIGH border (matches energy bar standard)."""
        P = Primitives
        P.fill_rect(img, x, y, w, h, Palette.MCPP_EDGE_DARK)
        P.fill_rect(img, x, y, w, 1, Palette.WINDOW_HIGH)
        P.fill_rect(img, x, y + h - 1, w, 1, Palette.WINDOW_HIGH)
        P.fill_rect(img, x, y, 1, h, Palette.WINDOW_HIGH)
        P.fill_rect(img, x + w - 1, y, 1, h, Palette.WINDOW_HIGH)


# ============================================================
# Sprite painters — progress arrow / flame / burn track
# ============================================================
class Sprites:
    @staticmethod
    def progress_arrow_filled(img: np.ndarray, x: int, y: int,
                              fill, highlight, shadow) -> None:
        """v20.3 classic vanilla furnace arrow 22×16, parametric color (full-ring 1px outline)."""
        P = Primitives
        # Rhombus row widths (y+1 .. y+14, total 14 rows)
        row_widths = [1, 2, 4, 6, 8, 10, 11, 11, 10, 8, 6, 4, 2, 1]
        n = len(row_widths)
        # 1) Main shaft 12×6
        P.fill_rect(img, x, y + 5, 12, 6, fill)
        # 2) Rhombus right tip fill
        for i in range(n):
            P.fill_rect(img, x + 11, y + 1 + i, row_widths[i], 1, fill)
        # 3) Highlight: top / left / upper-right edges
        P.fill_rect(img, x, y + 5, 12, 1, highlight)
        P.fill_rect(img, x, y + 5, 1, 6, highlight)
        for i in range(7):  # upper half i=0..6
            right_x = x + 11 + row_widths[i] - 1
            P.fill_rect(img, right_x, y + 1 + i, 1, 1, highlight)
        # 4) Shadow: bottom / lower-right edges
        P.fill_rect(img, x, y + 10, 12, 1, shadow)
        P.fill_rect(img, x, y + 10, 1, 1, shadow)
        for i in range(7, n):  # lower half i=7..13
            right_x = x + 11 + row_widths[i] - 1
            P.fill_rect(img, right_x, y + 1 + i, 1, 1, shadow)

    @staticmethod
    def progress_arrow_sprite(img: np.ndarray, x: int, y: int) -> None:
        """Purple ElectricCondenser progress arrow sprite."""
        Sprites.progress_arrow_filled(
            img, x, y,
            Palette.ACCENT_PURPLE, Palette.ICON_PURPLE_LIGHT, Palette.ACCENT_VIOLET,
        )

    @staticmethod
    def progress_arrow_empty(img: np.ndarray, x: int, y: int) -> None:
        """v20.2 PANEL_FILL flat surface (no highlight/shadow — sprite provides depth)."""
        Sprites.progress_arrow_filled(
            img, x, y,
            Palette.PANEL_FILL, Palette.PANEL_FILL, Palette.PANEL_FILL,
        )

    @staticmethod
    def horizontal_arrow_sprite(img: np.ndarray, x: int, y: int, w: int) -> None:
        """v20.10 PowMiner purple processing bar fill (8px tall, w wide)."""
        P = Primitives
        P.fill_rect(img, x, y, w, 8, Palette.ACCENT_PURPLE)
        P.fill_rect(img, x, y, w, 1, Palette.ICON_PURPLE_LIGHT)

    @staticmethod
    def horizontal_flame_sprite(img: np.ndarray, x: int, y: int, w: int) -> None:
        """v20.10 ElectricBallGenerator flame burn bar (with serrated top)."""
        P = Primitives
        P.fill_rect(img, x, y + 3, w, 3, Palette.FLAME_DARK)
        P.fill_rect(img, x, y + 2, w, 1, Palette.FLAME_LIGHT)
        P.fill_rect(img, x, y + 1, w, 1, Palette.FLAME_BRIGHT)
        for col in range(w):
            c = Palette.FLAME_DARK if (col % 3 == 2) else Palette.FLAME_BRIGHT
            P.fill_rect(img, x + col, y, 1, 1, c)

    @staticmethod
    def horizontal_burn_track(img: np.ndarray, x: int, y: int, w: int, h: int) -> None:
        """v20.10 ElectricBallGenerator MCPP black furnace burn track."""
        P = Primitives
        P.fill_rect(img, x, y, w, h, Palette.MCPP_EDGE_DARK)
        P.fill_rect(img, x, y, w, 1, Palette.WINDOW_HIGH)
        P.fill_rect(img, x, y + h - 1, w, 1, Palette.WINDOW_HIGH)
        P.fill_rect(img, x, y, 1, h, Palette.WINDOW_HIGH)
        P.fill_rect(img, x + w - 1, y, 1, h, Palette.WINDOW_HIGH)


# ============================================================
# Icons — hand-drawn 16×16 sprites (gear / back / disable)
# ============================================================
class Icons:
    @staticmethod
    def gear(img: np.ndarray, x: int, y: int) -> None:
        """Generate a GUI gear by recoloring AE2-CS' 16×16 wooden gear item."""
        P = Primitives
        if not GEAR_PATH.exists():
            raise FileNotFoundError(f"gear reference not found: {GEAR_PATH}")
        gear = Image.open(GEAR_PATH).convert("RGBA")
        for dy in range(16):
            for dx in range(16):
                r, g, b, a = gear.getpixel((dx, dy))
                if a == 0:
                    continue
                luma = (r * 30 + g * 59 + b * 11) // 100
                if luma >= 170:
                    color = Palette.WINDOW_HIGH
                elif luma >= 115:
                    color = Palette.PANEL_TOP
                elif luma >= 82:
                    color = Palette.ICON_FG
                else:
                    color = Palette.PANEL_LINE
                P.fill_rect(img, x + dx, y + dy, 1, 1, color)

    @staticmethod
    def back(img: np.ndarray, x: int, y: int) -> None:
        """v20.8 classic back arrow: 9-row tapered triangle tip + 5-row shaft."""
        P = Primitives
        fg = Palette.ICON_FG
        # Triangle tip (9 rows, y=4..12, widths 1-2-3-4-5-4-3-2-1)
        P.fill_rect(img, x + 6, y + 4, 1, 1, fg)
        P.fill_rect(img, x + 5, y + 5, 2, 1, fg)
        P.fill_rect(img, x + 4, y + 6, 3, 1, fg)
        P.fill_rect(img, x + 3, y + 7, 4, 1, fg)
        P.fill_rect(img, x + 2, y + 8, 5, 1, fg)
        P.fill_rect(img, x + 3, y + 9, 4, 1, fg)
        P.fill_rect(img, x + 4, y + 10, 3, 1, fg)
        P.fill_rect(img, x + 5, y + 11, 2, 1, fg)
        P.fill_rect(img, x + 6, y + 12, 1, 1, fg)
        # Shaft (5 rows tall, y=6..10, 7px wide)
        for dy in range(6, 11):
            P.fill_rect(img, x + 7, y + dy, 7, 1, fg)

    @staticmethod
    def disable(img: np.ndarray, x: int, y: int) -> None:
        """Red X disable icon (AE2-CS red palette)."""
        P = Primitives
        fg = Palette.ICON_RED
        sh = Palette.ICON_RED_DARK
        hi = Palette.ICON_RED_LIGHT
        # Double diagonal lines (10 px each)
        for i in range(10):
            P.fill_rect(img, x + 3 + i, y + 3 + i, 2, 2, fg)
            P.fill_rect(img, x + 12 - i, y + 3 + i, 2, 2, fg)
        # Central shadow
        P.fill_rect(img, x + 7, y + 7, 2, 2, sh)
        # Endpoint highlights
        P.fill_rect(img, x + 3, y + 3, 1, 1, hi)
        P.fill_rect(img, x + 12, y + 3, 1, 1, hi)


# ============================================================
# Machine textures — each returns numpy array (callable from notebook/CLI)
# ============================================================
class Machines:
    @staticmethod
    def pow_miner() -> np.ndarray:
        """v20.13 PowMiner 176×178 — symmetric energy / output / processing bar.

        Layout:
          - ENERGY (8, 22)  16×52  MCPP dark vertical bar
          - OUTPUT (79, 32) 18×18  single slot
          - ARROW  (62, 56) 52×8   purple horizontal processing bar
          - Inventory (8, 96) 9×3 + Hotbar (8, 154) 9×1
          - Purple sprite sheet @ (180, 0) 52×8
        """
        img = Primitives.canvas(256, 256)
        Widgets.window(img, 0, 0, 176, 178)
        Widgets.energy_track_mcpp(img, 8, 22, 16, 52)
        Widgets.slot_panel(img, 79, 32, 1, 1)
        Widgets.recessed_track(img, 62, 56, 52, 8)
        Widgets.slot_panel(img, 8, 96, 9, 3)
        Widgets.slot_panel(img, 8, 154, 9, 1)
        Sprites.horizontal_arrow_sprite(img, 180, 0, 52)
        return img

    @staticmethod
    def electric_ball_generator() -> np.ndarray:
        """v20.15 ElectricBallGenerator 176×178 — FUEL → flame arrow → ENERGY vertical bar.

        v20.15 dropped the left energy bar (user feedback: 'EBG no left energy slot,
        only right'). Right energy buffer is a 16×52 MCPP vertical bar to clearly
        signal 'energy storage', not 'item slot'.

        Layout:
          - FUEL   (47, 39) 18×18  single slot (left input: power ball)
          - ARROW  (77, 40) 22×16  empty flame arrow (orange when burning)
          - ENERGY (112, 22) 16×52 MCPP dark vertical bar (right energy buffer)
          - Inventory + hotbar (standard)
          - Flame arrow sprite @ (180, 0) 22×16
        """
        img = Primitives.canvas(256, 256)
        Widgets.window(img, 0, 0, 176, 178)
        Widgets.slot_panel(img, 47, 39, 1, 1)
        Sprites.progress_arrow_empty(img, 77, 40)
        Widgets.energy_track_mcpp(img, 112, 22, 16, 52)
        Widgets.slot_panel(img, 8, 96, 9, 3)
        Widgets.slot_panel(img, 8, 154, 9, 1)
        Sprites.progress_arrow_filled(
            img, 180, 0,
            Palette.FLAME_LIGHT, Palette.FLAME_BRIGHT, Palette.FLAME_DARK,
        )
        return img

    @staticmethod
    def electric_condenser() -> np.ndarray:
        """v20.13 ElectricCondenser 176×178 — INGOT/BALL → progress arrow → OUTPUT + UPGRADE column.

        Layout (all widgets centered on y=49):
          - ENERGY  (8, 23)    16×52  MCPP dark vertical bar
          - INGOT   (47, 29)   18×18  upper input slot
          - BALL    (47, 51)   18×18  lower input slot
          - ARROW   (77, 41)   22×16  empty progress arrow
          - OUTPUT  (111, 40)  18×18  output slot
          - UPGRADE (152, 28)  18×54  3 stacked upgrade slots
          - Inventory + hotbar (standard)
          - Purple progress arrow sprite @ (180, 0)
        """
        img = Primitives.canvas(256, 256)
        Widgets.window(img, 0, 0, 176, 178)
        Widgets.energy_track_mcpp(img, 8, 23, 16, 52)
        Widgets.slot_panel(img, 47, 29, 1, 1)
        Widgets.slot_panel(img, 47, 51, 1, 1)
        Sprites.progress_arrow_empty(img, 77, 41)
        Widgets.slot_panel(img, 111, 40, 1, 1)
        Widgets.slot_panel(img, 152, 28, 1, 3)
        Widgets.slot_panel(img, 8, 96, 9, 3)
        Widgets.slot_panel(img, 8, 154, 9, 1)
        Sprites.progress_arrow_sprite(img, 180, 0)
        return img

    @staticmethod
    def side_config_menu() -> np.ndarray:
        """v20.7 generic side-config 120×107 blank backdrop (buttons drawn at runtime)."""
        img = Primitives.canvas(256, 256)
        Widgets.window(img, 0, 0, 120, 107)
        return img

    @staticmethod
    def icons_sheet() -> np.ndarray:
        """v14 icons.png 256×256 — palette block + button atlas + vertical FE red fill + hand-drawn icons.

        Layout:
          - (0, 64)    144×64  AE2-CS palette block (copied from ae2cs_icons.png (16, 192))
          - (0, 130)   144×20  AE2-CS BUTTON DARK atlas (copied from ae2cs_icons.png (16, 216))
          - (0, 150)   144×20  AE2-CS BUTTON LIGHT atlas (copied from ae2cs_icons.png (16, 196))
          - (240, 0)   16×64   MC pixel-perfect 7-step mirror red vertical energy fill
          - (0, 40)    16×16   generated gear icon
          - (16, 40)   16×16   AE2-CS back arrow icon
          - (48, 40)   16×16   receiver/input icon (copied from AE2-CS RECEIVER_STATE)
          - (64, 40)   16×16   sender/output icon (copied from AE2-CS SENDER_STATE)
          - (80, 40)   16×16   AE2-CS disable X icon
        """
        P = Primitives
        img = P.canvas(256, 256)
        icons = P.load_icons_sheet()

        # AE2-CS palette block 144×64 (one-shot rect copy preserves stack borders)
        P.copy_rect(icons, 16, 192, 144, 64, img, 0, 64)
        # AE2-CS BUTTON DARK atlas 144×20 for SideFaceButton
        P.copy_rect(icons, 16, 216, 144, 20, img, 0, 130)
        # AE2-CS BUTTON LIGHT atlas 144×20 for enabled auto-mode toolbar buttons
        P.copy_rect(icons, 16, 196, 144, 20, img, 0, 150)

        # Vertical FE red fill 16×64 @ x=240..255 (MCPP 7-step mirror)
        # v21: removed bottom 1-px WINDOW_HIGH border. The sprite is consumed by
        # MachineUi.drawVerticalBar which paints the bottom-most rows of the
        # track when fill is high; if the sprite has a white bottom border it
        # stacks on top of the track's own WINDOW_HIGH bottom border, producing
        # a visibly 2-px-thick white line under the energy bar (user feedback).
        # Fix: extend red stripes down to y=63 and only keep the top WINDOW_HIGH.
        base_x = 240
        # Step 1: fill the whole 16×64 with MCPP dark (will be partly overdrawn)
        P.fill_rect(img, base_x, 0, 16, 64, Palette.MCPP_EDGE_DARK)
        # Step 2: top + left + right WINDOW_HIGH border only (no bottom — see note above)
        P.fill_rect(img, base_x, 0, 16, 1, Palette.WINDOW_HIGH)
        P.fill_rect(img, base_x, 0, 1, 64, Palette.WINDOW_HIGH)
        P.fill_rect(img, base_x + 15, 0, 1, 64, Palette.WINDOW_HIGH)

        # Step 3: 7-step mirror red stripes (col 1..14, y=1..63, alternating light/dark rows)
        # v21: extended to y=63 (was y=1..62) so that satisfying-fill blits don't
        # land on dark MCPP_EDGE_DARK or a white border at the bottom row.
        palette_idx_per_col = [0, 1, 2, 3, 4, 5, 6, 6, 5, 4, 3, 2, 1, 0]
        for col in range(1, 15):  # col = 1..14 inclusive
            px = base_x + col
            idx = palette_idx_per_col[col - 1]
            light = Palette.MCPP_RED_LIGHT[idx]
            dark = Palette.MCPP_RED_DARK[idx]
            for yy in range(1, 64):  # yy = 1..63 inclusive (extended to bottom)
                color = light if (yy % 2 == 1) else dark
                P.fill_rect(img, px, yy, 1, 1, color)

        # 16×16 icons row @ y=40..55
        Icons.gear(img, 0, 40)
        Icons.back(img, 16, 40)
        # ICON_QUESTION_U=32 — removed v20.7 (zero references)
        P.copy_icon_cell(icons, 0, 7, img, 48, 40)
        P.copy_icon_cell(icons, 1, 7, img, 64, 40)
        Icons.disable(img, 80, 40)
        # ICON_BURN_U=96 / ICON_LINK_U=112 / ICON_PICKAXE_U=128 — removed (zero references)
        return img


# ============================================================
# IO — save / diff verification
# ============================================================
def save_png(arr: np.ndarray, name: str, out_dir: Path = OUT_DIR) -> None:
    """Save numpy RGBA array as PNG to ``out_dir/name``."""
    out_dir.mkdir(parents=True, exist_ok=True)
    out = out_dir / name
    Image.fromarray(arr, "RGBA").save(out)
    h, w = arr.shape[:2]
    print(f"  Wrote {name} ({w}×{h})")


def diff_png(arr: np.ndarray, name: str, out_dir: Path = OUT_DIR) -> tuple[bool, int]:
    """Compare numpy RGBA array against existing ``out_dir/name``. Returns ``(ok, n_diff_pixels)``."""
    path = out_dir / name
    if not path.exists():
        print(f"  MISS {name}: existing file not found at {path}")
        return False, -1
    existing = np.array(Image.open(path).convert("RGBA"))
    if existing.shape != arr.shape:
        print(f"  DIFF {name}: shape mismatch {existing.shape} vs {arr.shape}")
        return False, -2
    diff_mask = np.any(existing != arr, axis=-1)
    n_diff = int(np.sum(diff_mask))
    if n_diff == 0:
        print(f"  OK   {name} ({arr.shape[1]}×{arr.shape[0]})")
        return True, 0
    print(f"  DIFF {name}: {n_diff} pixel(s) differ")
    ys, xs = np.where(diff_mask)
    if len(ys) > 0:
        y0, x0 = int(ys[0]), int(xs[0])
        print(f"        first diff at ({x0}, {y0}): "
              f"java={tuple(int(v) for v in existing[y0, x0])} "
              f"py={tuple(int(v) for v in arr[y0, x0])}")
    return False, n_diff


# ============================================================
# Generator registry — name → callable (consumed by CLI + notebook)
# ============================================================
GENERATORS = {
    "pow_miner": Machines.pow_miner,
    "electric_ball_generator": Machines.electric_ball_generator,
    "electric_condenser": Machines.electric_condenser,
    "side_config_menu": Machines.side_config_menu,
    "icons": Machines.icons_sheet,
}


def cli(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="VirtualCraft GUI texture generator (Python port of GuiTextureGenerator.java)."
    )
    parser.add_argument(
        "--only",
        choices=list(GENERATORS.keys()),
        help="Generate only the named texture (default: generate all).",
    )
    parser.add_argument(
        "--diff",
        action="store_true",
        help="Compare against existing PNG instead of writing (regression check).",
    )
    args = parser.parse_args(argv)

    targets = [args.only] if args.only else list(GENERATORS.keys())
    print(f"Output directory: {OUT_DIR.resolve()}")

    if args.diff:
        n_ok = n_diff = 0
        for name in targets:
            arr = GENERATORS[name]()
            ok, _ = diff_png(arr, f"{name}.png")
            if ok:
                n_ok += 1
            else:
                n_diff += 1
        total = n_ok + n_diff
        if n_diff == 0:
            print(f"Done. All {total} texture(s) match Java output pixel-for-pixel.")
            return 0
        print(f"Done. {n_ok}/{total} match, {n_diff} differ.")
        return 1

    for name in targets:
        arr = GENERATORS[name]()
        save_png(arr, f"{name}.png")
    print(f"Done. {len(targets)} texture(s) generated.")
    return 0


if __name__ == "__main__":
    sys.exit(cli())
