#!/usr/bin/env python3
"""VirtualCraft item & block texture generator — Python port of AssetTextureGenerator.java.

Pixel-perfect equivalent of the Java version. Verifiable via ``--diff``.

Generates:
  - 5 items in textures/item/  (hash_voucher, mining_surveyor, speed_upgrade_card,
    wireless_transfer_upgrade_card, link_card)
  - 4 cube_all blocks in textures/block/  (electric_ball_generator, wireless_power_tower,
    holographic_shop, pow_miner)

The 4 hand-drawn items (electric_ingot, electric_absorber, electric_absorber_on,
electric_ball) are intentionally skipped — they're authored manually and kept as-is.
The procedural ``draw_electric_ingot`` / ``draw_electric_absorber`` references remain
in the file as design references but are not invoked.

Requirements: ``pip install pillow numpy``
"""

from __future__ import annotations

import argparse
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

# Reuse shared infrastructure (Primitives, _c, _java_round, save_png, diff_png)
# from the GUI generator — no need to duplicate the numpy/PIL plumbing.
sys.path.insert(0, str(Path(__file__).resolve().parent))
from gui_texture_generator import _c, Primitives  # noqa: E402

# ============================================================
# Output paths (matches Java AssetTextureGenerator constants)
# Layout:  workspace_root/tools/python/asset_texture_generator.py → ROOT = .parents[2]
# ============================================================
ROOT = Path(__file__).resolve().parents[2]
ITEM_DIR = ROOT / "src" / "main" / "resources" / "assets" / "virtual_craft" / "textures" / "item"
BLOCK_DIR = ROOT / "src" / "main" / "resources" / "assets" / "virtual_craft" / "textures" / "block"
# AE2-CS reference textures (used as stencils for hue-shifted derivatives).
# Path is gitignored — the contents are extracted from the AE2-CS jar separately
# (see tools/ae2cs/README.md). Only loaded at generation time, never bundled.
AE2_TEMPLATE_DIR = ROOT / "tools" / "ae2cs" / "extracted" / "assets" / "ae2cs" / "textures" / "item"
SIZE = 16


def _hue_shift_rgba(rgba: np.ndarray, deg: float, sat_thresh: float = 0.18) -> np.ndarray:
    """Hue-shift opaque pixels of an RGBA image by ``deg`` degrees.

    Only pixels with saturation > ``sat_thresh`` are shifted, so near-grayscale
    pixels (frame outline #413F54, stone shelf #696D88, etc) are preserved
    as-is. This lets us recolor AE2-CS gem cards (green→violet, etc) without
    destroying the AE2 frame outline.

    Implementation is fully vectorized numpy (no per-pixel loops).
    """
    if deg == 0 or rgba.size == 0:
        return rgba.copy()
    out = rgba.copy()
    rgb = rgba[..., :3].astype(np.float32) / 255.0
    a = rgba[..., 3]

    # --- RGB → HSV (vectorized, matches colorsys.rgb_to_hsv) ---
    maxc = rgb.max(axis=-1)
    minc = rgb.min(axis=-1)
    v = maxc
    delta = maxc - minc
    s = np.where(maxc > 0, delta / np.maximum(maxc, 1e-10), 0.0)
    safe_delta = np.maximum(delta, 1e-10)
    rc = (maxc - rgb[..., 0]) / safe_delta
    gc = (maxc - rgb[..., 1]) / safe_delta
    bc = (maxc - rgb[..., 2]) / safe_delta
    h = np.where(rgb[..., 0] == maxc, bc - gc,
        np.where(rgb[..., 1] == maxc, 2.0 + rc - bc, 4.0 + gc - rc))
    h = (h / 6.0) % 1.0
    h = np.where(delta == 0, 0.0, h)

    # --- Apply hue shift only to saturated, opaque pixels ---
    apply_mask = (s > sat_thresh) & (a >= 128)
    h_shifted = np.where(apply_mask, (h + deg / 360.0) % 1.0, h)

    # --- HSV → RGB (vectorized, matches colorsys.hsv_to_rgb) ---
    i = np.floor(h_shifted * 6.0).astype(int) % 6
    f = h_shifted * 6.0 - np.floor(h_shifted * 6.0)
    p = v * (1.0 - s)
    q = v * (1.0 - s * f)
    t = v * (1.0 - s * (1.0 - f))
    r_out = np.choose(i, [v, q, p, p, t, v])
    g_out = np.choose(i, [t, v, v, q, p, p])
    b_out = np.choose(i, [p, p, t, v, v, q])

    new_r = np.clip(r_out * 255.0, 0, 255).astype(np.uint8)
    new_g = np.clip(g_out * 255.0, 0, 255).astype(np.uint8)
    new_b = np.clip(b_out * 255.0, 0, 255).astype(np.uint8)

    # Update only the masked pixels; leave low-sat / transparent ones unchanged.
    out[..., 0] = np.where(apply_mask, new_r, rgba[..., 0])
    out[..., 1] = np.where(apply_mask, new_g, rgba[..., 1])
    out[..., 2] = np.where(apply_mask, new_b, rgba[..., 2])
    return out


