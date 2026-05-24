# AE2-CS Color Palette Extraction

Sampled **101 sprites** (20983 opaque pixels, 235 unique colors).

## Top colors per hue bucket

| Bucket | Count | %catalog | Top samples |
|---|---:|---:|---|
| 01_black | 599 | 2.9% | `#212222` `#232626` `#2D2F2D` `#292B29` `#1F2221` `#161717` |
| 02_dark_gray | 4187 | 20.0% | `#7F7F7F` `#515151` `#3D3D43` `#747474` `#646464` `#676767` |
| 03_light_gray | 1141 | 5.4% | `#8F8F8F` `#CBCCD4` `#97A2A2` `#A3ADAD` `#A5A7AC` `#848E8E` |
| 04_white | 1073 | 5.1% | `#FFFFFF` `#FEFFFF` `#E8FFFC` `#DEDFE3` `#F2F2F2` `#E8E8EA` |
| 05_red | 611 | 2.9% | `#AA212B` `#D73E42` `#FF8585` `#84122A` `#715F5C` `#A83816` |
| 06_orange | 1123 | 5.4% | `#FFBFA4` `#B76E22` `#CE9835` `#493615` `#CEB79B` `#A05911` |
| 07_yellow | 134 | 0.6% | `#E8C64B` `#F1D13E` `#F3E668` `#FAF599` `#F4FFB5` `#D4CFBE` |
| 08_green | 938 | 4.5% | `#182112` `#2E411E` `#5B873B` `#517335` `#323835` `#364C23` |
| 09_cyan | 3227 | 15.4% | `#ACE9FF` `#DAFFFF` `#105E51` `#284654` `#258474` `#91E3FF` |
| 10_blue | 6441 | 30.7% | `#1B1541` `#2F2F37` `#6A9ECE` `#1E1655` `#8ABBEF` `#9CD3FF` |
| 11_violet | 864 | 4.1% | `#B66FFF` `#E8E0FF` `#593485` `#E087FF` `#704091` `#C096FF` |
| 12_purple | 169 | 0.8% | `#140012` `#FF9FFF` `#FF80D7` `#884472` `#FFC4FF` `#210019` |
| 13_pink | 476 | 2.3% | `#400029` `#C83264` `#4F002C` `#913660` `#741A41` `#F74572` |

## Top-N overall

| Rank | Hex | Bucket | Pixels | % |
|---:|---|---|---:|---:|
| 1 | `#7F7F7F` | 02_dark_gray | 903 | 4.30% |
| 2 | `#515151` | 02_dark_gray | 682 | 3.25% |
| 3 | `#3D3D43` | 02_dark_gray | 630 | 3.00% |
| 4 | `#747474` | 02_dark_gray | 563 | 2.68% |
| 5 | `#1B1541` | 10_blue | 508 | 2.42% |
| 6 | `#2F2F37` | 10_blue | 459 | 2.19% |
| 7 | `#646464` | 02_dark_gray | 444 | 2.12% |
| 8 | `#676767` | 02_dark_gray | 442 | 2.11% |
| 9 | `#ACE9FF` | 09_cyan | 432 | 2.06% |
| 10 | `#8F8F8F` | 03_light_gray | 409 | 1.95% |
| 11 | `#6A9ECE` | 10_blue | 401 | 1.91% |
| 12 | `#DAFFFF` | 09_cyan | 338 | 1.61% |
| 13 | `#1E1655` | 10_blue | 333 | 1.59% |
| 14 | `#8ABBEF` | 10_blue | 326 | 1.55% |
| 15 | `#9CD3FF` | 10_blue | 320 | 1.53% |
| 16 | `#5C86AF` | 10_blue | 308 | 1.47% |
| 17 | `#242A62` | 10_blue | 304 | 1.45% |
| 18 | `#456278` | 10_blue | 266 | 1.27% |
| 19 | `#FFFFFF` | 04_white | 264 | 1.26% |
| 20 | `#797979` | 02_dark_gray | 260 | 1.24% |
| 21 | `#413F54` | 10_blue | 239 | 1.14% |
| 22 | `#7362D3` | 10_blue | 239 | 1.14% |
| 23 | `#688DA8` | 10_blue | 226 | 1.08% |
| 24 | `#FEFFFF` | 04_white | 214 | 1.02% |
| 25 | `#352D68` | 10_blue | 214 | 1.02% |
| 26 | `#105E51` | 09_cyan | 201 | 0.96% |
| 27 | `#4D4D67` | 10_blue | 187 | 0.89% |
| 28 | `#B66FFF` | 11_violet | 185 | 0.88% |
| 29 | `#284654` | 09_cyan | 183 | 0.87% |
| 30 | `#258474` | 09_cyan | 181 | 0.86% |
| 31 | `#E8FFFC` | 04_white | 174 | 0.83% |
| 32 | `#91E3FF` | 09_cyan | 174 | 0.83% |

