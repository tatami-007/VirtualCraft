# VirtualCraft × AE2-CS Visual Fidelity Audit

Compared **7 VirtualCraft procedural textures** against **89 AE2-CS 16×16 references**.

Similarity score = `0.5 × palette_jaccard + 0.5 × silhouette_iou`. Higher is closer to AE2-CS visual identity.

## `item/hash_voucher`

- **Geometry**: card (rectangular, near-full bbox)
- **Unique opaque colors**: 11
- **Top-5 colors**: `#DEDFE3` (33px), `#ADB0C4` (31px), `#413F54` (29px), `#CBCCD4` (26px), `#4D4D67` (19px)

**Top-5 closest AE2-CS references** (similarity / palette / silhouette IoU):

| Rank | AE2-CS reference | Combined | Palette Jaccard | Silhouette IoU |
|---:|---|---:|---:|---:|
| 1 | `item/blank_print_press` | **0.909** | 0.818 | 1.000 |
| 2 | `item/resonating_print_press` | **0.899** | 0.818 | 0.979 |
| 3 | `item/ender_blank_print_press` | **0.781** | 0.562 | 1.000 |
| 4 | `item/resonating_pattern` | **0.645** | 0.643 | 0.646 |
| 5 | `item/resonating_pattern_converter` | **0.623** | 0.600 | 0.646 |

## `item/mining_surveyor`

- **Geometry**: block (full-bleed 16×16, e.g. cube_all)
- **Unique opaque colors**: 11
- **Top-5 colors**: `#212222` (88px), `#7F7F7F` (76px), `#515151` (27px), `#CBCCD4` (24px), `#284654` (20px)

**Top-5 closest AE2-CS references** (similarity / palette / silhouette IoU):

| Rank | AE2-CS reference | Combined | Palette Jaccard | Silhouette IoU |
|---:|---|---:|---:|---:|
| 1 | `block/certus_quartz_ore` | **0.562** | 0.125 | 1.000 |
| 2 | `block/deepslate_certus_quartz_ore` | **0.560** | 0.120 | 1.000 |
| 3 | `block/redstone_crystal_block` | **0.556** | 0.111 | 1.000 |
| 4 | `block/resonating_crystal_block` | **0.526** | 0.053 | 1.000 |
| 5 | `block/ender_quartz_block` | **0.500** | 0.000 | 1.000 |

## `item/speed_upgrade_card`

- **Geometry**: compound (irregular)
- **Unique opaque colors**: 7
- **Top-5 colors**: `#440F5E` (29px), `#642484` (28px), `#1A0326` (24px), `#370A4D` (20px), `#543F40` (18px)

**Top-5 closest AE2-CS references** (similarity / palette / silhouette IoU):

| Rank | AE2-CS reference | Combined | Palette Jaccard | Silhouette IoU |
|---:|---|---:|---:|---:|
| 1 | `item/ender_interface_upgrade` | **0.500** | 0.000 | 1.000 |
| 2 | `item/extended_ender_interface_upgrade` | **0.500** | 0.000 | 1.000 |
| 3 | `item/extended_integrated_interface_upgrade` | **0.500** | 0.000 | 1.000 |
| 4 | `item/extended_resonating_pattern_provider_upgrade` | **0.500** | 0.000 | 1.000 |
| 5 | `item/integrated_interface_upgrade` | **0.500** | 0.000 | 1.000 |

## `item/link_card`

- **Geometry**: card (rectangular, near-full bbox)
- **Unique opaque colors**: 5
- **Top-5 colors**: `#212222` (78px), `#8F8F8F` (77px), `#B66FFF` (3px), `#C096FF` (2px), `#F2F2F2` (1px)

**Top-5 closest AE2-CS references** (similarity / palette / silhouette IoU):

| Rank | AE2-CS reference | Combined | Palette Jaccard | Silhouette IoU |
|---:|---|---:|---:|---:|
| 1 | `item/extended_resonating_pattern_provider_upgrade` | **0.490** | 0.100 | 0.880 |
| 2 | `item/meteor_pattern_provider_upgrade` | **0.490** | 0.100 | 0.880 |
| 3 | `item/extended_ender_interface_upgrade` | **0.485** | 0.091 | 0.880 |
| 4 | `item/extended_integrated_interface_upgrade` | **0.475** | 0.071 | 0.880 |
| 5 | `item/blank_print_press` | **0.458** | 0.077 | 0.839 |

## `block/electric_ball_generator`