# ============================================================
# Palette — AE2-CS extended color set (separate from GuiTextureGenerator's Palette)
# ============================================================
class Palette:
    # === Generic window ===
    OUTLINE = _c(0x413F54)
    HIGHLIGHT = _c(0xF2F2F2)
    SHADOW = _c(0x696D88)
    SCREW = _c(0x4D4D67)
    # === Body chassis ===
    BODY_FILL = _c(0xADB0C4)
    BODY_TOP = _c(0x9A9FB4)
    BODY_DEEP = _c(0x878FA5)
    # === Accent purple ===
    PURPLE = _c(0x915DCD)
    PURPLE_DARK = _c(0x6054A6)
    PURPLE_LIGHT = _c(0xA79CFF)
    PURPLE_GLOW = _c(0xC8B6FF)
    # === FE red (energy core) ===
    FE_RED = _c(0xD22A2A)
    FE_RED_DARK = _c(0x801010)
    FE_RED_LIGHT = _c(0xFF9090)
    # === Yellow / electric core ===
    ELECTRIC = _c(0xFFC107)
    ELECTRIC_HOT = _c(0xFFF080)
    ELECTRIC_DIM = _c(0xC68F08)
    # === Metal / ingot ===
    METAL_HIGHLIGHT = _c(0xE6E8F2)
    METAL_BASE = _c(0xB5B8CC)
    METAL_SHADE = _c(0x6E7385)
    # === Paper / voucher ===
    PAPER = _c(0xE8DDB8)
    PAPER_DARK = _c(0xC8B580)
    INK_BROWN = _c(0x8C5A1F)
    INK_DARK = _c(0x4D2E0E)
    # === Compass / surveyor (brass) ===
    BRASS = _c(0xB5872C)
    BRASS_DARK = _c(0x5D4520)
    NORTH_RED = _c(0xD22A2A)
    SOUTH_BLUE = _c(0x373B72)
    # === Link card ===
    GOLD_LIGHT = _c(0xFFD75A)
    # === Wireless (cyan) ===
    CYAN_LIGHT = _c(0x80D7FF)
    CYAN_DARK = _c(0x1F4D6E)
    # === Shop (teal) ===
    TEAL = _c(0x2C8C8C)
    TEAL_DARK = _c(0x18504F)
    TEAL_LIGHT = _c(0x6FBDB9)
    # === Miner (silver) ===
    SILVER = _c(0xC4C8D6)
    SILVER_DARK = _c(0x747890)
    # === Currency generator (gold) ===
    GOLD = _c(0xD4A434)
    GOLD_DARK = _c(0x8C6A1B)
    GOLD_HIGH = _c(0xFFE680)
    # === Electric absorber (glass purple) ===
    ABSORBER_GLASS = _c(0x8C7AC0)
    ABSORBER_GLASS_DARK = _c(0x4A3D80)
    ABSORBER_GLASS_ON = _c(0xC8B6FF)

    # ============================================================
    # AE2-CS extended palette — Crystal Science gem & card style
    # v2: derived from analyzer_tools.py extract_palette --top 32
    #     across 101 AE2-CS sprites (20983 px sample).
    #
    # Each gem ramp uses 4 luminance steps: DEEP / MID / HI / GLINT picked from
    # the bucketed colors. Constants below are the real AE2-CS pixel values,
    # not invented "AE2-style" approximations.
    # ============================================================
    # Silver chassis — pulled from achromatic 01_black + 02_dark_gray + 03_light_gray buckets
    SILVER_HI = _c(0xCBCCD4)     # was #E6E8F2 — AE2 light_gray top sample
    SILVER_MID = _c(0x7F7F7F)    # was #C0C4D6 — AE2 dark_gray #1 (4.30%)
    SILVER_LOW = _c(0x515151)    # was #8088A0 — AE2 dark_gray #2 (3.25%)
    SILVER_BLACK = _c(0x212222)  # was #282834 — AE2 black #1 (0.50%)
    # Card base — paper-card colors; we keep slightly purple-tinted for readability
    # but anchored to AE2 light_gray family so jaccard agrees
    CARD_BASE_HI = _c(0xDEDFE3)  # was #E2E4F0 — AE2 white_off
    CARD_BASE = _c(0x8F8F8F)     # was #B2B6C8 — AE2 light_gray top
    CARD_BASE_LOW = _c(0x515151) # was #6F7488 — AE2 dark_gray #2
    # Red crystal — AE2 red bucket samples
    RED_GEM_HI = _c(0xFF8585)    # was #FF8888
    RED_GEM = _c(0xAA212B)       # was #D03030 — AE2 red #1
    RED_GEM_DEEP = _c(0x84122A)  # was #6A0808 — AE2 red dark
    RED_GEM_GLINT = _c(0xFFCBBC) # was #FFC0C0 — AE2 red glint
    # Gold crystal — AE2 yellow bucket (sparse, 6 unique)
    GOLD_GEM_HI = _c(0xFAF599)   # was #FFE680 — AE2 yellow hi
    GOLD_GEM = _c(0xF1D13E)      # was #D4A434 — AE2 yellow #1
    GOLD_GEM_DEEP = _c(0x6A4810) # kept (no good AE2 dark-yellow sample)
    GOLD_GEM_GLINT = _c(0xF4FFB5) # was #FFFFC8 — AE2 yellow glint
    # Violet crystal — AE2 violet bucket #B66FFF dominates
    VIOLET_GEM_HI = _c(0xB66FFF) # was #C080FF — AE2 violet #1 (0.88%)
    VIOLET_GEM = _c(0x704091)    # was #6038A8 — AE2 violet mid
    VIOLET_GEM_DEEP = _c(0x352D68) # was #2A1058 — AE2 blue/violet deep
    VIOLET_GEM_GLINT = _c(0xC096FF) # was #E6B0FF — AE2 violet glint
    # Cyan crystal — AE2 cyan bucket dominant (#ACE9FF, #91E3FF, #DAFFFF)
    CYAN_GEM_HI = _c(0xACE9FF)   # was #C0F0FF — AE2 cyan #1 (2.06%)
    CYAN_GEM = _c(0x5C86AF)      # was #4090C8 — AE2 blue mid
    CYAN_GEM_DEEP = _c(0x284654) # was #103060 — AE2 cyan deep
    CYAN_GEM_GLINT = _c(0xDAFFFF) # was #E0F8FF — AE2 cyan glint
    # Teal crystal (glass / hologram) — AE2 cyan/green darks
    TEAL_GEM_HI = _c(0x91E3FF)   # was #80E8E0 — AE2 cyan hi
    TEAL_GEM = _c(0x258474)      # was #2E8C84 — AE2 cyan/teal mid
    TEAL_GEM_DEEP = _c(0x105E51) # was #103030 — AE2 cyan/teal deep
    # LEDs — AE2 reds keep the punchy saturation
    LED_RED = _c(0xD73E42)       # was #FF3838 — AE2 red #2
    LED_RED_DARK = _c(0x84122A)  # was #801010 — AE2 red dark


