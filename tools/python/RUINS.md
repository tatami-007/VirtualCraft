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

Previews appear in `tools/previews/boss_structures/`. The materials sheet shows
actual pixels and 3x3 repeats. Architectural views use texture-average colors,
not Minecraft rendering or lighting. They do not validate terrain integration,
chunk placement latency, existing-world compatibility, or actual boss combat.

Existing placed buildings are not rewritten. Test natural generation in new
chunks or a disposable world before using the new assets in an important save.