- **Geometry**: block (full-bleed 16×16, e.g. cube_all)
- **Unique opaque colors**: 8
- **Top-5 colors**: `#212222` (88px), `#AA212B` (85px), `#84122A` (48px), `#FF8585` (25px), `#FFCBBC` (5px)

**Top-5 closest AE2-CS references** (similarity / palette / silhouette IoU):

| Rank | AE2-CS reference | Combined | Palette Jaccard | Silhouette IoU |
|---:|---|---:|---:|---:|
| 1 | `block/redstone_crystal_block` | **0.607** | 0.214 | 1.000 |
| 2 | `block/rose_quartz_block` | **0.531** | 0.062 | 1.000 |
| 3 | `block/certus_quartz_ore` | **0.500** | 0.000 | 1.000 |
| 4 | `block/deepslate_certus_quartz_ore` | **0.500** | 0.000 | 1.000 |
| 5 | `block/ender_quartz_block` | **0.500** | 0.000 | 1.000 |

## `block/holographic_shop`

- **Geometry**: block (full-bleed 16×16, e.g. cube_all)
- **Unique opaque colors**: 7
- **Top-5 colors**: `#212222` (88px), `#258474` (76px), `#91E3FF` (40px), `#105E51` (39px), `#FFC107` (9px)

**Top-5 closest AE2-CS references** (similarity / palette / silhouette IoU):

| Rank | AE2-CS reference | Combined | Palette Jaccard | Silhouette IoU |
|---:|---|---:|---:|---:|
| 1 | `block/ender_quartz_block` | **0.571** | 0.143 | 1.000 |
| 2 | `block/certus_quartz_ore` | **0.500** | 0.000 | 1.000 |
| 3 | `block/deepslate_certus_quartz_ore` | **0.500** | 0.000 | 1.000 |
| 4 | `block/irradiated_crystal_block` | **0.500** | 0.000 | 1.000 |
| 5 | `block/meteor_crystal_block` | **0.500** | 0.000 | 1.000 |

## `block/pow_miner`

- **Geometry**: block (full-bleed 16×16, e.g. cube_all)
- **Unique opaque colors**: 10
- **Top-5 colors**: `#212222` (88px), `#7F7F7F` (74px), `#515151` (27px), `#CBCCD4` (24px), `#B66FFF` (20px)

**Top-5 closest AE2-CS references** (similarity / palette / silhouette IoU):

| Rank | AE2-CS reference | Combined | Palette Jaccard | Silhouette IoU |
|---:|---|---:|---:|---:|
| 1 | `block/redstone_crystal_block` | **0.559** | 0.118 | 1.000 |
| 2 | `block/certus_quartz_ore` | **0.520** | 0.040 | 1.000 |
| 3 | `block/deepslate_certus_quartz_ore` | **0.519** | 0.038 | 1.000 |
| 4 | `block/ender_quartz_block` | **0.500** | 0.000 | 1.000 |
| 5 | `block/irradiated_crystal_block` | **0.500** | 0.000 | 1.000 |

---

## AE2-CS catalog geometry breakdown

| Geometry mode | Count | % |
|---|---:|---:|
| compound (irregular) | 51 | 57.3% |
| crystal/orb (compact, irregular silhouette) | 25 | 28.1% |
| block (full-bleed 16×16, e.g. cube_all) | 9 | 10.1% |
| card (rectangular, near-full bbox) | 4 | 4.5% |

## Summary

| Kind | VirtualCraft asset | Best AE2-CS match score | Avg vs catalog |
|---|---|---:|---:|
| item | `hash_voucher` | 0.909 | 0.261 |
| item | `mining_surveyor` | 0.562 | 0.225 |
| item | `speed_upgrade_card` | 0.500 | 0.254 |
| item | `link_card` | 0.490 | 0.257 |
| block | `electric_ball_generator` | 0.607 | 0.221 |
| block | `holographic_shop` | 0.571 | 0.224 |
| block | `pow_miner` | 0.559 | 0.220 |

### How to read these scores

- **Combined ≥ 0.50** — strong AE2-CS visual identity (top decile of the catalog).
- **0.35 – 0.50** — recognizable AE2-CS style, color palette agrees but silhouette differs.
- **0.20 – 0.35** — only mildly AE2-CS adjacent; consider redesigning the silhouette.
- **< 0.20** — visually unrelated to any AE2-CS asset.

Note: a 100% match is not the goal (would mean copying the asset). A score in the 0.35–0.55 band typically reads as 'a sibling of an AE2-CS asset'.