# ============================================================
# Helpers — match Java helper methods 1:1
# ============================================================
class Helpers:
    """Asset-specific drawing helpers (pixel / circle / card / chevron / frame)."""

    @staticmethod
    def canvas() -> np.ndarray:
        return Primitives.canvas(SIZE, SIZE)

    @staticmethod
    def pixel(img: np.ndarray, x: int, y: int, color) -> None:
        """Set a single pixel with bounds check (matches Java pixel())."""
        Primitives.fill_rect(img, x, y, 1, 1, color)

    @staticmethod
    def load_ae2_template(name: str, hue_shift_deg: float = 0.0) -> np.ndarray:
        """Load an AE2-CS 16×16 texture as a numpy RGBA array, optionally hue-shifted.

        ``name`` is the filename without ``.png`` (e.g. ``"ender_interface_upgrade"``).
        Looked up under ``tools/ae2cs/extracted/assets/ae2cs/textures/item/``.
        The hue shift uses ``_hue_shift_rgba`` which preserves near-gray pixels
        (frame outlines, shelf rows) — only the saturated "gem" pixels are recolored.

        Raises ``FileNotFoundError`` if the template is missing — meaning the
        AE2-CS asset extraction step hasn't been run.
        """
        src = AE2_TEMPLATE_DIR / f"{name}.png"
        if not src.is_file():
            raise FileNotFoundError(
                f"AE2-CS template not found: {src}\n"
                f"  Run the AE2-CS extraction step first (see tools/ae2cs/README.md)."
            )
        img = np.array(Image.open(src).convert("RGBA"))
        if hue_shift_deg != 0:
            img = _hue_shift_rgba(img, hue_shift_deg)
        return img

    @staticmethod
    def draw_rect_outline(img: np.ndarray, x: int, y: int, w: int, h: int, color) -> None:
        """1-px outline only (no fill). Matches Java drawRect / drawOutline."""
        P = Primitives
        P.fill_rect(img, x, y, w, 1, color)
        P.fill_rect(img, x, y + h - 1, w, 1, color)
        P.fill_rect(img, x, y, 1, h, color)
        P.fill_rect(img, x + w - 1, y, 1, h, color)

    @staticmethod
    def fill_circle(img: np.ndarray, cx: int, cy: int, r: int, color) -> None:
        """Filled circle via row-scan with sqrt (matches Java fillCircle)."""
        for dy in range(-r, r + 1):
            dx_max = int(math.floor(math.sqrt(r * r - dy * dy)))
            Primitives.fill_rect(img, cx - dx_max, cy + dy, dx_max * 2 + 1, 1, color)

    @staticmethod
    def draw_circle(img: np.ndarray, cx: int, cy: int, r: int, color) -> None:
        """Bresenham circle outline (matches Java drawCircle exactly).

        Uses the same integer error term ``err`` and update rule as the Java version
        so that pixel placement is bit-identical.
        """
        x, y, err = r, 0, 0
        while x >= y:
            Helpers.pixel(img, cx + x, cy + y, color)
            Helpers.pixel(img, cx + y, cy + x, color)
            Helpers.pixel(img, cx - y, cy + x, color)
            Helpers.pixel(img, cx - x, cy + y, color)
            Helpers.pixel(img, cx - x, cy - y, color)
            Helpers.pixel(img, cx - y, cy - x, color)
            Helpers.pixel(img, cx + y, cy - x, color)
            Helpers.pixel(img, cx + x, cy - y, color)
            y += 1
            err += 1 + 2 * y
            if 2 * (err - x) + 1 > 0:
                x -= 1
                err += 1 - 2 * x

    @staticmethod
    def draw_fat_chevron(img: np.ndarray, x: int, y: int, body, tip) -> None:
        """Bold ``>`` chevron, 2-px thick stair, 5-px tall (matches Java drawFatChevron)."""
        P = Primitives
        P.fill_rect(img, x, y - 2, 2, 1, body)
        P.fill_rect(img, x + 1, y - 1, 2, 1, body)
        P.fill_rect(img, x + 2, y, 2, 1, body)
        P.fill_rect(img, x + 1, y + 1, 2, 1, body)
        P.fill_rect(img, x, y + 2, 2, 1, body)
        Helpers.pixel(img, x + 3, y, tip)

    @staticmethod
    def draw_crystal_gem_block(hi, mid, low, dim) -> np.ndarray:
        """AE2-CS crystal gem block frame (4-layer recess + central crystal cavity).

        Matches Java drawCrystalGemBlock exactly.
        """
        P = Primitives
        img = Helpers.canvas()
        # Edge 0: pure black outer outline
        P.fill_rect(img, 0, 0, 16, 16, Palette.SILVER_BLACK)
        # Edge 1: hi (top+left) / low (bottom+right) bevel
        P.fill_rect(img, 1, 1, 14, 1, hi)
        P.fill_rect(img, 1, 1, 1, 14, hi)
        P.fill_rect(img, 1, 14, 14, 1, low)
        P.fill_rect(img, 14, 1, 1, 14, low)
        # Edge 2: main body mid
        P.fill_rect(img, 2, 2, 12, 12, mid)
        # Central 6×6 crystal cavity (dim)
        P.fill_rect(img, 5, 5, 6, 6, dim)
        # Inner 1-px black outline (depth boost)
        Helpers.draw_rect_outline(img, 4, 4, 8, 8, Palette.SILVER_BLACK)
        # 4-corner sparkles
        Helpers.pixel(img, 1, 1, Palette.HIGHLIGHT)
        Helpers.pixel(img, 14, 14, low)
        return img

    @staticmethod
    def draw_card_silver_3d(accent_top, accent_mid, accent_low) -> np.ndarray:
        """AE2-CS silver 3D card (matches Java drawCardSilver3D)."""
        P = Primitives
        img = Helpers.canvas()
        # Body 11×13 (x=2..12, y=1..13)
        P.fill_rect(img, 2, 1, 11, 13, Palette.CARD_BASE)
        # Top 1-px highlight
        P.fill_rect(img, 2, 1, 11, 1, Palette.CARD_BASE_HI)
        # Left 1-px highlight
        P.fill_rect(img, 2, 1, 1, 13, Palette.CARD_BASE_HI)
        # Bottom 1-px shadow
        P.fill_rect(img, 2, 13, 11, 1, Palette.CARD_BASE_LOW)
        # Right 1-px shadow
        P.fill_rect(img, 12, 1, 1, 13, Palette.CARD_BASE_LOW)
        # Outer outline
        Helpers.draw_rect_outline(img, 2, 1, 11, 13, Palette.SILVER_BLACK)
        # Right-side accent tab (the AE2-CS card's signature feature)
        # position (13, 3) size 2×9
        P.fill_rect(img, 13, 3, 2, 9, accent_mid)
        P.fill_rect(img, 13, 3, 2, 1, accent_top)
        P.fill_rect(img, 13, 11, 2, 1, accent_low)
        Helpers.draw_rect_outline(img, 13, 3, 2, 9, Palette.SILVER_BLACK)
        # 4 corner round-off pixels
        Helpers.pixel(img, 2, 1, Palette.SILVER_BLACK)
        Helpers.pixel(img, 12, 1, Palette.SILVER_BLACK)
        Helpers.pixel(img, 2, 13, Palette.SILVER_BLACK)
        Helpers.pixel(img, 12, 13, Palette.SILVER_BLACK)
        return img

    @staticmethod
    def draw_machine_frame_tinted(body, deep, hi) -> np.ndarray:
        """Generic machine chassis frame (16×16 body + bevel + 4 screws + central panel).

        Currently unused by the active generators (kept for parity with Java's
        drawMachineFrame helper which is also @SuppressWarnings("unused") there).
        """
        P = Primitives
        img = Helpers.canvas()
        P.fill_rect(img, 0, 0, 16, 16, body)
        P.fill_rect(img, 0, 0, 16, 1, hi)
        P.fill_rect(img, 0, 0, 1, 16, hi)
        P.fill_rect(img, 0, 15, 16, 1, deep)
        P.fill_rect(img, 15, 0, 1, 16, deep)
        Helpers.draw_rect_outline(img, 0, 0, 16, 16, Palette.OUTLINE)
        # 4 corner screws
        for sx, sy in [(2, 2), (12, 2), (2, 12), (12, 12)]:
            P.fill_rect(img, sx, sy, 2, 2, Palette.SCREW)
            Helpers.pixel(img, sx, sy, Palette.HIGHLIGHT)
        # Central 8×8 recessed panel
        P.fill_rect(img, 4, 4, 8, 8, deep)
        Helpers.draw_rect_outline(img, 4, 4, 8, 8, Palette.OUTLINE)
        P.fill_rect(img, 5, 5, 6, 1, body)
        return img


