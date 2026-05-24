#!/usr/bin/env python3
"""VirtualCraft texture verifiers — Python port of VerifyIcons.java + VerifyVFill.java.

Subcommands:

    icons   VerifyIcons.java   Verify icons.png copy-from-AE2 cells match source + retired cells empty
    vfill   VerifyVFill.java   Verify icons.png 16×64 vertical FE-fill sprite matches design table

Both subcommands exit 0 on full pass, 1 on any failure (suitable for CI / pre-commit).

Examples:

    python tools/verify_tools.py icons
    python tools/verify_tools.py vfill
    python tools/verify_tools.py vfill --quiet         # skip pixel grid + histogram
    python tools/verify_tools.py vfill path/to/icons.png
"""

from __future__ import annotations

import argparse
import sys
from collections import Counter
from pathlib import Path

import numpy as np
from PIL import Image

# Layout: workspace_root/tools/python/verify_tools.py → ROOT = .parents[2]
ROOT = Path(__file__).resolve().parents[2]
GUI_DIR = ROOT / "src" / "main" / "resources" / "assets" / "virtual_craft" / "textures" / "gui"
ICONS_PATH_DEFAULT = GUI_DIR / "icons.png"
AE2_REF_ICONS_PATH = ROOT / "tools" / "refs" / "ae2cs_icons.png"


# ============================================================
# Helpers — RGBA arrays, Java-equivalent ARGB packing
# ============================================================
def _argb(pixel) -> int:
    """Pack RGBA tuple into Java-style 0xAARRGGBB int (matches Java getRGB)."""
    r, g, b, a = (int(v) for v in pixel)
    return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF)


def _load_rgba(path: Path) -> np.ndarray:
    if not path.exists():
        raise FileNotFoundError(f"image not found: {path}")
    return np.array(Image.open(path).convert("RGBA"))


# ============================================================
# Subcommand: icons  (VerifyIcons.java)
# ============================================================
def _check_copy(out: np.ndarray, label: str, wx: int, wy: int,
                ref: np.ndarray, ix: int, iy: int) -> int:
    """Compare a 16×16 region of ``out`` at ``(wx, wy)`` with ``ref`` at ``(ix, iy)``.

    Returns 0 on full match (256/256 pixels), 1 on any mismatch.
    Prints up to 3 individual mismatches for debug.
    """
    mismatched = 0
    for dy in range(16):
        for dx in range(16):
            o_rgb = _argb(out[wy + dy, wx + dx])
            r_rgb = _argb(ref[iy + dy, ix + dx])
            if o_rgb != r_rgb:
                mismatched += 1
                if mismatched <= 3:
                    print(f"  [FAIL] {label} @ +({dx},{dy}): "
                          f"out=#{o_rgb & 0xFFFFFFFF:08X} vs ref=#{r_rgb & 0xFFFFFFFF:08X}")
    if mismatched == 0:
        print(f"  [OK] {label}: 256/256 pixels @ out({wx},{wy}) == ref({ix},{iy})")
        return 0
    print(f"  [FAIL] {label}: {mismatched}/256 pixel(s) mismatch")
    return 1


def _check_empty(out: np.ndarray, label: str, wx: int, wy: int) -> int:
    """Verify a 16×16 region of ``out`` at ``(wx, wy)`` is fully transparent."""
    region = out[wy:wy + 16, wx:wx + 16]
    non_empty = int((region[..., 3] > 0).sum())
    if non_empty == 0:
        print(f"  [OK] {label}: 256/256 pixels fully transparent @ out({wx},{wy})")
        return 0
    print(f"  [FAIL] {label}: {non_empty}/256 pixel(s) not transparent @ out({wx},{wy})")
    return 1


def cmd_icons(args) -> int:
    """Verify icons.png copy cells == AE2 reference + retired cells fully transparent.

    Baseline updated to GuiTextureGenerator v20.7 (Java VerifyIcons.java is stale —
    it still checks LINK at u=112, which v20.7 retired alongside BURN / PICKAXE).

    Checks:
      1. out[48..63, 40..55] (INPUT)   == ref[0..15, 112..127]   (icons cell 0,7)
      2. out[64..79, 40..55] (OUTPUT)  == ref[16..31, 112..127]  (icons cell 1,7)
      3. out[96..111, 40..55] (BURN)    fully transparent  (retired v8)
      4. out[112..127, 40..55] (LINK)   fully transparent  (retired v20.7)
      5. out[128..143, 40..55] (PICKAXE) fully transparent (retired v8)
    """
    out = _load_rgba(Path(args.icons))
    ref = _load_rgba(AE2_REF_ICONS_PATH)
    errors = 0

    # Copy-from-AE2 icons (v10: input/output use AE2 shape (0,7)/(1,7))
    errors += _check_copy(out, "INPUT", 48, 40, ref, 0, 112)
    errors += _check_copy(out, "OUTPUT", 64, 40, ref, 16, 112)

    # All retired cells must be fully empty
    errors += _check_empty(out, "BURN (u=96)", 96, 40)
    errors += _check_empty(out, "LINK (u=112)", 112, 40)
    errors += _check_empty(out, "PICKAXE (u=128)", 128, 40)

    print()
    if errors == 0:
        print("[OK] All icon checks passed!")
        return 0
    print(f"[FAIL] {errors} check(s) failed.")
    return 1


