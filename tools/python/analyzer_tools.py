#!/usr/bin/env python3
"""VirtualCraft analyzer tools — Python port of ColorSampler.java + PixelAnalyzer.java.

Subcommands:

    color_sample   ColorSampler.java     Histogram top-N opaque colors + sample fixed pixel coords
    pixel_analyze  PixelAnalyzer.java    Row/column sweep + nearest/block-average downsample dump

Examples:

    python tools/analyzer_tools.py color_sample tools/refs/inscriber.png
    python tools/analyzer_tools.py color_sample tools/refs/ae2cs_icons.png src/main/resources/assets/virtual_craft/textures/gui/icons.png
    python tools/analyzer_tools.py pixel_analyze tools/mc_pixel_perfect.png
"""

from __future__ import annotations

import argparse
import sys
from collections import Counter
from pathlib import Path

import numpy as np
from PIL import Image


# ============================================================
# Subcommand: color_sample  (ColorSampler.java)
# ============================================================
def cmd_color_sample(args) -> int:
    """For each input PNG: histogram of opaque colors (top 30) + fixed pixel samples."""
    # Common AE2 176×166 background sample positions
    sample_coords = [
        ("1,1", "outer-tl"),
        ("4,4", "border-area"),
        ("8,8", "inner-bg"),
        ("50,30", "mid-bg"),
        ("80,30", "near-arrow"),
        ("100,50", "far-bg"),
        ("7,82", "above-inv"),
        ("7,84", "inv-frame-tl"),
        ("8,85", "inv-slot-outer-tl"),
        ("9,86", "inv-slot-fill"),
        ("15,85", "inv-slot-bottom"),
    ]

    for path in args.paths:
        p = Path(path)
        img = np.array(Image.open(p).convert("RGBA"))
        H, W = img.shape[:2]
        print(f"=== {path} ({W}x{H}) ===")

        # Histogram of opaque pixels (alpha >= 128)
        opaque = img[img[..., 3] >= 128]
        # Pack RGB into a single uint32 for fast counting
        packed = (opaque[:, 0].astype(np.uint32) << 16) \
                 | (opaque[:, 1].astype(np.uint32) << 8) \
                 | opaque[:, 2].astype(np.uint32)
        counter = Counter(int(v) for v in packed.tolist())
        total = sum(counter.values())
        unique = len(counter)
        print(f"  Total non-transparent: {total}, unique colors: {unique}")
        print("  Top 30 by frequency:")
        for rgb, count in counter.most_common(30):
            r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
            pct = 100.0 * count / total if total else 0.0
            print(f"    #{rgb:06X}  rgb({r:3d},{g:3d},{b:3d})  {count:6d} px  {pct:5.2f}%")

        print("  Specific pixel samples:")
        for coord, label in sample_coords:
            xs, ys = coord.split(",")
            x, y = int(xs), int(ys)
            if x >= W or y >= H:
                continue
            r, g, b, _ = (int(v) for v in img[y, x])
            rgb = (r << 16) | (g << 8) | b
            print(f"    ({x:3d},{y:3d}) [{label}]  #{rgb:06X}  rgb({r:3d},{g:3d},{b:3d})")
        print()
    return 0