# ============================================================
# Items — 5 procedurally generated items (4 hand-drawn ones skipped)
# ============================================================
class Items:
    @staticmethod
    def hash_voucher() -> np.ndarray:
        """AE2-CS ``blank_print_press`` stencil + small violet ``#`` overlay.

        Strategy (user-chosen): use AE2-CS's real ``blank_print_press`` sprite
        as the base — it already has the granite stone-plate look, AE2 frame
        outline and shelf rows we want. No hue shift (the stone is near-gray).
        A compact 3×3 violet ``#`` glyph is then drawn into the center to give
        the voucher its identifying mark.
        """
        img = Helpers.load_ae2_template("blank_print_press")

        # === Compact "#" glyph centered at (8, 6) — 3 wide × 3 tall ===
        # Pattern:
        #   . X .
        #   X X X
        #   . X .
        # rendered slightly larger as 5×5 with double bars to read well at 16x16.
        # Use VIOLET_GEM_DEEP so it sits "carved into" the stone rather than
        # painted on top.
        for dy in range(5):
            for dx in range(5):
                # vertical bars at dx ∈ {1, 3}, horizontal bars at dy ∈ {1, 3}
                if dx in (1, 3) or dy in (1, 3):
                    Helpers.pixel(img, 5 + dx, 4 + dy, Palette.VIOLET_GEM_DEEP)
        # 4 intersection highlights to add depth
        Helpers.pixel(img, 6, 5, Palette.VIOLET_GEM)
        Helpers.pixel(img, 8, 5, Palette.VIOLET_GEM)
        Helpers.pixel(img, 6, 7, Palette.VIOLET_GEM)
        Helpers.pixel(img, 8, 7, Palette.VIOLET_GEM)
        return img

    @staticmethod
    def mining_surveyor() -> np.ndarray:
        """AE2-CS silver instrument w/ cyan screen + red/blue compass needle + 4 LED corners."""
        img = Helpers.draw_crystal_gem_block(
            Palette.SILVER_HI, Palette.SILVER_MID, Palette.SILVER_LOW, Palette.CYAN_GEM_DEEP
        )
        P = Primitives
        # CRT scan lines top/bottom of central 6×6 cavity
        P.fill_rect(img, 5, 5, 6, 1, Palette.CYAN_GEM)
        P.fill_rect(img, 5, 10, 6, 1, Palette.CYAN_GEM)
        # Compass needle: north red top half, south blue bottom half
        P.fill_rect(img, 7, 6, 1, 2, Palette.NORTH_RED)
        P.fill_rect(img, 7, 8, 1, 2, Palette.SOUTH_BLUE)
        # Tip highlights
        Helpers.pixel(img, 7, 6, Palette.RED_GEM_GLINT)
        Helpers.pixel(img, 7, 9, Palette.CYAN_GEM_HI)
        # Center pivot
        Helpers.pixel(img, 7, 7, Palette.HIGHLIGHT)
        # 4 corner red LEDs
        Helpers.pixel(img, 3, 3, Palette.LED_RED)
        Helpers.pixel(img, 12, 3, Palette.LED_RED)
        Helpers.pixel(img, 3, 12, Palette.LED_RED)
        Helpers.pixel(img, 12, 12, Palette.LED_RED)
        return img

    @staticmethod
    def speed_upgrade_card() -> np.ndarray:
        """Hue-shifted AE2-CS ``ender_interface_upgrade`` (green → violet, +110°).

        Strategy (user-chosen): use the real AE2-CS upgrade-card sprite as a
        stencil, then rotate hue by +110° so the green gem body becomes violet
        (matching VirtualCraft's brand violet). The frame outline (#413F54) is
        a near-gray and is preserved by ``_hue_shift_rgba``'s saturation filter.

        Center retains AE2's "interface socket" decoration — semantically
        unrelated to "speed" but it gives the card the unmistakable AE2 look
        the user requested. Discriminating it from ``link_card`` /
        ``wireless_transfer_upgrade_card`` is done by the violet hue.
        """
        return Helpers.load_ae2_template("ender_interface_upgrade", hue_shift_deg=110.0)

    @staticmethod
    def wireless_transfer_upgrade_card() -> np.ndarray:
        """AE2-CS 3D silver card w/ cyan tab + central WiFi fan (source + 2 arcs)."""
        img = Helpers.draw_card_silver_3d(
            Palette.CYAN_GEM_HI, Palette.CYAN_GEM, Palette.CYAN_GEM_DEEP
        )
        # Signal source point (bottom center (7, 11))
        Helpers.pixel(img, 7, 11, Palette.CYAN_GEM_GLINT)
        Helpers.pixel(img, 7, 10, Palette.CYAN_GEM)
        # Inner arc (small, r≈2)
        Helpers.pixel(img, 5, 9, Palette.CYAN_GEM)
        Helpers.pixel(img, 6, 8, Palette.CYAN_GEM)
        Helpers.pixel(img, 7, 8, Palette.CYAN_GEM_GLINT)
        Helpers.pixel(img, 8, 8, Palette.CYAN_GEM)
        Helpers.pixel(img, 9, 9, Palette.CYAN_GEM)
        # Outer arc (medium, r≈3)
        Helpers.pixel(img, 4, 7, Palette.CYAN_GEM_HI)
        Helpers.pixel(img, 5, 6, Palette.CYAN_GEM_HI)
        Helpers.pixel(img, 7, 5, Palette.CYAN_GEM_HI)
        Helpers.pixel(img, 9, 6, Palette.CYAN_GEM_HI)
        Helpers.pixel(img, 10, 7, Palette.CYAN_GEM_HI)
        Helpers.pixel(img, 6, 6, Palette.CYAN_GEM_HI)
        Helpers.pixel(img, 8, 6, Palette.CYAN_GEM_HI)
        # Center bright point
        Helpers.pixel(img, 7, 7, Palette.HIGHLIGHT)
        return img

    @staticmethod
    def link_card() -> np.ndarray:
        """AE2-CS 3D silver card w/ gold tab + central two-node link (binding semantic)."""
        P = Primitives
        img = Helpers.draw_card_silver_3d(
            Palette.GOLD_GEM_HI, Palette.GOLD_GEM, Palette.GOLD_GEM_DEEP
        )
        # Left node 3×3 (3..5, 6..8)
        P.fill_rect(img, 3, 6, 3, 3, Palette.VIOLET_GEM)
        Helpers.pixel(img, 3, 6, Palette.VIOLET_GEM_GLINT)
        Helpers.pixel(img, 4, 7, Palette.VIOLET_GEM_HI)
        Helpers.pixel(img, 5, 8, Palette.VIOLET_GEM_DEEP)
        Helpers.draw_rect_outline(img, 3, 6, 3, 3, Palette.SILVER_BLACK)
        # Right node 3×3 (8..10, 6..8)
        P.fill_rect(img, 8, 6, 3, 3, Palette.VIOLET_GEM)
        Helpers.pixel(img, 8, 6, Palette.VIOLET_GEM_GLINT)
        Helpers.pixel(img, 9, 7, Palette.VIOLET_GEM_HI)
        Helpers.pixel(img, 10, 8, Palette.VIOLET_GEM_DEEP)
        Helpers.draw_rect_outline(img, 8, 6, 3, 3, Palette.SILVER_BLACK)
        # Energy line between nodes (6..7, 7..8) — 2×2 pink-violet pulse
        P.fill_rect(img, 6, 7, 2, 2, Palette.VIOLET_GEM_GLINT)
        Helpers.pixel(img, 6, 7, Palette.HIGHLIGHT)
        Helpers.pixel(img, 7, 8, Palette.VIOLET_GEM_HI)
        return img