# ============================================================
# Subcommand: vfill  (VerifyVFill.java)
# ============================================================
# Baseline updated to GuiTextureGenerator v21 (Java VerifyVFill.java is stale —
# its expected table still uses the original MCPP reference border (col 0 #373737,
# col 15 #FFFFFF, top row gray, bottom row white). v20.7 unified borders to
# WINDOW_HIGH #F2F2F2; v21 then dropped the bottom-row border entirely so the
# satisfying-fill blit doesn't stack a 2-px-thick white line under the energy
# bar at high charge (user feedback). Interior columns now extend to y=63.
#
# Expected mapping (col 0..15) → (light row color, dark row color) for interior cells:
#   col 0:  ALL #F2F2F2 (v20.7 — was all #373737)
#   col 1..7:  graded red light/dark (steps 1..7)
#   col 8..14: same gradient mirrored (steps 7..1)
#   col 15: ALL #F2F2F2 (v20.7 — was all #FFFFFF)
EXPECTED_LIGHT_DARK = [
    (0xF2F2F2, 0xF2F2F2),  # col 0  (left border, v20.7 unified)
    (0x420000, 0x2B0000),  # col 1  step 1
    (0x5C0000, 0x3C0000),  # col 2  step 2
    (0x7A0000, 0x500000),  # col 3  step 3
    (0x990000, 0x640000),  # col 4  step 4
    (0xB80000, 0x780000),  # col 5  step 5
    (0xD60000, 0x8C0000),  # col 6  step 6
    (0xF50000, 0xA00000),  # col 7  step 7 (brightest)
    (0xF50000, 0xA00000),  # col 8  step 7 mirror
    (0xD60000, 0x8C0000),  # col 9  step 6 mirror
    (0xB80000, 0x780000),  # col 10 step 5 mirror
    (0x990000, 0x640000),  # col 11 step 4 mirror
    (0x7A0000, 0x500000),  # col 12 step 3 mirror
    (0x5C0000, 0x3C0000),  # col 13 step 2 mirror
    (0x420000, 0x2B0000),  # col 14 step 1 mirror
    (0xF2F2F2, 0xF2F2F2),  # col 15 (right border, v20.7 unified)
]

VFILL_BASE_X = 240
VFILL_W = 16
VFILL_H = 64
VFILL_BORDER = 0xF2F2F2  # v20.7 WINDOW_HIGH


def cmd_vfill(args) -> int:
    """Verify icons.png vertical FE-fill sprite at (240, 0..63) follows v20.7 design."""
    img = _load_rgba(Path(args.icons))
    base_x, w, h = VFILL_BASE_X, VFILL_W, VFILL_H
    errors = 0

    print(f"=== Verify {args.icons} sprite region "
          f"[x={base_x}..{base_x + w - 1}, y=0..{h - 1}] ===")

    def rgb_no_alpha(pixel) -> int:
        r, g, b, _ = (int(v) for v in pixel)
        return (r << 16) | (g << 8) | b

    for col in range(w):
        x = base_x + col
        light_exp, dark_exp = EXPECTED_LIGHT_DARK[col]

        for y in range(h):
            actual = rgb_no_alpha(img[y, x])

            if col == 0 or col == w - 1:
                expected = VFILL_BORDER
                reason = f"col {col} left/right border"
            elif y == 0:
                # v21: only top border remains (bottom border removed so the
                # satisfying-fill blit doesn't double the WINDOW_HIGH stripe).
                expected = VFILL_BORDER
                reason = "top row border"
            elif y % 2 == 1:
                expected = light_exp
                reason = "odd y (light)"
            else:
                expected = dark_exp
                reason = "even y (dark)"

            if actual != expected:
                errors += 1
                if errors <= 20:
                    print(f"  MISMATCH @ (x={x}, y={y}) [col={col}, {reason}]  "
                          f"expected #{expected:06X}  actual #{actual:06X}")

    if not args.quiet:
        # Per-row pixel grid dump (for visual debug)
        print(f"\n=== Actual pixel grid (x={base_x}..{base_x + w - 1}, y=0..{h - 1}) ===")
        for y in range(h):
            row = []
            for col in range(w):
                row.append(f"#{rgb_no_alpha(img[y, base_x + col]):06X}")
            print(f"  y={y:2d}: " + " ".join(row))

        # Color histogram in sprite region
        print("\n=== Color histogram in sprite region ===")
        region = img[0:h, base_x:base_x + w]
        packed = (region[..., 0].astype(np.uint32) << 16) \
                 | (region[..., 1].astype(np.uint32) << 8) \
                 | region[..., 2].astype(np.uint32)
        counter = Counter(int(v) for v in packed.flatten().tolist())
        total = w * h
        for rgb, count in counter.most_common():
            pct = 100.0 * count / total
            print(f"  #{rgb:06X}  {count:4d} px  {pct:5.2f}%")

    total = w * h
    print()
    if errors == 0:
        print(f"[OK] All {total} pixels match design spec!")
        return 0
    print(f"[FAIL] {errors} pixel(s) do not match design.")
    return 1


# ============================================================
# CLI dispatch
# ============================================================
def cli(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="verify_tools.py",
        description=__doc__.split("\n\n")[0],
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_icons = sub.add_parser("icons", help="Verify icons.png copy cells + retired cells empty")
    p_icons.add_argument("icons", nargs="?", default=str(ICONS_PATH_DEFAULT),
                         help="Path to icons.png (default: src/.../gui/icons.png)")

    p_vfill = sub.add_parser("vfill", help="Verify 16×64 vertical FE-fill sprite design")
    p_vfill.add_argument("icons", nargs="?", default=str(ICONS_PATH_DEFAULT),
                         help="Path to icons.png (default: src/.../gui/icons.png)")
    p_vfill.add_argument("--quiet", action="store_true",
                         help="Skip per-row pixel grid + color histogram")

    args = parser.parse_args(argv)
    handler = {
        "icons": cmd_icons,
        "vfill": cmd_vfill,
    }[args.cmd]
    return handler(args)


if __name__ == "__main__":
    sys.exit(cli())
