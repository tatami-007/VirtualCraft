# AGENTS.md

Operating guide for AI coding agents working on **VirtualCraft** — a Minecraft 1.21.1 NeoForge mod that adds an in-game crypto-mining / energy-economy gameplay layer (wallets, exchanges, holographic shop terminals, electric machines).

This file documents the project's structure, build commands, and **the non-obvious constraints that an agent must respect**. Read this before making any non-trivial change.

---

## Project at a glance

| Field | Value |
|---|---|
| Mod ID | `virtual_craft` |
| Group / package | `dev.tatami.virtualcraft` |
| Minecraft | 1.21.1 |
| Mod loader | NeoForge 21.1.176 |
| Java | 21 (toolchain auto-provisioned by Gradle) |
| UI framework | [ModernUI](https://maven.izzel.io/) `icyllis.modernui:ModernUI-NeoForge:1.21.1-3.12.0.2` |
| Entry point | `dev.tatami.virtualcraft.VirtualCraft` (see `@Mod` annotation) |
| Build system | Gradle wrapper (`gradlew` / `gradlew.bat`) with `net.neoforged.moddev` plugin |

---

## Build & run

```powershell
# Build the mod jar (output: build/libs/virtual_craft-0.1.jar)
.\gradlew build

# Run a dev client with the mod loaded
.\gradlew runClient

# Run a dev server
.\gradlew runServer

# Run unit tests (JUnit 5)
.\gradlew test
```

**Don't run `gradle` (system) — always use the wrapper.** The wrapper pins the correct Gradle version and Java toolchain.

`run/` is the in-place dev game directory and is gitignored — its `saves/`, `mods/`, `logs/` are all local-only.

---

## Repository layout

```
VirtualCraft-1.21.1/
├── AGENTS.md                  ← you are reading this
├── build.gradle               ← Gradle build script (NeoForge moddev plugin)
├── gradle.properties          ← JVM args + parallel/caching flags
├── settings.gradle
├── gradlew / gradlew.bat      ← Gradle wrapper — always use this
├── src/
│   ├── main/
│   │   ├── java/dev/tatami/virtualcraft/   ← all mod source
│   │   └── resources/                       ← assets / data / META-INF
│   └── test/                  ← JUnit 5 tests
├── tools/                     ← dev tooling (NOT shipped with the mod)
│   ├── README_python.md       ← Python tools entry point
│   ├── python/                ← active Python helpers (texture gen / verify / preview)
│   ├── java_legacy/           ← original Java tool sources (kept for reference)
│   ├── previews/              ← preview PNG outputs from preview_tools.py
│   ├── refs/                  ← AE2-CS reference textures (read-only inputs)
│   └── ae2cs/                 ← AE2-CS extracted assets (read-only inputs)
└── run/                       ← dev game directory (gitignored)
```

### `src/main/java/dev/tatami/virtualcraft/` package map

| Package | Purpose |
|---|---|
| `VirtualCraft.java` | Mod entry — `@Mod` class, payload registrations, server-tick loop |
| `api/` | Public extension points (re-export friendly types only) |
| `block/` | Block classes (machines, ores, decorative) |
| `blockentity/` | BlockEntity classes — tick logic, container, sync data |
| `client/` | Client-only renderers, screens, ModernUI fragments |
| `client/screen/` | Screen subclasses (PowMinerScreen, ElectricBallGeneratorScreen, ElectricCondenserScreen, holographic/*) |
| `client/screen/widget/` | Reusable widgets (IconButton, SideFaceButton, MachineUi helpers) |
| `currency/` | Currency model (BTC/ETH/SOL/etc) |
| `energy/` | Wireless energy network |
| `exchange/` | Real-time exchange rate service (network sync) |
| `gameplay/` | Cross-cutting gameplay state (HolographicTerminalSession, etc.) |
| `item/` | Item classes (UpgradeCard, LinkCard, MiningSurveyor, etc.) |
| `machine/` | Machine-specific logic shared between block and BE |
| `menu/` | Menu (container) classes — `*Menu` |
| `mining/` | Mining surveyor / vein detection logic |
| `network/` | Custom payloads — every network message lives here, registered in `VirtualCraft.java` |
| `registry/` | DeferredRegister holders — `ModBlocks`, `ModItems`, `ModBlockEntities`, `ModMenuTypes`, `ModAttachments`, `ModCapabilities`, `ModCreativeTabs` |
| `shop/` | Holographic-terminal shop catalog |
| `util/` | Pure-utility helpers (no mod-state coupling) |
| `wallet/` | Player wallet attachment + ledger paging |
| `wireless/` | (placeholder for future wireless features) |

---

## Conventions

### Java / NeoForge

- **DeferredRegister registration**: All blocks / items / menus / BE types live in a `Mod*` class under `registry/`. Never call registry methods outside those classes — they're invoked from `VirtualCraft`'s constructor in a defined order.
- **Network payloads**: One file per payload under `network/`, all `record`s implementing `CustomPacketPayload`. Register them in `VirtualCraft.registerPayloadHandlers(...)`.
- **Side discipline**: Client-only code goes under `client/`. **Never** import `client/*` from non-client packages, and never reference `Minecraft.getInstance()` outside `client/`. The mod must server-load cleanly.
- **Logging**: Use the existing `LogManager.getLogger()` in `VirtualCraft.java` for cross-cutting info; per-class loggers are fine for high-volume areas.
- **Encoding**: All Java sources are UTF-8 (enforced in `build.gradle`).

### Resources

- Texture path convention: `src/main/resources/assets/virtual_craft/textures/{gui,item,block}/<name>.png`
- Many `gui/*.png` and some `item/*.png` and `block/*.png` are **procedurally generated** by the Python tools — see *Texture Generators* below.
- Hand-drawn textures (`electric_ingot.png`, `electric_absorber*.png`, `electric_ball.png`, `wireless_power_tower.png` 32×32) **must not be overwritten** by the procedural generators. The generator code knows about these and skips them; if you add a new hand-drawn texture, add it to `LEGACY_GENERATORS` in `tools/python/asset_texture_generator.py`.

### ModernUI screens

- Screens that use ModernUI are under `client/screen/holographic/` (see `HolographicShopFragment`, `HolographicTheme`).
- Use `HolographicTheme` constants for any new UI surface — don't hardcode hex colors.
- Tooltip surfaces: prefer the Win11-mica drawable already defined in `HolographicTheme` (created in v20.x).

---

## Texture generators (`tools/python/`)

The mod's GUI / item / block textures are generated by Python scripts that mirror an AE2-CS aesthetic. **Five Python files** replaced **13 Java tools**; the Java sources live in `tools/java_legacy/` for reference.

| Tool | Purpose |
|---|---|
| `gui_texture_generator.py` | Generates 5 GUI atlas PNGs (`pow_miner.png`, `electric_ball_generator.png`, `electric_condenser.png`, `side_config_menu.png`, `icons.png`) |
| `asset_texture_generator.py` | Generates 4 item PNGs + 3 cube_all block PNGs |
| `preview_tools.py` | Visual preview / annotation helpers (sub-commands: `reference`, `assets`, `arrow_sprite`, `cells`, `on_bg`, `sprite_v_fill`, `annotated`) |
| `analyzer_tools.py` | Color histogram + pixel sampling + downsample analysis |
| `verify_tools.py` | Regression tests (sub-commands: `icons`, `vfill`) — **CI-friendly** (exit 1 on any failure) |
| `notebook_playground.ipynb` | Jupyter playground with `%autoreload 2` for live pixel debugging |

**Setup** (one-time):

```powershell
python -m pip install pillow numpy
```

**Workflow when changing a texture**:

1. Edit a `Palette` constant or a `Machines.xxx()` / `Items.xxx()` / `Blocks.xxx()` body.
2. Run the relevant generator with `--diff` to confirm what would change:
   ```powershell
   python tools\python\gui_texture_generator.py --diff
   python tools\python\asset_texture_generator.py --diff
   ```
3. Run without `--diff` to write the new PNG to disk.
4. Run `verify_tools` to ensure regressions in invariant areas (border colors, retired cells):
   ```powershell
   python tools\python\verify_tools.py icons
   python tools\python\verify_tools.py vfill --quiet
   ```

See `tools/README_python.md` for the full reference (commands, gotchas, parity table with Java).

---

## DOs and DON'Ts for agents

### DO

- **Always run `gradlew` (not system gradle).** Java toolchain is auto-fetched.
- **Run `--diff` after editing any texture generator** to confirm the resulting PNG diff is what you intended.
- **Mirror coordinate edits in the matching Screen + Menu pair** — slot positions in `XxxMenu.java` and the visual draw region in `XxxScreen.java` must agree, or items render in the wrong place.
- **Use the existing `Palette`, `Widgets`, `Sprites`, `Icons` classes in `gui_texture_generator.py`** when adding new GUI elements. Don't introduce parallel color tables.
- **Keep tools out of the mod jar.** `tools/` is dev-only; never reference it from `src/main/`.

### DON'T

- **Don't move `tools/python/*.py` into `tools/`** without also fixing every `Path(__file__).resolve().parents[2]` ROOT calculation. The current layout assumes `tools/python/<file>.py` (depth 2 from workspace root).
- **Don't run the Java tools** (`tools/java_legacy/*.java`) — at least two of them have stale baselines:
  - `VerifyIcons.java` still expects a `LINK` icon retired in v20.7
  - `VerifyVFill.java` still expects `#373737` / `#FFFFFF` borders that v20.7 unified to `#F2F2F2`
  - `AssetTextureGenerator.java`'s `main()` still tries to write `wireless_transfer_upgrade_card.png` (the item was removed from the project) and `wireless_power_tower.png` (a 32×32 hand-drawn version supersedes the procedural 16×16 — Java would silently overwrite it)

  The Python ports correct these; the Java sources are kept only for code-archaeology / rollback. If you need them, fix the baselines first.
- **Don't edit `tools/refs/*` or `tools/ae2cs/*`** — these are read-only AE2-CS reference assets used by the generators.
- **Don't commit `tools/previews/*.png` regenerations as part of unrelated changes** — they're outputs and noise up the diff. Only commit them when the underlying generator changed.
- **Don't add Python dependencies beyond `pillow + numpy`** without updating `tools/README_python.md`. Keep the toolchain trivial to set up.
- **Don't import `Minecraft`, `Screen`, ModernUI types, or other client-only classes from non-client packages.** This breaks dedicated server loading.

---

## Common tasks — recipes

### Adding a new machine

1. **Block + BlockEntity**: add classes under `block/` and `blockentity/`, register in `ModBlocks` / `ModBlockEntities`.
2. **Menu**: add `XxxMenu` under `menu/`, register in `ModMenuTypes`. Slots positions must match the GUI texture's panel coordinates.
3. **Screen**: add `XxxScreen` under `client/screen/`, mirror the menu's slot positions for visual alignment.
4. **GUI texture**: add a `Machines.xxx()` static method in `tools/python/gui_texture_generator.py` and an entry to `GENERATORS` dict. Run `python tools\python\gui_texture_generator.py --only xxx` then `--diff`.
5. **Block texture**: add a `Blocks.xxx()` method in `tools/python/asset_texture_generator.py` (under "Blocks" class) and add to `GENERATORS`.
6. **Network sync** (if the BE has client-visible state): add a `SyncXxxPayload` under `network/`, register in `VirtualCraft.registerPayloadHandlers`.

### Tweaking an existing texture color

1. Open `tools/python/gui_texture_generator.py` (or `asset_texture_generator.py`).
2. Either:
   - Edit a `Palette.XXX = _c(0xRRGGBB)` constant (affects every place using that color), OR
   - Open `tools/python/notebook_playground.ipynb`, override the `Palette` field locally, see the diff with `diff_overlay()` before committing.
3. Run `--diff` to preview, then save with no flag.

### Verifying nothing broke before pushing

```powershell
python tools\python\gui_texture_generator.py --diff
python tools\python\asset_texture_generator.py --diff
python tools\python\verify_tools.py icons
python tools\python\verify_tools.py vfill --quiet
.\gradlew build
.\gradlew test
```

All five should exit 0.

---

## Out-of-band notes

- `tools/neoforge.zip` is a manually downloaded NeoForge SDK cache — leave it alone.
- The mod uses `gradle.configuration-cache=true`. If you change `build.gradle`, the next build will re-cache automatically.
- `.cascade-tmp/`, `.tmp/`, `*.log`, `*.tmp` are gitignored at the repo root for AI-tool scratch space.
- Active conversation summaries / progress notes (if you keep them) belong in `.cascade-tmp/` or as private workspace files — not in the repo.