# ============================================================
# Blocks — 4 cube_all single-face textures
# ============================================================
class Blocks:
    @staticmethod
    def electric_ball_generator() -> np.ndarray:
        """Red crystal block + central 4×4 lightning ball (red gem + yellow Z-bolt core)."""
        P = Primitives
        img = Helpers.draw_crystal_gem_block(
            Palette.RED_GEM_HI, Palette.RED_GEM, Palette.RED_GEM_DEEP, Palette.RED_GEM_DEEP
        )
        # Central lightning ball: 4×4 area (6..9, 6..9)
        P.fill_rect(img, 6, 6, 4, 4, Palette.RED_GEM)
        # Ball highlight (top-left 1px white)
        Helpers.pixel(img, 6, 6, Palette.RED_GEM_GLINT)
        Helpers.pixel(img, 7, 6, Palette.RED_GEM_HI)
        # Ball shadow (bottom-right 1px dark)
        Helpers.pixel(img, 9, 9, Palette.RED_GEM_DEEP)
        # Yellow Z-bolt core (4 px)
        Helpers.pixel(img, 7, 7, Palette.ELECTRIC_HOT)
        Helpers.pixel(img, 8, 7, Palette.ELECTRIC)
        Helpers.pixel(img, 7, 8, Palette.ELECTRIC)
        Helpers.pixel(img, 8, 8, Palette.ELECTRIC_HOT)
        # 4 corner sparkles
        Helpers.pixel(img, 3, 3, Palette.RED_GEM_GLINT)
        Helpers.pixel(img, 12, 3, Palette.RED_GEM_GLINT)
        Helpers.pixel(img, 3, 12, Palette.RED_GEM_GLINT)
        Helpers.pixel(img, 12, 12, Palette.RED_GEM_GLINT)
        return img

    @staticmethod
    def wireless_power_tower() -> np.ndarray:
        """Violet crystal block + central antenna tower (vertical crystal column + base)."""
        P = Primitives
        img = Helpers.draw_crystal_gem_block(
            Palette.VIOLET_GEM_HI, Palette.VIOLET_GEM, Palette.VIOLET_GEM_DEEP, Palette.VIOLET_GEM_DEEP
        )
        # Antenna vertical crystal column (cx=7, y=5..9)
        P.fill_rect(img, 7, 5, 1, 5, Palette.VIOLET_GEM)
        P.fill_rect(img, 7, 5, 1, 1, Palette.VIOLET_GEM_GLINT)
        P.fill_rect(img, 7, 9, 1, 1, Palette.VIOLET_GEM_HI)
        # Right-side 1-px shadow stripe
        Helpers.pixel(img, 8, 6, Palette.VIOLET_GEM_DEEP)
        Helpers.pixel(img, 8, 7, Palette.VIOLET_GEM_DEEP)
        Helpers.pixel(img, 8, 8, Palette.VIOLET_GEM_DEEP)
        # Top emitter glow
        Helpers.pixel(img, 7, 4, Palette.VIOLET_GEM_GLINT)
        Helpers.pixel(img, 6, 5, Palette.VIOLET_GEM_HI)
        Helpers.pixel(img, 8, 5, Palette.VIOLET_GEM_HI)
        # Base (6..9, 10)
        P.fill_rect(img, 6, 10, 4, 1, Palette.VIOLET_GEM)
        # Side signal sparks
        Helpers.pixel(img, 5, 6, Palette.VIOLET_GEM_GLINT)
        Helpers.pixel(img, 9, 6, Palette.VIOLET_GEM_GLINT)
        # 4 corner sparkles
        Helpers.pixel(img, 3, 3, Palette.VIOLET_GEM_GLINT)
        Helpers.pixel(img, 12, 3, Palette.VIOLET_GEM_GLINT)
        Helpers.pixel(img, 3, 12, Palette.VIOLET_GEM_GLINT)
        Helpers.pixel(img, 12, 12, Palette.VIOLET_GEM_GLINT)
        return img

    @staticmethod
    def holographic_shop() -> np.ndarray:
        """Teal crystal block + central CRT screen (scan lines + 4×3 hologram orb)."""
        P = Primitives
        img = Helpers.draw_crystal_gem_block(
            Palette.TEAL_GEM_HI, Palette.TEAL_GEM, Palette.TEAL_GEM_DEEP, Palette.TEAL_GEM_DEEP
        )
        # Top CRT scan line
        P.fill_rect(img, 5, 6, 6, 1, Palette.TEAL_GEM_HI)
        # Central 4×3 hologram orb
        P.fill_rect(img, 6, 7, 4, 3, Palette.ELECTRIC)
        P.fill_rect(img, 7, 8, 2, 1, Palette.ELECTRIC_HOT)
        Helpers.pixel(img, 6, 7, Palette.HIGHLIGHT)
        # Bottom CRT scan line
        P.fill_rect(img, 5, 10, 6, 1, Palette.TEAL_GEM_HI)
        # 4 corner sparkles
        Helpers.pixel(img, 3, 3, Palette.TEAL_GEM_HI)
        Helpers.pixel(img, 12, 3, Palette.TEAL_GEM_HI)
        Helpers.pixel(img, 3, 12, Palette.TEAL_GEM_HI)
        Helpers.pixel(img, 12, 12, Palette.TEAL_GEM_HI)
        return img

    @staticmethod
    def pow_miner() -> np.ndarray:
        """Silver chassis + central violet ``#`` hash imprint + 4 corner red LEDs."""
        P = Primitives
        img = Helpers.draw_crystal_gem_block(
            Palette.SILVER_HI, Palette.SILVER_MID, Palette.SILVER_LOW, Palette.VIOLET_GEM_DEEP
        )
        # Central ``#`` violet imprint (5×5 within 6×6 cavity)
        P.fill_rect(img, 5, 6, 6, 1, Palette.VIOLET_GEM_HI)
        P.fill_rect(img, 5, 9, 6, 1, Palette.VIOLET_GEM_HI)
        P.fill_rect(img, 6, 5, 1, 6, Palette.VIOLET_GEM_HI)
        P.fill_rect(img, 9, 5, 1, 6, Palette.VIOLET_GEM_HI)
        # ``#`` central glints
        Helpers.pixel(img, 7, 7, Palette.VIOLET_GEM_GLINT)
        Helpers.pixel(img, 8, 8, Palette.VIOLET_GEM_GLINT)
        # 4 corner red LEDs
        Helpers.pixel(img, 3, 3, Palette.LED_RED)
        Helpers.pixel(img, 12, 3, Palette.LED_RED)
        Helpers.pixel(img, 3, 12, Palette.LED_RED)
        Helpers.pixel(img, 12, 12, Palette.LED_RED)
        # LED shadows
        Helpers.pixel(img, 4, 3, Palette.LED_RED_DARK)
        Helpers.pixel(img, 11, 3, Palette.LED_RED_DARK)
        return img


