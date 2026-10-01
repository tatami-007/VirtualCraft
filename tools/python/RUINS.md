# Monumental boss ruins

Install Pillow (`python -m pip install pillow`). These two tools run independently
of the local-only legacy asset toolchain; they are development tools, not mod code.

```powershell
python tools/python/ruin_materials.py --diff
python tools/python/ruin_materials.py --write
python tools/python/ruin_materials.py --diff
python tools/python/boss_structure_compiler.py
```

The material diff exits 1 when intended changes have not yet been written and 0
when all twelve files match. Writing is limited to the twelve named ruin block
textures. Each is verified as opaque 16x16 RGBA with at most 24 colors. No GUI,
item, altar, entity, or hand-drawn textures are regenerated.

The structure compiler exports the two existing structure identifiers, preserving
their altar and loot/spawner marker names. It checks flat-floor reachability to
every chest and altar, entrance steps, and an unobstructed 15-block-tall combat
volume (radius 18 snake; radius 28 mushroom). Java Kind dimensions must remain
160x160x72 and 176x176x96 (width/depth/height), respectively.

Each ruin has two elevated galleries checked by a three-dimensional walkability
flood fill (one-block steps, two-block headroom). Low-level cutaway previews show
these spaces with blocks at height 24 and above removed for visibility.

Natural siting samples 25 ground heights across the footprint, rejects relief
over 18 blocks and excessive cuts/fills, and uses the median floor height. The
foundation extends down at most 24 blocks within the currently generated chunk.
Both structure sets use spacing 48 / separation 24 chunks; these are candidate
placement parameters, not a promise of a ruin every 768 blocks. Biomes and
terrain eligibility further reduce density.

Previews appear in `tools/previews/boss_structures/`. The materials sheet shows
actual pixels and 3x3 repeats. Architectural views use texture-average colors,
not Minecraft rendering or lighting. They do not validate terrain integration,
chunk placement latency, existing-world compatibility, or actual boss combat.

This development project already has a terrain-version migration hook. Increment
VirtualWorldTerrainMigration.TERRAIN_VERSION for every map/structure update.
On the next world open, it moves the virtual_world dimension to the save-local
virtual_craft_dimension_backups directory, then writes the new version marker.
Existing placed virtual-world buildings reset along with terrain. No repeated
reset occurs at the same version. Overworld, Nether, End and player files are
not moved. Test in a disposable world before using an important save.