## Suggested `Palette` substitution (Python snippet)

Copy these `_c(0xRRGGBB)` values into `tools/python/asset_texture_generator.py`'s `Palette` class to retire the made-up colors with AE2-CS-actual ones. The mapping below picks 4 luminance steps per hue bucket (deep / mid / hi / glint) ranked by frequency.

```python
# 05_red  (12 unique, 611 px)
RED_GEM_DEEP = _c(0x6F5D5A)
RED_GEM = _c(0xA83816)
RED_GEM_HI = _c(0xF07665)
RED_GEM_GLINT = _c(0xFFCBBC)
# 06_orange  (43 unique, 1123 px)
BURN_AMBER_DARK = _c(0x281E0B)
BURN_AMBER = _c(0xA05911)
FLAME_LIGHT = _c(0xE4DACA)
FLAME_BRIGHT = _c(0xFFEBD1)
# 07_yellow  (6 unique, 134 px)
GOLD_GEM_DEEP = _c(0xD4CFBE)
GOLD_GEM = _c(0xF1D13E)
GOLD_GEM_HI = _c(0xFAF599)
GOLD_GEM_GLINT = _c(0xF4FFB5)
# 08_green  (20 unique, 938 px)
TEAL_GEM_DEEP = _c(0x182112)
TEAL_GEM = _c(0x3A423A)
TEAL_GEM_HI = _c(0x575E54)
TEAL_GEM_HI = _c(0x6A9944)
# 09_cyan  (33 unique, 3227 px)
CYAN_GEM_DEEP = _c(0x0C3730)
CYAN_GEM = _c(0x284654)
CYAN_GEM_HI = _c(0xACE9FF)
CYAN_GEM_GLINT = _c(0xBBFFFA)
# 10_blue  (51 unique, 6441 px)
ACCENT_BLUE = _c(0x2F2F37)
SOUTH_BLUE = _c(0x242A62)
ACCENT_BLUE = _c(0x6A9ECE)
CYAN_GEM_HI = _c(0x9CD3FF)
# 11_violet  (10 unique, 864 px)
VIOLET_GEM_DEEP = _c(0x4D326D)
VIOLET_GEM = _c(0x704091)
VIOLET_GEM_HI = _c(0xB66FFF)
VIOLET_GEM_GLINT = _c(0xC096FF)
# 12_purple  (6 unique, 169 px)
PURPLE_DARK = _c(0x140012)
PURPLE = _c(0x884472)
PURPLE_LIGHT = _c(0xFF80D7)
PURPLE_GLOW = _c(0xFFC4FF)

# achromatic ramp (black / dark-gray / light-gray / white)
SILVER_BLACK = _c(0x212222)
SILVER_LOW = _c(0x7F7F7F)
SILVER_MID = _c(0x8F8F8F)
SILVER_HI = _c(0xFFFFFF)
```