# ============================================================
# Generator registry — name -> (callable, output dir)
# ============================================================
# ACTIVE: written by default. Verified pixel-equal to current disk PNGs.
GENERATORS = {
    # Items (4 active, 4 hand-drawn skipped, wireless_transfer_upgrade_card retired)
    "hash_voucher": (Items.hash_voucher, ITEM_DIR),
    "mining_surveyor": (Items.mining_surveyor, ITEM_DIR),
    "speed_upgrade_card": (Items.speed_upgrade_card, ITEM_DIR),
    "link_card": (Items.link_card, ITEM_DIR),
    # Blocks (3 active; wireless_power_tower's 32×32 hand-drawn version on disk
    # supersedes the procedural 16×16 — kept in LEGACY_GENERATORS below)
    "electric_ball_generator": (Blocks.electric_ball_generator, BLOCK_DIR),
    "holographic_shop": (Blocks.holographic_shop, BLOCK_DIR),
    "pow_miner": (Blocks.pow_miner, BLOCK_DIR),
}

# LEGACY: callable via --only for retro debug / regression, never written by default.
#   - wireless_transfer_upgrade_card: item retired from the project (no PNG on disk)
#   - wireless_power_tower: replaced by hand-drawn 32×32 PNG (size mismatch by design)
LEGACY_GENERATORS = {
    "wireless_transfer_upgrade_card": (Items.wireless_transfer_upgrade_card, ITEM_DIR),
    "wireless_power_tower": (Blocks.wireless_power_tower, BLOCK_DIR),
}