# ============================================================
# Subcommand: pixel_analyze  (PixelAnalyzer.java)
# ============================================================
def cmd_pixel_analyze(args) -> int:
    """Row/column sweep + nearest-neighbor & block-average downsample of a sprite."""
    p = Path(args.input)
    img = np.array(Image.open(p).convert("RGB"))  # RGB-only — drop alpha for analysis
    H, W = img.shape[:2]
    print(f"Size: {W} x {H}")

    def to_rgb(pixel) -> int:
        r, g, b = (int(v) for v in pixel)
        return (r << 16) | (g << 8) | b

    # Column sweep at y=H/2
    print(f"\n=== Column sweep (y = H/2 = {H // 2}) ===")
    for x in range(W):
        rgb = to_rgb(img[H // 2, x])
        if x % 16 == 0:
            print(f"\n  x={x:3d}: ", end="")
        print(f"#{rgb:06X} ", end="")
    print()

    # Row sweep at x=W/2 with run-length encoding
    print(f"\n=== Row sweep (x = W/2 = {W // 2}) ===")
    prev = -1
    run_start = 0
    for y in range(H):
        rgb = to_rgb(img[y, W // 2])
        if rgb != prev:
            if prev != -1:
                print(f"  y={run_start:3d}..{y - 1:3d} ({y - run_start:2d} px): #{prev:06X}")
            prev = rgb
            run_start = y
    print(f"  y={run_start:3d}..{H - 1:3d} ({H - run_start:2d} px): #{prev:06X}")

    # Multi-column run-length scan
    print("\n=== Multi-col run-length on specific columns ===")
    test_cols = [0, 5, 10, 20, 40, 60, 80, 100, 120, 140, 155, 159]
    for x in test_cols:
        if x >= W:
            continue
        print(f"\n-- Column x={x} --")
        prev = -1
        run_start = 0
        for y in range(H):
            rgb = to_rgb(img[y, x])
            if rgb != prev:
                if prev != -1:
                    print(f"  y={run_start:3d}..{y - 1:3d} ({y - run_start:2d}): #{prev:06X}")
                prev = rgb
                run_start = y
        print(f"  y={run_start:3d}..{H - 1:3d} ({H - run_start:2d}): #{prev:06X}")

    # Nearest-neighbor downsample to 8x64 and 16x64
    for target_w in (8, 16):
        target_h = 64
        print(f"\n=== Nearest-neighbor downsample to {target_w}x{target_h} ===")
        sx = W / target_w
        sy = H / target_h
        for ty in range(target_h):
            src_y = int((ty + 0.5) * sy)
            row = []
            for tx in range(target_w):
                src_x = int((tx + 0.5) * sx)
                row.append(f"#{to_rgb(img[src_y, src_x]):06X}")
            print(f"  y={ty:2d}: " + " ".join(row))

    # Block-average downsample to 8x64
    print("\n=== Block-average downsample to 8x64 ===")
    tw, th = 8, 64
    for ty in range(th):
        y0 = ty * H // th
        y1 = (ty + 1) * H // th
        row = []
        for tx in range(tw):
            x0 = tx * W // tw
            x1 = (tx + 1) * W // tw
            block = img[y0:y1, x0:x1]
            mean = block.reshape(-1, 3).mean(axis=0)
            r, g, b = int(mean[0]), int(mean[1]), int(mean[2])
            row.append(f"#{(r << 16) | (g << 8) | b:06X}")
        print(f"  y={ty:2d}: " + " ".join(row))
    return 0


# ============================================================
# Subcommand: audit  (AE2-CS visual fidelity report)
# ============================================================
ROOT = Path(__file__).resolve().parents[2]
AE2_ITEM_DIR = ROOT / "tools" / "ae2cs" / "extracted" / "assets" / "ae2cs" / "textures" / "item"
AE2_BLOCK_DIR = ROOT / "tools" / "ae2cs" / "extracted" / "assets" / "ae2cs" / "textures" / "block"
VC_ITEM_DIR = ROOT / "src" / "main" / "resources" / "assets" / "virtual_craft" / "textures" / "item"
VC_BLOCK_DIR = ROOT / "src" / "main" / "resources" / "assets" / "virtual_craft" / "textures" / "block"
PREVIEWS_DIR = ROOT / "tools" / "previews"

# VirtualCraft procedural textures from asset_texture_generator.py (skip hand-drawn ones)
VC_PROCEDURAL_ITEMS = [
    "hash_voucher", "mining_surveyor", "speed_upgrade_card", "link_card",
]
VC_PROCEDURAL_BLOCKS = [
    "electric_ball_generator", "holographic_shop", "pow_miner",
]


def _load_16x16(path: Path) -> np.ndarray | None:
    """Load PNG as RGBA, return None if not 16×16 or missing."""
    if not path.exists():
        return None
    img = np.array(Image.open(path).convert("RGBA"))
    if img.shape[:2] != (16, 16):
        return None
    return img


def _palette_set(img: np.ndarray, alpha_threshold: int = 128) -> set[int]:
    """Set of opaque RGB colors as packed 0xRRGGBB ints."""
    opaque = img[img[..., 3] >= alpha_threshold]
    if len(opaque) == 0:
        return set()
    packed = (opaque[:, 0].astype(np.uint32) << 16) \
             | (opaque[:, 1].astype(np.uint32) << 8) \
             | opaque[:, 2].astype(np.uint32)
    return set(int(v) for v in packed.tolist())


def _alpha_mask(img: np.ndarray, alpha_threshold: int = 128) -> np.ndarray:
    """Boolean 16×16 mask of opaque pixels (the silhouette)."""
    return img[..., 3] >= alpha_threshold


def _palette_jaccard(a: set[int], b: set[int]) -> float:
    """Jaccard similarity of two color sets, 0..1."""
    if not a and not b:
        return 1.0
    union = a | b
    return len(a & b) / len(union) if union else 0.0


def _silhouette_iou(a: np.ndarray, b: np.ndarray) -> float:
    """IoU of two boolean alpha masks."""
    inter = int((a & b).sum())
    union = int((a | b).sum())
    return inter / union if union else 0.0


def _classify_geometry(img: np.ndarray) -> str:
    """Categorize a 16×16 sprite by its non-transparent silhouette shape."""
    mask = _alpha_mask(img)
    coverage = mask.sum() / 256.0
    if coverage == 0:
        return "empty"
    if coverage > 0.95:
        return "block (full-bleed 16×16, e.g. cube_all)"
    # Bounding box
    ys, xs = np.where(mask)
    y0, y1 = int(ys.min()), int(ys.max())
    x0, x1 = int(xs.min()), int(xs.max())
    bbox_w = x1 - x0 + 1
    bbox_h = y1 - y0 + 1
    bbox_area = bbox_w * bbox_h
    fill_ratio = mask.sum() / bbox_area  # how filled is the bbox?
    aspect = bbox_w / bbox_h
    # Heuristics
    if bbox_h <= 8 and aspect >= 1.5:
        return "ingot (short, wide trapezoid)"
    if bbox_w >= 12 and bbox_h >= 12 and fill_ratio > 0.85:
        return "card (rectangular, near-full bbox)"
    if 0.7 <= aspect <= 1.4 and fill_ratio < 0.7:
        return "crystal/orb (compact, irregular silhouette)"
    if aspect < 0.6 or aspect > 1.7:
        return "tool (elongated)"
    return "compound (irregular)"


def _top_n_colors(img: np.ndarray, n: int = 5) -> list[tuple[int, int]]:
    """Top-N (rgb, count) pairs."""
    opaque = img[img[..., 3] >= 128]
    if len(opaque) == 0:
        return []
    packed = (opaque[:, 0].astype(np.uint32) << 16) \
             | (opaque[:, 1].astype(np.uint32) << 8) \
             | opaque[:, 2].astype(np.uint32)
    return Counter(int(v) for v in packed.tolist()).most_common(n)


def cmd_audit(args) -> int:
    """Generate an AE2-CS fidelity report for VirtualCraft's procedural textures."""
    # Collect all AE2-CS 16×16 references (items + blocks)
    ae2_refs: dict[str, np.ndarray] = {}
    for d, prefix in [(AE2_ITEM_DIR, "item/"), (AE2_BLOCK_DIR, "block/")]:
        if not d.exists():
            continue
        for png in sorted(d.glob("*.png")):
            arr = _load_16x16(png)
            if arr is not None:
                ae2_refs[prefix + png.stem] = arr
    print(f"Loaded {len(ae2_refs)} AE2-CS 16×16 reference textures.")

    # Pre-compute palette sets + alpha masks for all references (perf)
    ref_palettes = {name: _palette_set(arr) for name, arr in ae2_refs.items()}
    ref_masks = {name: _alpha_mask(arr) for name, arr in ae2_refs.items()}

    # Score each VirtualCraft procedural texture
    vc_targets = [(n, VC_ITEM_DIR / f"{n}.png", "item") for n in VC_PROCEDURAL_ITEMS] + \
                 [(n, VC_BLOCK_DIR / f"{n}.png", "block") for n in VC_PROCEDURAL_BLOCKS]

    report_lines = []
    report_lines.append("# VirtualCraft × AE2-CS Visual Fidelity Audit")
    report_lines.append("")
    report_lines.append(f"Compared **{len(vc_targets)} VirtualCraft procedural textures** against "
                        f"**{len(ae2_refs)} AE2-CS 16×16 references**.")
    report_lines.append("")
    report_lines.append("Similarity score = `0.5 × palette_jaccard + 0.5 × silhouette_iou`. "
                        "Higher is closer to AE2-CS visual identity.")
    report_lines.append("")

    print()
    print(f"{'VirtualCraft asset':<35}  {'Geom':<45}  Top-3 closest AE2-CS refs (similarity)")
    print("-" * 140)

    for vc_name, vc_path, vc_kind in vc_targets:
        vc_arr = _load_16x16(vc_path)
        if vc_arr is None:
            print(f"{vc_name:<35}  (missing or non-16×16)")
            continue
        vc_pal = _palette_set(vc_arr)
        vc_mask = _alpha_mask(vc_arr)
        vc_geom = _classify_geometry(vc_arr)

        scores = []
        for ref_name, ref_arr in ae2_refs.items():
            pal_score = _palette_jaccard(vc_pal, ref_palettes[ref_name])
            iou_score = _silhouette_iou(vc_mask, ref_masks[ref_name])
            combined = 0.5 * pal_score + 0.5 * iou_score
            scores.append((ref_name, combined, pal_score, iou_score))
        scores.sort(key=lambda x: x[1], reverse=True)
        top3 = scores[:3]

        top3_str = "  ".join(f"{n} ({s:.2f})" for n, s, _, _ in top3)
        print(f"{vc_name:<35}  {vc_geom:<45}  {top3_str}")

        # Markdown report
        report_lines.append(f"## `{vc_kind}/{vc_name}`")
        report_lines.append("")
        report_lines.append(f"- **Geometry**: {vc_geom}")
        report_lines.append(f"- **Unique opaque colors**: {len(vc_pal)}")
        top_colors = _top_n_colors(vc_arr, n=5)
        if top_colors:
            top_color_str = ", ".join(f"`#{rgb:06X}` ({c}px)" for rgb, c in top_colors)
            report_lines.append(f"- **Top-5 colors**: {top_color_str}")
        report_lines.append("")
        report_lines.append("**Top-5 closest AE2-CS references** (similarity / palette / silhouette IoU):")
        report_lines.append("")
        report_lines.append("| Rank | AE2-CS reference | Combined | Palette Jaccard | Silhouette IoU |")
        report_lines.append("|---:|---|---:|---:|---:|")
        for i, (ref_name, combined, pal_s, iou_s) in enumerate(scores[:5], 1):
            report_lines.append(f"| {i} | `{ref_name}` | **{combined:.3f}** | {pal_s:.3f} | {iou_s:.3f} |")
        report_lines.append("")

    # Geometry-mode distribution across AE2-CS catalog
    print()
    print("AE2-CS geometry mode distribution:")
    print("-" * 60)
    geom_counter: Counter = Counter()
    for name, arr in ae2_refs.items():
        geom_counter[_classify_geometry(arr)] += 1
    for geom, count in geom_counter.most_common():
        pct = 100.0 * count / len(ae2_refs)
        print(f"  {geom:<55}  {count:4d}  {pct:5.1f}%")

    report_lines.append("---")
    report_lines.append("")
    report_lines.append("## AE2-CS catalog geometry breakdown")
    report_lines.append("")
    report_lines.append("| Geometry mode | Count | % |")
    report_lines.append("|---|---:|---:|")
    for geom, count in geom_counter.most_common():
        pct = 100.0 * count / len(ae2_refs)
        report_lines.append(f"| {geom} | {count} | {pct:.1f}% |")
    report_lines.append("")

    # Aggregate VC scores
    print()
    print("VirtualCraft summary:")
    print("-" * 60)
    summary_rows = []
    for vc_name, vc_path, vc_kind in vc_targets:
        vc_arr = _load_16x16(vc_path)
        if vc_arr is None:
            continue
        vc_pal = _palette_set(vc_arr)
        vc_mask = _alpha_mask(vc_arr)
        scores = [
            0.5 * _palette_jaccard(vc_pal, ref_palettes[r]) +
            0.5 * _silhouette_iou(vc_mask, ref_masks[r])
            for r in ae2_refs
        ]
        max_s = max(scores) if scores else 0.0
        avg_s = sum(scores) / len(scores) if scores else 0.0
        summary_rows.append((vc_kind, vc_name, max_s, avg_s))
        print(f"  {vc_kind}/{vc_name:<35}  best={max_s:.3f}  avg={avg_s:.3f}")

    report_lines.append("## Summary")
    report_lines.append("")
    report_lines.append("| Kind | VirtualCraft asset | Best AE2-CS match score | Avg vs catalog |")
    report_lines.append("|---|---|---:|---:|")
    for kind, name, best, avg in summary_rows:
        report_lines.append(f"| {kind} | `{name}` | {best:.3f} | {avg:.3f} |")
    report_lines.append("")

    # Interpretation
    report_lines.append("### How to read these scores")
    report_lines.append("")
    report_lines.append("- **Combined ≥ 0.50** — strong AE2-CS visual identity (top decile of the catalog).")
    report_lines.append("- **0.35 – 0.50** — recognizable AE2-CS style, color palette agrees but silhouette differs.")
    report_lines.append("- **0.20 – 0.35** — only mildly AE2-CS adjacent; consider redesigning the silhouette.")
    report_lines.append("- **< 0.20** — visually unrelated to any AE2-CS asset.")
    report_lines.append("")
    report_lines.append("Note: a 100% match is not the goal (would mean copying the asset). "
                        "A score in the 0.35–0.55 band typically reads as 'a sibling of an AE2-CS asset'.")

    PREVIEWS_DIR.mkdir(parents=True, exist_ok=True)
    out_path = PREVIEWS_DIR / "asset_audit_report.md"
    out_path.write_text("\n".join(report_lines), encoding="utf-8")
    print()
    print(f"Wrote markdown report → {out_path}")
    return 0


# ============================================================
# Subcommand: extract_palette  (AE2-CS color extraction)
# ============================================================
def _rgb_to_hsv(r: int, g: int, b: int) -> tuple[float, float, float]:
    """Standard RGB→HSV (0..1, 0..1, 0..1). Used for hue-bucketing."""
    r_, g_, b_ = r / 255.0, g / 255.0, b / 255.0
    cmax = max(r_, g_, b_)
    cmin = min(r_, g_, b_)
    delta = cmax - cmin
    # Hue
    if delta == 0:
        h = 0.0
    elif cmax == r_:
        h = ((g_ - b_) / delta) % 6
    elif cmax == g_:
        h = (b_ - r_) / delta + 2
    else:
        h = (r_ - g_) / delta + 4
    h /= 6.0
    # Saturation
    s = 0.0 if cmax == 0 else delta / cmax
    # Value
    v = cmax
    return h, s, v


def _bucket_hue(rgb: int) -> str:
    """Categorize an opaque RGB color into a named hue bucket."""
    r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
    h, s, v = _rgb_to_hsv(r, g, b)
    # Achromatic
    if s < 0.10:
        if v < 0.20:
            return "01_black"
        if v < 0.55:
            return "02_dark_gray"
        if v < 0.85:
            return "03_light_gray"
        return "04_white"
    # Hue bands (HSV hue × 360 ranges)
    deg = h * 360
    if deg < 15 or deg >= 345:
        return "05_red"
    if deg < 45:
        return "06_orange"
    if deg < 70:
        return "07_yellow"
    if deg < 165:
        return "08_green"
    if deg < 200:
        return "09_cyan"
    if deg < 255:
        return "10_blue"
    if deg < 285:
        return "11_violet"
    if deg < 320:
        return "12_purple"
    return "13_pink"


def cmd_extract_palette(args) -> int:
    """Extract high-frequency colors from all AE2-CS sprites, hue-bucketed."""
    # Load every PNG under tools/ae2cs/...
    sources: list[Path] = []
    for d in [AE2_ITEM_DIR, AE2_BLOCK_DIR]:
        if d.exists():
            sources.extend(sorted(d.glob("*.png")))
    if not sources:
        print(f"No AE2-CS textures found under {AE2_ITEM_DIR.parent}")
        return 1

    print(f"Scanning {len(sources)} AE2-CS PNGs...")

    # Aggregate counter across all sources
    global_counter: Counter[int] = Counter()
    for path in sources:
        img = np.array(Image.open(path).convert("RGBA"))
        opaque = img[img[..., 3] >= 128]
        if len(opaque) == 0:
            continue
        packed = (opaque[:, 0].astype(np.uint32) << 16) \
                 | (opaque[:, 1].astype(np.uint32) << 8) \
                 | opaque[:, 2].astype(np.uint32)
        global_counter.update(int(v) for v in packed.tolist())

    total_px = sum(global_counter.values())
    print(f"Total opaque pixels: {total_px}, unique colors: {len(global_counter)}")
    print()

    # Group by hue bucket
    buckets: dict[str, list[tuple[int, int]]] = {}
    for rgb, count in global_counter.items():
        bucket = _bucket_hue(rgb)
        buckets.setdefault(bucket, []).append((rgb, count))
    for bucket in buckets:
        buckets[bucket].sort(key=lambda x: x[1], reverse=True)

    # === stdout: top-N overall ===
    print(f"=== Top {args.top} colors overall (across {len(sources)} AE2-CS sprites) ===")
    for rgb, count in global_counter.most_common(args.top):
        r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
        bucket = _bucket_hue(rgb)
        pct = 100.0 * count / total_px
        print(f"  #{rgb:06X}  rgb({r:3d},{g:3d},{b:3d})  {bucket:<16}  {count:6d} px  {pct:5.2f}%")

    # === stdout: top-K per hue bucket ===
    print()
    print(f"=== Top {args.per_bucket} colors per hue bucket ===")
    for bucket in sorted(buckets):
        items = buckets[bucket]
        bucket_total = sum(c for _, c in items)
        bucket_pct = 100.0 * bucket_total / total_px
        print(f"\n-- {bucket}  ({bucket_total} px = {bucket_pct:.2f}% of catalog, {len(items)} unique) --")
        for rgb, count in items[:args.per_bucket]:
            r, g, b = (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF
            pct = 100.0 * count / total_px
            print(f"  #{rgb:06X}  rgb({r:3d},{g:3d},{b:3d})  {count:6d} px  {pct:5.2f}%")

    # === Markdown report + Python snippet ===
    lines = []
    lines.append("# AE2-CS Color Palette Extraction")
    lines.append("")
    lines.append(f"Sampled **{len(sources)} sprites** ({total_px} opaque pixels, "
                 f"{len(global_counter)} unique colors).")
    lines.append("")
    lines.append("## Top colors per hue bucket")
    lines.append("")
    lines.append("| Bucket | Count | %catalog | Top samples |")
    lines.append("|---|---:|---:|---|")
    for bucket in sorted(buckets):
        items = buckets[bucket]
        bucket_total = sum(c for _, c in items)
        pct = 100.0 * bucket_total / total_px
        samples = " ".join(f"`#{rgb:06X}`" for rgb, _ in items[:args.per_bucket])
        lines.append(f"| {bucket} | {bucket_total} | {pct:.1f}% | {samples} |")
    lines.append("")
    lines.append("## Top-N overall")
    lines.append("")
    lines.append("| Rank | Hex | Bucket | Pixels | % |")
    lines.append("|---:|---|---|---:|---:|")
    for i, (rgb, count) in enumerate(global_counter.most_common(args.top), 1):
        bucket = _bucket_hue(rgb)
        pct = 100.0 * count / total_px
        lines.append(f"| {i} | `#{rgb:06X}` | {bucket} | {count} | {pct:.2f}% |")
    lines.append("")

    # Python snippet — paste-ready palette mapping suggestion
    lines.append("## Suggested `Palette` substitution (Python snippet)")
    lines.append("")
    lines.append("Copy these `_c(0xRRGGBB)` values into `tools/python/asset_texture_generator.py`'s "
                 "`Palette` class to retire the made-up colors with AE2-CS-actual ones. "
                 "The mapping below picks 4 luminance steps per hue bucket "
                 "(deep / mid / hi / glint) ranked by frequency.")
    lines.append("")
    lines.append("```python")

    def pick4(items: list[tuple[int, int]]) -> tuple[int, int, int, int] | None:
        """Pick 4 luminance levels (deep, mid, hi, glint) from a hue bucket's top items."""
        if len(items) < 4:
            return None
        # Sort top-12 by HSV value (lightness)
        top = items[:12]
        ranked = sorted(top, key=lambda x: _rgb_to_hsv(
            (x[0] >> 16) & 0xFF, (x[0] >> 8) & 0xFF, x[0] & 0xFF
        )[2])
        # 4 evenly-spaced indices over the lightness range
        n = len(ranked)
        indices = [0, n // 3, 2 * n // 3, n - 1]
        return tuple(ranked[i][0] for i in indices)  # type: ignore[return-value]

    bucket_to_palette = {
        "05_red": ("RED_GEM_DEEP", "RED_GEM", "RED_GEM_HI", "RED_GEM_GLINT"),
        "06_orange": ("BURN_AMBER_DARK", "BURN_AMBER", "FLAME_LIGHT", "FLAME_BRIGHT"),
        "07_yellow": ("GOLD_GEM_DEEP", "GOLD_GEM", "GOLD_GEM_HI", "GOLD_GEM_GLINT"),
        "08_green": ("TEAL_GEM_DEEP", "TEAL_GEM", "TEAL_GEM_HI", "TEAL_GEM_HI"),
        "09_cyan": ("CYAN_GEM_DEEP", "CYAN_GEM", "CYAN_GEM_HI", "CYAN_GEM_GLINT"),
        "10_blue": ("ACCENT_BLUE", "SOUTH_BLUE", "ACCENT_BLUE", "CYAN_GEM_HI"),
        "11_violet": ("VIOLET_GEM_DEEP", "VIOLET_GEM", "VIOLET_GEM_HI", "VIOLET_GEM_GLINT"),
        "12_purple": ("PURPLE_DARK", "PURPLE", "PURPLE_LIGHT", "PURPLE_GLOW"),
    }

    for bucket, names in bucket_to_palette.items():
        items = buckets.get(bucket, [])
        picked = pick4(items)
        if picked is None:
            lines.append(f"# (bucket {bucket} has fewer than 4 colors; skipping)")
            continue
        lines.append(f"# {bucket}  ({len(items)} unique, "
                     f"{sum(c for _, c in items)} px)")
        for name, rgb in zip(names, picked):
            lines.append(f"{name} = _c(0x{rgb:06X})")

    # Achromatic — gray ramp for SILVER_*
    gray_buckets = []
    for b in ("01_black", "02_dark_gray", "03_light_gray", "04_white"):
        if b in buckets and buckets[b]:
            gray_buckets.append(buckets[b][0][0])
    if len(gray_buckets) >= 4:
        lines.append("")
        lines.append("# achromatic ramp (black / dark-gray / light-gray / white)")
        for name, rgb in zip(("SILVER_BLACK", "SILVER_LOW", "SILVER_MID", "SILVER_HI"), gray_buckets):
            lines.append(f"{name} = _c(0x{rgb:06X})")

    lines.append("```")
    lines.append("")

    PREVIEWS_DIR.mkdir(parents=True, exist_ok=True)
    out_path = PREVIEWS_DIR / "ae2cs_palette_extraction.md"
    out_path.write_text("\n".join(lines), encoding="utf-8")
    print()
    print(f"Wrote markdown report → {out_path}")
    return 0


# ============================================================
# CLI dispatch
# ============================================================
def cli(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="analyzer_tools.py",
        description=__doc__.split("\n\n")[0],
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_cs = sub.add_parser("color_sample",
                          help="Histogram top opaque colors + sample fixed AE2 pixel coords")
    p_cs.add_argument("paths", nargs="+", help="One or more PNG paths")

    p_pa = sub.add_parser("pixel_analyze",
                          help="Row/column sweep + nearest/block-average downsample")
    p_pa.add_argument("input", help="Input PNG path (typically tools/mc_pixel_perfect.png)")

    sub.add_parser("audit",
                   help="AE2-CS visual fidelity report for VirtualCraft procedural textures")

    p_ep = sub.add_parser("extract_palette",
                          help="Extract high-frequency AE2-CS colors, hue-bucketed and ranked")
    p_ep.add_argument("--top", type=int, default=64,
                      help="How many top colors to extract overall (default 64)")
    p_ep.add_argument("--per-bucket", type=int, default=8,
                      help="Top colors per hue bucket (default 8)")

    args = parser.parse_args(argv)
    handler = {
        "color_sample": cmd_color_sample,
        "pixel_analyze": cmd_pixel_analyze,
        "audit": cmd_audit,
        "extract_palette": cmd_extract_palette,
    }[args.cmd]
    return handler(args)


if __name__ == "__main__":
    sys.exit(cli())