ALL_GENERATORS = {**GENERATORS, **LEGACY_GENERATORS}


# ============================================================
# IO — save / diff (per-generator output dir aware)
# ============================================================
def save_png(arr: np.ndarray, name: str, out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    out = out_dir / name
    Image.fromarray(arr, "RGBA").save(out)
    h, w = arr.shape[:2]
    print(f"  Wrote {out_dir.name}/{name} ({w}×{h})")


def diff_png(arr: np.ndarray, name: str, out_dir: Path) -> tuple[bool, int]:
    path = out_dir / name
    if not path.exists():
        print(f"  MISS {out_dir.name}/{name}: file not found at {path}")
        return False, -1
    existing = np.array(Image.open(path).convert("RGBA"))
    if existing.shape != arr.shape:
        print(f"  DIFF {out_dir.name}/{name}: shape mismatch {existing.shape} vs {arr.shape}")
        return False, -2
    diff_mask = np.any(existing != arr, axis=-1)
    n_diff = int(np.sum(diff_mask))
    if n_diff == 0:
        print(f"  OK   {out_dir.name}/{name} ({arr.shape[1]}×{arr.shape[0]})")
        return True, 0
    print(f"  DIFF {out_dir.name}/{name}: {n_diff} pixel(s) differ")
    ys, xs = np.where(diff_mask)
    if len(ys) > 0:
        y0, x0 = int(ys[0]), int(xs[0])
        print(f"        first diff at ({x0}, {y0}): "
              f"java={tuple(int(v) for v in existing[y0, x0])} "
              f"py={tuple(int(v) for v in arr[y0, x0])}")
    return False, n_diff


def cli(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="VirtualCraft asset texture generator (Python port of AssetTextureGenerator.java)."
    )
    parser.add_argument(
        "--only",
        choices=list(ALL_GENERATORS.keys()),
        help="Generate only the named texture (default: all active). "
             "LEGACY generators only run when explicitly named via --only.",
    )
    parser.add_argument(
        "--diff",
        action="store_true",
        help="Compare against existing PNG instead of writing.",
    )
    args = parser.parse_args(argv)

    # Default target list excludes LEGACY (those only run when explicitly named).
    targets = [args.only] if args.only else list(GENERATORS.keys())
    print(f"Item dir : {ITEM_DIR.resolve()}")
    print(f"Block dir: {BLOCK_DIR.resolve()}")

    if args.diff:
        n_ok = n_diff = 0
        for name in targets:
            fn, out_dir = ALL_GENERATORS[name]
            arr = fn()
            ok, _ = diff_png(arr, f"{name}.png", out_dir)
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
        fn, out_dir = ALL_GENERATORS[name]
        save_png(fn(), f"{name}.png", out_dir)
    print(f"Done. {len(targets)} texture(s) generated.")
    return 0


if __name__ == "__main__":
    sys.exit(cli())
