"""Compile VirtualCraft boss dungeons into vanilla structure NBT files.

This is an offline authoring tool. It deliberately avoids editing a running
Minecraft world: architecture is described as reusable voxel primitives,
compiled to structure templates, and rendered to deterministic previews.
"""

from __future__ import annotations

import argparse
import gzip
import math
import struct
from collections import deque
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw, ImageStat


ROOT = Path(__file__).resolve().parents[2]
STRUCTURE_DIR = ROOT / "src/main/resources/data/virtual_craft/structure/boss"
PREVIEW_DIR = ROOT / "tools/previews/boss_structures"
DATA_VERSION = 3955

AIR = "minecraft:air"
SNAKE_FOUNDATION = "virtual_craft:serpent_stone"
SNAKE_BRICK = "virtual_craft:serpent_stone_bricks"
SNAKE_CHISELED = "virtual_craft:chiseled_serpent_stone_bricks"
SNAKE_FLOOR = "virtual_craft:serpent_floor_tiles"
SNAKE_LATTICE = "virtual_craft:serpent_lattice"
SNAKE_LIGHT = "virtual_craft:venom_crystal"
FUNGAL_FOUNDATION = "virtual_craft:fungal_stone"
FUNGAL_BRICK = "virtual_craft:fungal_stone_bricks"
FUNGAL_CHISELED = "virtual_craft:chiseled_fungal_stone_bricks"
FUNGAL_CAP = "virtual_craft:fungal_cap_tiles"
FUNGAL_GILL = "virtual_craft:fungal_gill_tiles"
FUNGAL_LIGHT = "virtual_craft:spore_lamp"


@dataclass(frozen=True)
class Marker:
    pos: tuple[int, int, int]
    metadata: str


class Structure:
    def __init__(self, name: str, size: tuple[int, int, int]) -> None:
        self.name = name
        self.width, self.height, self.depth = size
        self.blocks: dict[tuple[int, int, int], str] = {}
        self.markers: list[Marker] = []
        self.interior_cells: set[tuple[int, int, int]] = set()

    def set(self, x: int, y: int, z: int, block: str) -> None:
        if 0 <= x < self.width and 0 <= y < self.height and 0 <= z < self.depth:
            self.blocks[(x, y, z)] = block

    def fill(self, a: tuple[int, int, int], b: tuple[int, int, int], block: str) -> None:
        x0, y0, z0 = (min(a[i], b[i]) for i in range(3))
        x1, y1, z1 = (max(a[i], b[i]) for i in range(3))
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                for x in range(x0, x1 + 1):
                    self.set(x, y, z, block)

    def clear(self, a: tuple[int, int, int], b: tuple[int, int, int]) -> None:
        self.fill(a, b, AIR)

    def clear_unset(self, a: tuple[int, int, int], b: tuple[int, int, int]) -> None:
        x0, y0, z0 = (min(a[i], b[i]) for i in range(3))
        x1, y1, z1 = (max(a[i], b[i]) for i in range(3))
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                for x in range(x0, x1 + 1):
                    if (x, y, z) not in self.blocks:
                        self.set(x, y, z, AIR)

    def floor(self, x0: int, z0: int, x1: int, z1: int, y: int, block: str) -> None:
        self.fill((x0, y, z0), (x1, y, z1), block)

    def walls(
        self, x0: int, z0: int, x1: int, z1: int, y0: int, y1: int, block: str, thickness: int = 1
    ) -> None:
        self.fill((x0, y0, z0), (x0 + thickness - 1, y1, z1), block)
        self.fill((x1 - thickness + 1, y0, z0), (x1, y1, z1), block)
        self.fill((x0, y0, z0), (x1, y1, z0 + thickness - 1), block)
        self.fill((x0, y0, z1 - thickness + 1), (x1, y1, z1), block)

    def room(
        self,
        x0: int,
        z0: int,
        x1: int,
        z1: int,
        floor_y: int,
        wall_top: int,
        floor_block: str,
        wall_block: str,
        trim_block: str,
        thickness: int = 2,
        support_base: bool = True,
    ) -> None:
        # Keep foundations under the authored footprint instead of stamping a
        # giant rectangular platform across the surrounding terrain.
        if support_base:
            for base_y, base_block in (
                (floor_y - 3, wall_block),
                (floor_y - 2, wall_block),
                (floor_y - 1, trim_block),
            ):
                self.floor(x0, z0, x1, z1, base_y, base_block)
        self.floor(x0, z0, x1, z1, floor_y, floor_block)
        self.clear_unset((x0 + thickness, floor_y + 1, z0 + thickness),
                         (x1 - thickness, wall_top, z1 - thickness))
        for x in range(x0 + thickness, x1 - thickness + 1):
            for z in range(z0 + thickness, z1 - thickness + 1):
                self.interior_cells.add((x, floor_y + 1, z))
        self.walls(x0, z0, x1, z1, floor_y + 1, wall_top, wall_block, thickness)
        self.fill((x0, floor_y + 4, z0), (x0 + thickness - 1, floor_y + 5, z1), trim_block)
        self.fill((x1 - thickness + 1, floor_y + 4, z0), (x1, floor_y + 5, z1), trim_block)

    def doorway_z(self, x0: int, x1: int, z: int, floor_y: int, height: int, depth: int = 3) -> None:
        self.clear((x0, floor_y + 1, z - depth // 2), (x1, floor_y + height, z + depth // 2))

    def doorway_x(self, z0: int, z1: int, x: int, floor_y: int, height: int, depth: int = 3) -> None:
        self.clear((x - depth // 2, floor_y + 1, z0), (x + depth // 2, floor_y + height, z1))

    def barrel_roof(
        self,
        x0: int,
        z0: int,
        x1: int,
        z1: int,
        wall_top: int,
        rise: int,
        body: str,
        trim: str,
    ) -> None:
        center = (x0 + x1) / 2.0
        radius = max(1.0, (x1 - x0) / 2.0)
        heights: dict[int, int] = {}
        for x in range(x0, x1 + 1):
            normalized = abs(x - center) / radius
            y = wall_top + max(0, round(rise * (1.0 - normalized ** 1.55)))
            heights[x] = y
        for x, roof_y in heights.items():
            neighbor_heights = [
                heights.get(x - 1, wall_top),
                heights.get(x + 1, wall_top),
            ]
            lower_y = min(roof_y, *neighbor_heights)
            block = trim if x in (x0, x1) or x % 7 == 0 else body
            # Fill each stepped rise so adjacent roof strips cannot expose the room.
            self.fill((x, lower_y, z0), (x, roof_y, z1), block)
        for z in (z0, z1):
            for x, roof_y in heights.items():
                self.fill((x, wall_top + 1, z), (x, roof_y, z), body)

    def octagon_contains(self, x: int, z: int, x0: int, z0: int, x1: int, z1: int, cut: int) -> bool:
        return (
            x0 <= x <= x1
            and z0 <= z <= z1
            and x - x0 + z - z0 >= cut
            and x1 - x + z - z0 >= cut
            and x - x0 + z1 - z >= cut
            and x1 - x + z1 - z >= cut
        )

    def octagon_room(
        self,
        x0: int,
        z0: int,
        x1: int,
        z1: int,
        floor_y: int,
        wall_top: int,
        cut: int,
        floor_block: str,
        wall_block: str,
        trim_block: str,
        support_base: bool = True,
    ) -> None:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not self.octagon_contains(x, z, x0, z0, x1, z1, cut):
                    continue
                if support_base:
                    self.set(x, floor_y - 3, z, wall_block)
                    self.set(x, floor_y - 2, z, wall_block)
                    self.set(x, floor_y - 1, z, trim_block)
                self.set(x, floor_y, z, floor_block)
                edge = any(
                    not self.octagon_contains(x + dx, z + dz, x0, z0, x1, z1, cut)
                    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
                )
                for y in range(floor_y + 1, wall_top + 1):
                    if edge:
                        self.set(x, y, z,
                                 trim_block if y in (floor_y + 5, wall_top) else wall_block)
                    elif (x, y, z) not in self.blocks:
                        self.set(x, y, z, AIR)
                if not edge:
                    self.interior_cells.add((x, floor_y + 1, z))

    def cap_roof(
        self,
        cx: int,
        cz: int,
        rx: int,
        rz: int,
        base_y: int,
        rise: int,
        body: str,
        trim: str,
    ) -> None:
        heights: dict[tuple[int, int], int] = {}
        for x in range(cx - rx, cx + rx + 1):
            for z in range(cz - rz, cz + rz + 1):
                nx = (x - cx) / max(1, rx)
                nz = (z - cz) / max(1, rz)
                distance = nx * nx + nz * nz
                if distance > 1.0:
                    continue
                heights[(x, z)] = base_y + round(rise * (1.0 - distance) ** 0.48)
        for (x, z), roof_y in heights.items():
            neighbors = [
                heights.get((x + 1, z), base_y),
                heights.get((x - 1, z), base_y),
                heights.get((x, z + 1), base_y),
                heights.get((x, z - 1), base_y),
            ]
            lower_y = min(roof_y, *neighbors)
            nx = (x - cx) / max(1, rx)
            nz = (z - cz) / max(1, rz)
            edge = nx * nx + nz * nz > 0.78
            self.fill((x, lower_y, z), (x, roof_y, z), trim if edge else body)

    def octagon_ceiling(
        self, x0: int, z0: int, x1: int, z1: int,
        y: int, cut: int, body: str, trim: str
    ) -> None:
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not self.octagon_contains(x, z, x0, z0, x1, z1, cut):
                    continue
                edge = any(
                    not self.octagon_contains(x + dx, z + dz, x0, z0, x1, z1, cut)
                    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
                )
                self.set(x, y, z, trim if edge else body)

    def pillar(self, x: int, z: int, y0: int, y1: int, body: str, trim: str, radius: int = 1) -> None:
        self.fill((x - radius, y0, z - radius), (x + radius, y1, z + radius), body)
        self.fill((x - radius - 1, y0, z - radius - 1), (x + radius + 1, y0 + 1, z + radius + 1), trim)
        self.fill((x - radius - 1, y1 - 1, z - radius - 1), (x + radius + 1, y1, z + radius + 1), trim)

    def staircase_z(
        self, center_x: int, start_z: int, start_y: int, target_y: int,
        width: int, toward_north: bool, body: str, trim: str
    ) -> None:
        half = width // 2
        steps = target_y - start_y + 1
        if steps <= 0:
            raise ValueError("stair target must be above its start")
        for step in range(steps):
            z = start_z - step if toward_north else start_z + step
            y = start_y + step
            # Cut the upper floor and roof clearance before placing the tread.
            self.clear((center_x - half, y + 1, z),
                       (center_x + half, y + 3, z))
            self.fill((center_x - half, y, z), (center_x + half, y, z), body)
            self.set(center_x - half, y, z, trim)
            self.set(center_x + half, y, z, trim)
        landing_z = start_z - steps if toward_north else start_z + steps
        self.clear((center_x - half, target_y + 1, landing_z - 1),
                   (center_x + half, target_y + 3, landing_z + 1))
        self.floor(center_x - half, landing_z - 1, center_x + half,
                   landing_z + 1, target_y, body)
        for rail_x in (center_x - half, center_x + half):
            self.fill((rail_x, target_y + 1, landing_z - 1),
                      (rail_x, target_y + 1, landing_z + 1), trim)

    def bridge_x(self, x0: int, x1: int, center_z: int, y: int, body: str, trim: str) -> None:
        self.floor(x0, center_z - 2, x1, center_z + 2, y, body)
        self.fill((x0, y + 1, center_z - 2), (x1, y + 1, center_z - 2), trim)
        self.fill((x0, y + 1, center_z + 2), (x1, y + 1, center_z + 2), trim)

    def treasure_dais(
        self, cx: int, cz: int, y: int, marker: str,
        body: str, trim: str, light: str, chest_count: int
    ) -> None:
        self.floor(cx - 5, cz - 4, cx + 5, cz + 4, y, body)
        for x in range(cx - 5, cx + 6):
            self.set(x, y + 1, cz - 4, trim)
            self.set(x, y + 1, cz + 4, trim)
        for z in range(cz - 4, cz + 5):
            self.set(cx - 5, y + 1, z, trim)
            self.set(cx + 5, y + 1, z, trim)
        for x, z in ((cx - 4, cz - 3), (cx + 4, cz - 3),
                     (cx - 4, cz + 3), (cx + 4, cz + 3)):
            self.pillar(x, z, y + 1, y + 4, body, trim)
            self.set(x, y + 4, z, light)
        offsets = (0,) if chest_count == 1 else (-3, 0, 3)
        for dx in offsets:
            self.marker(cx + dx, y + 1, cz, marker)

    def spawner_dais(
        self, cx: int, cz: int, y: int, marker: str,
        body: str, trim: str, light: str
    ) -> None:
        self.floor(cx - 4, cz - 4, cx + 4, cz + 4, y, trim)
        self.floor(cx - 2, cz - 2, cx + 2, cz + 2, y + 1, body)
        for x, z in ((cx - 4, cz - 4), (cx + 4, cz - 4),
                     (cx - 4, cz + 4), (cx + 4, cz + 4)):
            self.pillar(x, z, y + 1, y + 3, body, trim)
            self.set(x, y + 3, z, light)
        self.marker(cx, y + 2, cz, marker)

    def floor_frame(self, x0: int, z0: int, x1: int, z1: int, y: int, block: str) -> None:
        for x in range(x0, x1 + 1):
            self.set(x, y, z0, block)
            self.set(x, y, z1, block)
        for z in range(z0, z1 + 1):
            self.set(x0, y, z, block)
            self.set(x1, y, z, block)

    def snake_vault(
        self, cx: int, cz: int, y: int, marker: str,
        body: str, trim: str, light: str, treasure: bool
    ) -> None:
        # A lit floor spine ends in a wall-backed vault. Nothing stands loose
        # in the route, so the player reads the room and its reward at once.
        self.floor(cx - 3, cz - 7, cx + 3, cz + 5, y, body)
        for z in range(cz - 7, cz + 4):
            self.set(cx, y, z, light if (z - cz) % 3 == 0 else trim)
            self.set(cx - 2, y, z, trim)
            self.set(cx + 2, y, z, trim)
        self.fill((cx - 5, y + 1, cz + 5), (cx + 5, y + 5, cz + 5), body)
        self.fill((cx - 4, y + 2, cz + 4), (cx + 4, y + 4, cz + 4), trim)
        self.clear((cx - 3, y + 2, cz + 3), (cx + 3, y + 4, cz + 4))
        for dx in (-4, 4):
            self.set(cx + dx, y + 3, cz + 3, light)
        offsets = (-2, 0, 2) if treasure else (0,)
        for dx in offsets:
            self.marker(cx + dx, y + 1, cz + 3, marker)

    def snake_guard_pit(
        self, cx: int, cz: int, y: int, marker: str,
        body: str, trim: str, light: str
    ) -> None:
        self.floor(cx - 5, cz - 5, cx + 5, cz + 5, y, trim)
        self.clear((cx - 3, y, cz - 3), (cx + 3, y + 1, cz + 3))
        self.floor(cx - 3, cz - 3, cx + 3, cz + 3, y - 1, body)
        for x, z in ((cx - 4, cz), (cx + 4, cz), (cx, cz - 4), (cx, cz + 4)):
            self.set(x, y + 1, z, light)
        self.marker(cx, y, cz, marker)

    def mushroom_grotto(
        self, cx: int, cz: int, y: int, marker: str,
        body: str, trim: str, light: str, treasure: bool
    ) -> None:
        # Low radial gills lead to a bright cap alcove. The reward remains in
        # direct sight instead of being surrounded by generic columns.
        for radius in (6, 4, 2):
            for x in range(cx - radius, cx + radius + 1):
                for z in range(cz - radius, cz + radius + 1):
                    distance = abs(x - cx) + abs(z - cz)
                    if radius * 0.85 <= distance <= radius * 1.2:
                        self.set(x, y + (6 - radius) // 2, z,
                                 trim if radius != 4 else body)
        self.fill((cx - 5, y + 1, cz - 5), (cx + 5, y + 1, cz - 5), trim)
        self.fill((cx - 4, y + 2, cz - 5), (cx + 4, y + 4, cz - 5), body)
        for dx in (-4, 0, 4):
            self.set(cx + dx, y + 3, cz - 4, light)
        positions = ((-2, 0), (0, 0), (2, 0)) if treasure else ((0, 0),)
        for dx, dz in positions:
            self.marker(cx + dx, y + 1, cz + dz, marker)

    def visible_treasure_alcove(
        self, cx: int, cz: int, y: int, marker: str,
        body: str, trim: str, light: str, faces_south: bool
    ) -> None:
        """Single entrance-level chest with a bright wall niche and floor guide."""
        back_z = cz - 2 if faces_south else cz + 2
        front_z = cz + 1 if faces_south else cz - 1
        self.fill((cx - 4, y + 1, back_z), (cx + 4, y + 5, back_z), body)
        self.fill((cx - 3, y + 2, back_z + (1 if faces_south else -1)),
                  (cx + 3, y + 4, back_z + (1 if faces_south else -1)), trim)
        self.clear((cx - 2, y + 2, back_z + (1 if faces_south else -1)),
                   (cx + 2, y + 3, back_z + (1 if faces_south else -1)))
        self.set(cx - 3, y + 3, front_z, light)
        self.set(cx + 3, y + 3, front_z, light)
        self.marker(cx, y + 1, cz, marker)
        for step in range(1, 8):
            z = cz + step if faces_south else cz - step
            self.set(cx, y, z, light if step % 3 == 0 else trim)

    def mushroom_nest(
        self, cx: int, cz: int, y: int, marker: str,
        body: str, trim: str, light: str
    ) -> None:
        for radius, level in ((5, y), (3, y + 1)):
            for x in range(cx - radius, cx + radius + 1):
                for z in range(cz - radius, cz + radius + 1):
                    if (x - cx) ** 2 + (z - cz) ** 2 <= radius * radius:
                        self.set(x, level, z, trim if radius == 5 else body)
        for dx, dz in ((-4, -2), (4, -2), (-4, 2), (4, 2)):
            self.set(cx + dx, y + 1, cz + dz, light)
        self.marker(cx, y + 2, cz, marker)

    def serpent_archive_wall(
        self, x: int, z0: int, z1: int, y: int,
        inward: int, body: str, trim: str, light: str
    ) -> None:
        """Deep wall cases with a restrained luminous data spine."""
        for z in range(z0, z1 + 1, 5):
            self.fill((x, y + 1, z), (x, y + 4, min(z + 3, z1)), trim)
            shelf_x = x + inward
            self.fill((shelf_x, y + 1, z + 1),
                      (shelf_x, y + 1, min(z + 2, z1)), body)
            self.fill((shelf_x, y + 3, z + 1),
                      (shelf_x, y + 3, min(z + 2, z1)), body)
            self.set(shelf_x, y + 4, min(z + 1, z1), light)

    def fungal_display_bed(
        self, cx: int, cz: int, y: int,
        long_x: bool, body: str, trim: str, light: str
    ) -> None:
        """Low herbarium bed: readable decoration that never blocks sightlines."""
        rx, rz = (4, 2) if long_x else (2, 4)
        self.floor(cx - rx, cz - rz, cx + rx, cz + rz, y, trim)
        self.floor(cx - rx + 1, cz - rz + 1, cx + rx - 1, cz + rz - 1,
                   y + 1, body)
        for dx, dz in ((-rx + 1, -rz + 1), (rx - 1, -rz + 1),
                       (-rx + 1, rz - 1), (rx - 1, rz - 1)):
            self.set(cx + dx, y + 2, cz + dz, light)

    def hanging_light(
        self, x: int, z: int, ceiling_y: int,
        drop: int, chain: str, light: str
    ) -> None:
        self.fill((x, ceiling_y - drop, z), (x, ceiling_y - 1, z), chain)
        self.set(x, ceiling_y - drop - 1, z, light)

    def arena_wall_shrine(
        self, x: int, z: int, y: int, along_x: bool,
        body: str, trim: str, light: str
    ) -> None:
        """Compact wall shrine with stepped silhouette and no floor obstruction."""
        if along_x:
            self.fill((x - 3, y + 1, z), (x + 3, y + 1, z), trim)
            self.fill((x - 2, y + 2, z), (x + 2, y + 5, z), body)
            self.fill((x - 3, y + 5, z), (x + 3, y + 6, z), trim)
        else:
            self.fill((x, y + 1, z - 3), (x, y + 1, z + 3), trim)
            self.fill((x, y + 2, z - 2), (x, y + 5, z + 2), body)
            self.fill((x, y + 5, z - 3), (x, y + 6, z + 3), trim)
        self.set(x, y + 4, z, light)

    def marker(self, x: int, y: int, z: int, metadata: str) -> None:
        self.markers.append(Marker((x, y, z), metadata))
        self.set(x, y, z, "minecraft:structure_block[mode=data]")

    def ensure_marker_clearance(self) -> None:
        for marker in self.markers:
            if "chest" not in marker.metadata:
                continue
            x, y, z = marker.pos
            self.clear((x, y + 1, z), (x, y + 2, z))


def build_legacy_snake_temple() -> Structure:
    s = Structure("snake_king_temple", (80, 30, 96))
    floor_y = 3

    # Gatehouse: narrow, tall and deeply layered.
    s.room(25, 76, 54, 93, floor_y, 16, SNAKE_FLOOR, SNAKE_BRICK, SNAKE_LATTICE)
    s.doorway_z(35, 44, 93, floor_y, 9)
    s.doorway_z(34, 45, 76, floor_y, 9)
    for x in (27, 52):
        s.fill((x, floor_y + 1, 91), (x, 14, 92), SNAKE_CHISELED)
        s.set(x, 13, 90, SNAKE_LIGHT)
    for z in range(80, 91, 4):
        s.floor(37, z, 42, z, floor_y, SNAKE_CHISELED)
        s.set(39, floor_y, z, SNAKE_LIGHT)
    s.hanging_light(39, 84, 16, 5, SNAKE_CHISELED, SNAKE_LIGHT)
    s.barrel_roof(24, 75, 55, 94, 16, 5, SNAKE_BRICK, SNAKE_CHISELED)

    # Guard archives flank the sole entrance and make the approach functional.
    for left in (True, False):
        x0, x1 = (3, 24) if left else (55, 76)
        s.room(x0, 72, x1, 92, floor_y, 12, SNAKE_BRICK, SNAKE_BRICK, SNAKE_CHISELED)
        s.doorway_x(78, 84, 24 if left else 55, floor_y, 7)
        s.barrel_roof(x0 - 1, 71, x1 + 1, 93, 12, 4, SNAKE_BRICK, SNAKE_CHISELED)
        s.serpent_archive_wall(5 if left else 74, 75, 89, floor_y,
                               1 if left else -1, SNAKE_FOUNDATION,
                               SNAKE_LATTICE, SNAKE_LIGHT)
        desk_x = 15 if left else 64
        s.floor(desk_x - 3, 79, desk_x + 3, 83, floor_y + 1, SNAKE_FOUNDATION)
        s.floor(desk_x - 2, 80, desk_x + 2, 82, floor_y + 2, SNAKE_CHISELED)
        s.set(desk_x, floor_y + 3, 81, SNAKE_LIGHT)
        s.visible_treasure_alcove(desk_x, 88, floor_y,
                                  "snake_common_chest", SNAKE_FOUNDATION,
                                  SNAKE_CHISELED, SNAKE_LIGHT, False)
    # Close the one-block seams between the guard archives and side chapels.
    for x0, x1, outer_x in ((3, 24, 3), (55, 76, 76)):
        s.floor(x0, 71, x1, 71, floor_y, SNAKE_BRICK)
        s.fill((x0, floor_y - 2, 71), (x1, floor_y - 1, 71), SNAKE_CHISELED)
        s.fill((outer_x, floor_y + 1, 71), (outer_x, 12, 71), SNAKE_BRICK)
        s.fill((x0, 13, 71), (x1, 13, 71), SNAKE_CHISELED)

    # Axial nave with side chapels.
    s.room(20, 42, 59, 77, floor_y, 22, SNAKE_BRICK, SNAKE_BRICK, SNAKE_CHISELED)
    s.doorway_z(34, 45, 77, floor_y, 9)
    s.doorway_z(33, 46, 42, floor_y, 10)
    s.barrel_roof(19, 41, 60, 78, 22, 5, SNAKE_BRICK, SNAKE_CHISELED)
    for z in (49, 59, 69):
        # Structural ribs stay attached to the walls instead of becoming a
        # forest of freestanding columns.
        s.fill((21, floor_y + 1, z - 1), (23, 16, z + 1), SNAKE_CHISELED)
        s.fill((56, floor_y + 1, z - 1), (58, 16, z + 1), SNAKE_CHISELED)
        s.set(23, 14, z, SNAKE_LIGHT)
        s.set(56, 14, z, SNAKE_LIGHT)
    # A luminous serpent spine guides the player through the nave while the
    # side aisles stay open for combat and navigation.
    for z in range(46, 75):
        width = 2 if z % 8 in (0, 1, 2) else 1
        s.floor(39 - width, z, 40 + width, z, floor_y,
                SNAKE_LIGHT if z % 6 == 0 else SNAKE_CHISELED)
    for x, facing in ((22, 1), (57, -1)):
        s.serpent_archive_wall(x, 48, 70, floor_y, facing,
                               SNAKE_FOUNDATION, SNAKE_CHISELED, SNAKE_LIGHT)
    for z in (52, 62, 72):
        s.hanging_light(39, z, 21, 7, SNAKE_CHISELED, SNAKE_LIGHT)

    for left in (True, False):
        x0, x1 = (3, 20) if left else (59, 76)
        s.room(x0, 47, x1, 70, floor_y, 12, SNAKE_FLOOR, SNAKE_BRICK, SNAKE_LATTICE)
        s.doorway_x(55, 62, 20 if left else 59, floor_y, 7)
        s.snake_vault(11 if left else 68, 58, floor_y,
                      "snake_common_chest", SNAKE_FOUNDATION,
                      SNAKE_CHISELED, SNAKE_LIGHT, False)

        upper_y = 13
        s.room(x0, 47, x1, 70, upper_y, 20, SNAKE_BRICK, SNAKE_BRICK,
               SNAKE_CHISELED, support_base=False)
        s.barrel_roof(x0 - 1, 46, x1 + 1, 71, 20, 4, SNAKE_BRICK, SNAKE_CHISELED)
        s.doorway_x(55, 62, 20 if left else 59, upper_y, 5)
        s.snake_vault(11 if left else 68, 52, upper_y,
                      "snake_treasure_chest", SNAKE_FOUNDATION,
                      SNAKE_CHISELED, SNAKE_LIGHT, True)
        s.staircase_z(11 if left else 68, 68, floor_y + 1, upper_y, 5, True,
                      SNAKE_BRICK, SNAKE_CHISELED)

    # Two ritual preparation rooms broaden the route before the sanctum.
    for left in (True, False):
        x0, x1 = (3, 14) if left else (65, 76)
        s.room(x0, 29, x1, 45, floor_y, 11, SNAKE_FLOOR, SNAKE_BRICK, SNAKE_LATTICE)
        s.doorway_x(34, 40, 14 if left else 65, floor_y, 7)
        s.barrel_roof(x0 - 1, 28, x1 + 1, 46, 11, 4, SNAKE_BRICK, SNAKE_CHISELED)
        s.floor_frame(x0 + 3, 32, x1 - 3, 42, floor_y, SNAKE_CHISELED)
        table_x = (x0 + x1) // 2
        s.floor(table_x - 2, 34, table_x + 2, 40, floor_y + 1, SNAKE_FOUNDATION)
        s.floor(table_x - 1, 35, table_x + 1, 39, floor_y + 2, SNAKE_CHISELED)
        s.set(table_x, floor_y + 3, 37, SNAKE_LIGHT)

    # Cross hall prevents a straight corridor-to-boss-box composition.
    s.room(14, 28, 65, 43, floor_y, 15, SNAKE_BRICK, SNAKE_BRICK, SNAKE_CHISELED)
    s.doorway_z(33, 46, 43, floor_y, 10)
    s.doorway_z(31, 48, 28, floor_y, 11)
    s.barrel_roof(13, 27, 66, 44, 15, 5, SNAKE_BRICK, SNAKE_CHISELED)
    s.snake_guard_pit(18, 34, floor_y, "snake_spawner_bogged",
                      SNAKE_FOUNDATION, SNAKE_CHISELED, SNAKE_LIGHT)
    s.snake_guard_pit(61, 34, floor_y, "snake_spawner_skeleton",
                      SNAKE_FOUNDATION, SNAKE_CHISELED, SNAKE_LIGHT)
    s.bridge_x(20, 59, 59, 13, SNAKE_FOUNDATION, SNAKE_CHISELED)
    for x in range(24, 56, 6):
        s.hanging_light(x, 35, 15, 5, SNAKE_CHISELED, SNAKE_LIGHT)

    # Dedicated enclosed arena, octagonal inside a rectangular temple mass.
    s.octagon_room(8, 3, 71, 29, floor_y, 21, 7, SNAKE_BRICK, SNAKE_BRICK, SNAKE_CHISELED)
    s.doorway_z(31, 48, 29, floor_y, 11)
    s.octagon_ceiling(8, 3, 71, 29, 21, 7, SNAKE_BRICK, SNAKE_CHISELED)
    s.barrel_roof(7, 2, 72, 30, 21, 6, SNAKE_BRICK, SNAKE_CHISELED)

    # A layered ceremonial arena uses nested serpent frames and crystal nodes.
    for inset in (4, 8, 13):
        s.floor_frame(8 + inset, 3 + inset, 71 - inset, 29 - inset,
                      floor_y, SNAKE_FLOOR)
    for x in range(18, 62, 7):
        s.set(x, floor_y, 8, SNAKE_LIGHT)
        s.set(x, floor_y, 24, SNAKE_LIGHT)
    for z in range(9, 24, 5):
        s.set(18, floor_y, z, SNAKE_LIGHT)
        s.set(61, floor_y, z, SNAKE_LIGHT)
    # Wall shrines and an upper crystal gallery enrich the arena perimeter but
    # leave the complete inner combat oval unobstructed.
    for x, z, along_x in ((26, 6, True), (53, 6, True),
                          (26, 26, True), (53, 26, True),
                          (11, 16, False), (68, 16, False)):
        s.arena_wall_shrine(x, z, floor_y, along_x,
                            SNAKE_FOUNDATION, SNAKE_LATTICE, SNAKE_LIGHT)
    for x in range(20, 61, 5):
        s.set(x, 18, 5, SNAKE_LIGHT)
        s.set(x, 18, 27, SNAKE_LIGHT)
    for radius, y in ((9, floor_y + 1), (6, floor_y + 2), (3, floor_y + 3)):
        s.floor(39 - radius, 15 - radius, 40 + radius, 16 + radius, y,
                SNAKE_CHISELED if radius != 6 else SNAKE_FOUNDATION)
    s.set(39, floor_y + 4, 15, "virtual_craft:snake_king_altar")

    # Exterior buttresses and a readable processional stair.
    for x in (4, 18, 61, 75):
        s.fill((x - 1, 2, 73), (x + 1, 12, 75), SNAKE_FOUNDATION)
        s.set(x, 10, 72, SNAKE_LIGHT)
    for step in range(5):
        s.floor(33 - step, 91 + step, 46 + step, 95,
                floor_y - step // 2, SNAKE_FOUNDATION)
    s.ensure_marker_clearance()
    return s


def build_legacy_mushroom_palace() -> Structure:
    s = Structure("mushroom_king_palace", (88, 32, 88))
    floor_y = 3

    # Stem gate and broad cap-shaped foyer.
    s.room(34, 69, 53, 85, floor_y, 15, FUNGAL_CAP, FUNGAL_BRICK, FUNGAL_GILL)
    s.doorway_z(40, 47, 85, floor_y, 8)
    s.doorway_z(39, 48, 69, floor_y, 9)
    s.floor(34, 69, 53, 85, 15, FUNGAL_BRICK)
    s.cap_roof(44, 77, 13, 12, 15, 6, FUNGAL_BRICK, FUNGAL_CHISELED)

    # Closed herbarium rooms flank the single stem-shaped entrance.
    for left in (True, False):
        x0, x1 = (3, 33) if left else (54, 84)
        s.room(x0, 71, x1, 84, floor_y, 12, FUNGAL_BRICK, FUNGAL_BRICK, FUNGAL_CHISELED)
        s.doorway_x(75, 81, 33 if left else 54, floor_y, 7)
        s.floor(x0, 71, x1, 84, 12, FUNGAL_BRICK)
        s.cap_roof((x0 + x1) // 2, 77, 17, 9, 12, 5, FUNGAL_BRICK, FUNGAL_CHISELED)
        for x in range(x0 + 5, x1 - 3, 7):
            s.set(x, floor_y, 75, FUNGAL_LIGHT)
            s.set(x, floor_y, 81, FUNGAL_LIGHT)
        for z in (75, 81):
            for x in range(x0 + 7, x1 - 5, 10):
                s.fungal_display_bed(x, z, floor_y, True,
                                     FUNGAL_FOUNDATION, FUNGAL_CHISELED, FUNGAL_LIGHT)
        s.hanging_light((x0 + x1) // 2, 78, 12, 4,
                        FUNGAL_CHISELED, FUNGAL_LIGHT)
        s.visible_treasure_alcove(15 if left else 72, 73, floor_y,
                                  "mushroom_common_chest", FUNGAL_FOUNDATION,
                                  FUNGAL_CHISELED, FUNGAL_LIGHT, True)

    s.octagon_room(23, 45, 64, 70, floor_y, 23, 6, FUNGAL_CAP, FUNGAL_BRICK, FUNGAL_GILL)
    s.doorway_z(38, 49, 70, floor_y, 9)
    s.doorway_z(36, 51, 45, floor_y, 10)
    s.octagon_ceiling(23, 45, 64, 70, 23, 6, FUNGAL_BRICK, FUNGAL_CHISELED)
    s.cap_roof(44, 57, 25, 17, 23, 6, FUNGAL_BRICK, FUNGAL_CHISELED)
    # The foyer reads as a gill rotunda rather than an empty transition box.
    for angle_index in range(8):
        angle = math.tau * angle_index / 8.0
        x = round(44 + math.cos(angle) * 13)
        z = round(57 + math.sin(angle) * 8)
        # Gill ribs are wall-connected reliefs rather than standalone pillars.
        wall_x = round(44 + math.cos(angle) * 16)
        wall_z = round(57 + math.sin(angle) * 11)
        s.fill((min(x, wall_x), floor_y + 7, min(z, wall_z)),
               (max(x, wall_x), floor_y + 9, max(z, wall_z)), FUNGAL_GILL)
        s.set(x, floor_y + 7, z, FUNGAL_LIGHT)
    for radius in (5, 9, 13):
        for index in range(24):
            angle = math.tau * index / 24.0
            s.set(round(44 + math.cos(angle) * radius), floor_y,
                  round(57 + math.sin(angle) * radius), FUNGAL_CHISELED)
    s.hanging_light(44, 57, 22, 9, FUNGAL_CHISELED, FUNGAL_LIGHT)

    # Four asymmetrical service pods make the palace radial rather than axial.
    pods = (
        (3, 48, 25, 70, 6, "mushroom_spawner_spider"),
        (62, 48, 84, 70, 6, "mushroom_spawner_slime"),
        (4, 24, 26, 46, 6, "mushroom_common_chest"),
        (61, 24, 83, 46, 6, "mushroom_common_chest"),
    )
    for index, (x0, z0, x1, z1, cut, marker) in enumerate(pods):
        s.octagon_room(x0, z0, x1, z1, floor_y, 13, cut, FUNGAL_BRICK, FUNGAL_BRICK, FUNGAL_CHISELED)
        s.octagon_ceiling(x0, z0, x1, z1, 13, cut, FUNGAL_BRICK, FUNGAL_CHISELED)
        if index >= 2:
            s.cap_roof((x0 + x1) // 2, (z0 + z1) // 2,
                       (x1 - x0) // 2 + 2, (z1 - z0) // 2 + 2,
                       13, 5, FUNGAL_BRICK, FUNGAL_CHISELED)
        cx = (x0 + x1) // 2
        cz = (z0 + z1) // 2
        if "spawner" in marker:
            s.mushroom_nest(cx, cz, floor_y, marker,
                            FUNGAL_FOUNDATION, FUNGAL_CHISELED, FUNGAL_LIGHT)
        else:
            s.mushroom_grotto(cx, cz, floor_y, marker,
                              FUNGAL_FOUNDATION, FUNGAL_CHISELED, FUNGAL_LIGHT, False)
        for angle_index in range(4):
            angle = math.tau * angle_index / 4.0 + math.pi / 4.0
            px = round(cx + math.cos(angle) * 7)
            pz = round(cz + math.sin(angle) * 7)
            s.set(px, floor_y + 1, pz, FUNGAL_CHISELED)
            s.set(px, floor_y + 2, pz, FUNGAL_LIGHT)
    s.doorway_x(54, 63, 24, floor_y, 7)
    s.doorway_x(54, 63, 63, floor_y, 7)
    s.doorway_x(31, 39, 25, floor_y, 7)
    s.doorway_x(31, 39, 62, floor_y, 7)

    # Two accessible upper spore chambers and a bridge overlooking the foyer.
    for x0, x1, center_x in ((3, 25, 14), (62, 84, 73)):
        s.octagon_room(x0, 48, x1, 70, 14, 21, 6, FUNGAL_CAP,
                       FUNGAL_BRICK, FUNGAL_GILL, support_base=False)
        s.octagon_ceiling(x0, 48, x1, 70, 21, 6, FUNGAL_BRICK, FUNGAL_CHISELED)
        s.cap_roof(center_x, 59, 13, 13, 21, 5, FUNGAL_BRICK, FUNGAL_CHISELED)
        s.doorway_x(55, 63, 25 if x0 == 3 else 62, 14, 5)
        s.mushroom_grotto(center_x, 52, 14, "mushroom_treasure_chest",
                          FUNGAL_FOUNDATION, FUNGAL_CHISELED, FUNGAL_LIGHT, True)
        s.staircase_z(center_x, 68, floor_y + 1, 14, 5, True,
                      FUNGAL_BRICK, FUNGAL_CHISELED)
    s.bridge_x(25, 62, 59, 14, FUNGAL_FOUNDATION, FUNGAL_CHISELED)

    # Huge octagonal cap hall, enclosed and clear for the boss fight.
    s.octagon_room(15, 3, 72, 46, floor_y, 22, 10, FUNGAL_BRICK, FUNGAL_BRICK, FUNGAL_CHISELED)
    s.doorway_z(35, 52, 46, floor_y, 11)
    s.octagon_ceiling(15, 3, 72, 46, 22, 10, FUNGAL_BRICK, FUNGAL_CHISELED)
    s.cap_roof(44, 24, 34, 27, 22, 8, FUNGAL_BRICK, FUNGAL_CHISELED)

    # Gill ribs, radial floor inlays and a root-shaped altar.
    for z in range(8, 42, 6):
        s.fill((28, 20, z), (59, 20, z), FUNGAL_CHISELED)
    for inset in (5, 10, 15):
        s.floor_frame(15 + inset, 3 + inset, 72 - inset, 46 - inset,
                      floor_y, FUNGAL_CAP)
    for x, z in ((28, 12), (44, 9), (60, 12), (25, 24),
                 (63, 24), (28, 37), (44, 40), (60, 37)):
        s.set(x, floor_y, z, FUNGAL_LIGHT)
    # Layered perimeter gardens, hanging spore lamps and wall shrines make the
    # boss hall ceremonial without putting obstacles in the central arena.
    for x, z, along_x in ((30, 6, True), (57, 6, True),
                          (30, 43, True), (57, 43, True),
                          (18, 24, False), (69, 24, False)):
        s.arena_wall_shrine(x, z, floor_y, along_x,
                            FUNGAL_FOUNDATION, FUNGAL_GILL, FUNGAL_LIGHT)
    for x, z in ((27, 15), (44, 12), (61, 15),
                 (27, 34), (44, 37), (61, 34)):
        s.hanging_light(x, z, 21, 7, FUNGAL_CHISELED, FUNGAL_LIGHT)
    for cx, cz in ((23, 10), (65, 10), (23, 39), (65, 39)):
        s.fungal_display_bed(cx, cz, floor_y, True,
                             FUNGAL_FOUNDATION, FUNGAL_CHISELED, FUNGAL_LIGHT)
    # Keep a continuous 44x34 combat floor around the altar. The old stepped
    # dais broke movement and left the summon point above an uneven platform.
    arena_x0, arena_z0, arena_x1, arena_z1 = 22, 8, 65, 41
    s.floor(arena_x0, arena_z0, arena_x1, arena_z1, floor_y, FUNGAL_CAP)
    s.clear((arena_x0, floor_y + 1, arena_z0),
            (arena_x1, floor_y + 10, arena_z1))
    # A flat gill medallion preserves ceremony without becoming collision.
    for radius, block in ((11, FUNGAL_CHISELED), (7, FUNGAL_FOUNDATION),
                          (3, FUNGAL_LIGHT)):
        for index in range(48):
            angle = math.tau * index / 48.0
            x = round(43.5 + math.cos(angle) * radius)
            z = round(23.5 + math.sin(angle) * radius)
            s.set(x, floor_y, z, block)
    s.set(43, floor_y + 1, 23, "virtual_craft:mushroom_king_altar")

    # Spore towers visually anchor the four quadrants.
    for x, z in ((8, 54), (79, 54), (10, 17), (77, 17)):
        s.pillar(x, z, 2, 17, FUNGAL_FOUNDATION, FUNGAL_CHISELED, 2)
        s.cap_roof(x, z, 6, 6, 17, 4, FUNGAL_BRICK, FUNGAL_CHISELED)
        s.set(x, 19, z, FUNGAL_LIGHT)
    s.ensure_marker_clearance()
    return s


def disk(s: Structure, cx: int, cz: int, radius: int, y: int, block: str,
         inner: int = 0) -> None:
    for x in range(cx - radius, cx + radius + 1):
        for z in range(cz - radius, cz + radius + 1):
            d = (x - cx) ** 2 + (z - cz) ** 2
            if inner ** 2 <= d <= radius ** 2:
                s.set(x, y, z, block)


def monumental_base(s: Structure, cx: int, cz: int, radius: int, floor: str,
                    foundation: str, trim: str) -> None:
    for y in range(4):
        disk(s, cx, cz, radius - y, y, foundation if y < 3 else floor)
    disk(s, cx, cz, radius - 3, 3, trim, radius - 5)
    # Explicit air is essential: absent template cells do not remove terrain.
    for x in range(cx - radius + 3, cx + radius - 2):
        for z in range(cz - radius + 3, cz + radius - 2):
            if (x - cx) ** 2 + (z - cz) ** 2 <= (radius - 3) ** 2:
                s.clear((x, 4, z), (x, 24, z))


def stone_tube(s: Structure, points: list[tuple[int, int, int]], radius: int,
               block: str) -> None:
    """Overlapping voxel balls form continuous roots, ribs and serpent bodies."""
    for x, y, z in points:
        for dx in range(-radius, radius + 1):
            for dy in range(-radius, radius + 1):
                for dz in range(-radius, radius + 1):
                    if dx * dx + dy * dy + dz * dz <= radius * radius:
                        s.set(x + dx, y + dy, z + dz, block)


def arch_rib(s: Structure, cx: int, z: int, spring: int, radius: int,
             rise: int, block: str, along_z: bool = False) -> None:
    points = []
    for t in range(81):
        a = math.pi*t/80
        offset = round(radius*math.cos(a))
        y = spring+round(rise*math.sin(a))
        points.append((cx,y,z+offset) if along_z else (cx+offset,y,z))
    stone_tube(s,points,1,block)


def carved_cobra(s: Structure, cx: int, cz: int) -> None:
    # Sculpt the hood and tapering neck as elliptical layers, not a square head
    # pasted onto a rectangular column. The belly faces the approach (+Z).
    for y in range(8,49):
        hood = 3 if y<25 else round(3+8*math.sin((y-25)/24*math.pi))
        for dx in range(-hood,hood+1):
            depth = max(1,round(3*math.sqrt(max(0,1-(dx/max(1,hood))**2))))
            s.fill((cx+dx,y,cz-depth),(cx+dx,y,cz+depth),SNAKE_FOUNDATION)
            if y>=26 and (abs(dx)==hood or (y%5==0 and abs(dx)>3)):
                s.set(cx+dx,y,cz+depth,SNAKE_BRICK)
        if y%3==0:
            s.fill((cx-2,y,cz+3),(cx+2,y,cz+3),SNAKE_BRICK)
    for y,rx,rz in ((41,3,4),(42,4,5),(43,5,6),(44,5,6),(45,4,5),(46,3,4)):
        for dx in range(-rx,rx+1):
            for dz in range(-rz,rz+1):
                if (dx/rx)**2+(dz/rz)**2<=1:
                    s.set(cx+dx,y,cz+5+dz,SNAKE_FOUNDATION)
    for sign in (-1,1):
        s.set(cx+sign*4,44,cz+7,SNAKE_LIGHT)
        s.fill((cx+sign*4,45,cz+6),(cx+sign*4,45,cz+8),SNAKE_BRICK)
        s.fill((cx+sign*2,39,cz+9),(cx+sign*2,42,cz+9),SNAKE_BRICK)


def detail_snake(s: Structure) -> None:
    for x in (46,114):
        for z in range(78,123,9):
            if z+9<=123:
                arch_rib(s,x,z+4,16,5,8,SNAKE_FOUNDATION,True)
            # Fluted shaft with stepped capital and projecting impost.
            for sign in (-1,1):
                s.fill((x+sign*2,8,z),(x+sign*2,21,z),SNAKE_LATTICE)
            s.fill((x-3,22,z-3),(x+3,23,z+3),SNAKE_FOUNDATION)
        for z in range(78,124,6):
            s.fill((x-2,30,z-1),(x+2,31,z+1),SNAKE_BRICK)
    # Main gate lintel and layered arch join the two statue plinths.
    arch_rib(s,80,127,20,18,12,SNAKE_BRICK)
    arch_rib(s,80,129,20,18,14,SNAKE_FOUNDATION)
    for x in (62,98):
        s.pillar(x,128,4,20,SNAKE_BRICK,SNAKE_FOUNDATION,2)
    for x0,x1 in ((13,39),(121,147)):
        for x in range(x0+4,x1-1,6):
            s.fill((x,7,114),(x+2,13,114),SNAKE_LATTICE)
            arch_rib(s,x+1,115,13,2,3,SNAKE_FOUNDATION)
            s.fill((x-1,4,114),(x-1,15,115),SNAKE_BRICK)
        for y in (6,17,19):
            s.fill((x0-1,y,114),(x1+1,y,115),SNAKE_FOUNDATION)
    # Recessed blind arcades and broken crenellations articulate each terrace.
    for tier in range(6):
        inset=tier*3
        front=37-inset//2
        for x in range(55+inset,107-inset,7):
            s.fill((x,5+tier*6,front-1),(x+2,8+tier*6,front-1),SNAKE_LATTICE)
            s.set(x+1,9+tier*6,front,SNAKE_CHISELED)
        for x in range(52+inset,109-inset,5):
            if (x+tier)%7:
                s.fill((x,11+tier*6,front),(x+1,12+tier*6,front),SNAKE_FOUNDATION)
    # Decorative pavement belongs to the whole courtyard, not every tile.
    for i in range(160):
        a=i*math.tau/160
        r=13+2*math.sin(3*a)
        s.set(round(80+r*math.cos(a)),3,round(56+r*math.sin(a)),SNAKE_FOUNDATION)
    # Deterministic, sparse weathering on exposed stone; never remove route cells.
    for (x,y,z),state in list(s.blocks.items()):
        if state==SNAKE_BRICK and y>6 and (x*31+y*17+z*43)%97==0:
            s.set(x,y,z,SNAKE_FOUNDATION)


def detail_mushroom(s: Structure) -> None:
    # The paved court must contrast with the organic cap; no cap skin underfoot.
    for (x,y,z),state in list(s.blocks.items()):
        if y==3 and state==FUNGAL_CAP:
            s.set(x,y,z,FUNGAL_BRICK)
    for i in range(10):
        a=math.tau*i/10
        x,z=round(88+43*math.cos(a)),round(72+43*math.sin(a))
        # Branching arches tie adjacent stems to the canopy.
        for sign in (-1,1):
            points=[]
            for t in range(40):
                spread=sign*14*(t/39)**1.6
                points.append((round(x-spread*math.sin(a)),24+t,
                               round(z+spread*math.cos(a))))
            stone_tube(s,points,2,FUNGAL_GILL)
        for y in (17+i%3,29+i%4):
            sx,sz=round(x+5*math.cos(a)),round(z+5*math.sin(a))
            s.cap_roof(sx,sz,5,4,y,3,FUNGAL_CAP,FUNGAL_GILL)
    # Visible hanging spore bunches, not a glowing stripe up each tower.
    for i in range(16):
        a=math.tau*i/16
        x,z=round(88+49*math.cos(a)),round(72+49*math.sin(a))
        y=43+i%4
        s.fill((x,y,z),(x,55,z),FUNGAL_GILL)
        for dx,dy,dz in ((0,0,0),(-2,2,0),(2,3,1),(0,5,-2)):
            stone_tube(s,[(x+dx,y+dy,z+dz)],1,FUNGAL_LIGHT)
    for cx,cz in ((27,65),(149,70),(35,128),(141,134)):
        for dx in (-8,0,8):
            arch_rib(s,cx+dx,cz+16,12,3,5,FUNGAL_FOUNDATION)
            s.fill((cx+dx-2,7,cz+15),(cx+dx+2,12,cz+15),FUNGAL_GILL)
        for dx in (-12,12):
            stone_tube(s,[(cx+dx,4+t,cz+14-round(t*t/70)) for t in range(15)],1,FUNGAL_GILL)
    # Short, irregular boundary balustrades around the processional gardens.
    for x in (70,106):
        for z in range(131,150,4):
            s.fill((x,4,z),(x,6+z%3,z),FUNGAL_FOUNDATION)
        s.fill((x,6,131),(x,6,149),FUNGAL_GILL)


def build_snake_temple() -> Structure:
    s = Structure("snake_king_temple", (160, 72, 160))
    monumental_base(s, 80, 76, 72, SNAKE_FLOOR, SNAKE_FOUNDATION, SNAKE_CHISELED)
    # A broad processional causeway passes between paired cobra pylons.
    s.fill((69, 0, 113), (91, 3, 159), SNAKE_FOUNDATION)
    s.clear((70, 4, 110), (90, 24, 159))
    for z in range(112, 160):
        s.floor(78, z, 82, z, 3, SNAKE_CHISELED)
        if z % 6 == 0:
            s.set(80, 3, z, SNAKE_LIGHT)
    for step in range(3):
        z = 157 + step
        s.floor(69, z, 91, z, 3-step, SNAKE_FOUNDATION)
        s.clear((69, 4-step, z), (91, 6, z))
    for cx in (57, 103):
        s.fill((cx - 8, 1, 123), (cx + 8, 7, 139), SNAKE_FOUNDATION)
        carved_cobra(s,cx,131)
    # Long open colonnades frame the approach, not the boss movement area.
    for x in (46, 114):
        for z in range(78, 124, 9):
            s.pillar(x, z, 4, 25, SNAKE_BRICK, SNAKE_CHISELED, 2)
        s.fill((x - 3, 25, 75), (x + 3, 27, 127), SNAKE_CHISELED)
        s.fill((x - 4, 28, 74), (x + 4, 29, 128), SNAKE_FOUNDATION)
    # Open-air circular sanctum: flat 55-diameter central combat floor.
    for r in (28, 34, 40):
        disk(s, 80, 56, r, 3, SNAKE_CHISELED, r - 1)
    for tier in range(5):
        disk(s, 80, 56, 38 + tier * 2, 4 + tier, SNAKE_BRICK, 36 + tier * 2)
    s.clear((72, 4, 81), (88, 15, 108))
    for index in range(12):
        angle = math.tau * index / 12
        x, z = round(80 + 44 * math.cos(angle)), round(56 + 44 * math.sin(angle))
        s.pillar(x, z, 4, 30, SNAKE_FOUNDATION, SNAKE_CHISELED, 2)
        s.hanging_light(x, z, 34, 2, SNAKE_LATTICE, SNAKE_LIGHT)
    # Terraced rear ziggurat with a hollow, luminous crown.
    for tier in range(7):
        inset = tier * 3
        s.fill((51 + inset, 4 + tier * 6, 8 + inset // 2),
               (109 - inset, 9 + tier * 6, 36 - inset // 2), SNAKE_BRICK)
        s.floor(50 + inset, 7 + inset // 2, 110 - inset, 37 - inset // 2,
                10 + tier * 6, SNAKE_CHISELED)
    for x in (73, 87):
        s.pillar(x, 22, 44, 63, SNAKE_FOUNDATION, SNAKE_CHISELED, 2)
    s.fill((70, 63, 19), (90, 65, 25), SNAKE_CHISELED)
    s.fill((79, 49, 21), (81, 61, 23), SNAKE_LIGHT)
    # Vaults connect laterally to the causeway through broad doorways.
    for x0, x1, door in ((13, 39, 39), (121, 147, 121)):
        s.room(x0, 75, x1, 113, 3, 18, SNAKE_FLOOR, SNAKE_BRICK, SNAKE_CHISELED)
        s.barrel_roof(x0 - 1, 74, x1 + 1, 114, 18, 10, SNAKE_BRICK, SNAKE_LATTICE)
        s.doorway_x(89, 99, door, 3, 10, 5)
        cx = (x0 + x1) // 2
        s.marker(cx, 4, 83, "snake_treasure_chest")
        s.marker(cx, 4, 106, "snake_common_chest")
        s.marker(cx, 4, 94, "snake_spawner_skeleton")
        for z in (80, 88, 100, 108):
            for x in (x0, x1):
                s.fill((x, 7, z-1), (x, 13, z+1), SNAKE_LATTICE)
                s.pillar(x, z+3, 4, 17, SNAKE_FOUNDATION, SNAKE_CHISELED)
        # Framed front niches break up the otherwise blank vault facades.
        for x in range(x0+4, x1-2, 6):
            s.fill((x, 6, 113), (x+1, 14, 113), SNAKE_LATTICE)
    # Two coiled guardians crown the lateral vaults.
    for cx in (26, 134):
        points = [(round(cx + 8 * math.cos(t / 8)), 24 + t // 6,
                   round(92 + 8 * math.sin(t / 8))) for t in range(100)]
        stone_tube(s, points, 2, SNAKE_CHISELED)
        x, y, z = points[-1]
        s.fill((x - 3, y - 1, z - 3), (x + 3, y + 3, z + 4), SNAKE_FOUNDATION)
        s.set(x - 3, y + 2, z + 3, SNAKE_LIGHT)
        s.set(x + 3, y + 2, z + 3, SNAKE_LIGHT)
    detail_snake(s)
    s.set(80, 4, 56, "virtual_craft:snake_king_altar")
    s.ensure_marker_clearance()
    return s


def build_mushroom_palace() -> Structure:
    s = Structure("mushroom_king_palace", (176, 96, 176))
    monumental_base(s, 88, 88, 82, FUNGAL_CAP, FUNGAL_FOUNDATION, FUNGAL_GILL)
    s.fill((78, 0, 144), (98, 3, 175), FUNGAL_FOUNDATION)
    s.clear((79, 4, 135), (97, 24, 175))
    for step in range(3):
        z = 173 + step
        s.floor(78, z, 98, z, 3-step, FUNGAL_GILL)
        s.clear((78, 4-step, z), (98, 6, z))
    # A colossal canopy supported by a ring of living buttress-stems.
    for i in range(10):
        a = math.tau * i / 10
        x, z = round(88 + 43 * math.cos(a)), round(72 + 43 * math.sin(a))
        s.pillar(x, z, 4, 54, FUNGAL_FOUNDATION, FUNGAL_GILL, 3)
        points = [(round(x + math.cos(a) * (1 - t / 40) * 13),
                   4 + t, round(z + math.sin(a) * (1 - t / 40) * 13)) for t in range(41)]
        stone_tube(s, points, 3, FUNGAL_GILL)
    s.cap_roof(88, 72, 61, 59, 54, 35, FUNGAL_CAP, FUNGAL_GILL)
    # Underside gills radiate overhead; lamps stay far above the fight.
    for i in range(32):
        a = math.tau * i / 32
        points = [(round(88 + r * math.cos(a)), 53 + round((1-r/59)*18),
                   round(72 + r * math.sin(a))) for r in range(12, 59)]
        stone_tube(s, points, 1, FUNGAL_GILL)
        x, y, z = points[35]
        s.hanging_light(x, z, y, 5 + i % 4, FUNGAL_GILL, FUNGAL_LIGHT)
    # Irregular pale patches on the actual cap surface, not random noise.
    for cx, cz, radius in ((69, 51, 7), (103, 83, 9), (114, 45, 5), (53, 86, 5), (83, 106, 6)):
        for x in range(cx-radius, cx+radius+1):
            for z in range(cz-radius, cz+radius+1):
                if (x-cx)**2+(z-cz)**2 <= radius**2:
                    d = ((x-88)/61)**2 + ((z-72)/59)**2
                    if d <= 1:
                        s.set(x, 54 + round(35*(1-d)**0.48), z, FUNGAL_CHISELED)
    # Four explorable radial chapels with distinct heights and smaller caps.
    for cx, cz, rise in ((27, 65, 14), (149, 70, 19), (35, 128, 11), (141, 134, 15)):
        s.octagon_room(cx-15, cz-15, cx+15, cz+15, 3, 19, 7,
                       FUNGAL_CAP, FUNGAL_BRICK, FUNGAL_GILL)
        s.cap_roof(cx, cz, 19, 19, 19, rise, FUNGAL_CAP, FUNGAL_GILL)
        s.doorway_x(cz-4, cz+4, cx+15 if cx<88 else cx-15, 3, 10, 5)
        s.marker(cx, 4, cz-7, "mushroom_treasure_chest")
        s.marker(cx, 4, cz+7, "mushroom_common_chest")
        s.marker(cx-7 if cx<88 else cx+7, 4, cz, "mushroom_spawner_spider")
        for dx in (-8, 0, 8):
            s.fill((cx+dx, 8, cz+15), (cx+dx+1, 14, cz+15), FUNGAL_GILL)
        s.hanging_light(cx, cz, 23, 8, FUNGAL_GILL, FUNGAL_LIGHT)
    # Low herbarium islands flank the processional walk without entering combat.
    for x,z in ((59,126),(116,125),(64,144),(111,146),(46,99),(132,104)):
        s.fungal_display_bed(x,z,3,True,FUNGAL_FOUNDATION,FUNGAL_GILL,FUNGAL_LIGHT)
        for dx,dz,h in ((-3,0,5),(2,1,7),(0,-2,4)):
            s.fill((x+dx,4,z+dz),(x+dx,h,z+dz),FUNGAL_GILL)
            s.cap_roof(x+dx,z+dz,2,2,h,2,FUNGAL_CAP,FUNGAL_GILL)
    # Lower satellite mushrooms form a skyline around the giant central crown.
    for cx, cz, h, r in ((30, 28, 36, 17), (141, 27, 44, 20), (22, 153, 23, 13), (155, 154, 29, 14)):
        s.pillar(cx, cz, 1, h, FUNGAL_FOUNDATION, FUNGAL_GILL, 3)
        s.cap_roof(cx, cz, r, r, h, r//2, FUNGAL_CAP, FUNGAL_GILL)
        for dy in (8,15):
            s.set(cx+3,dy,cz,FUNGAL_LIGHT)
    # Root arch announces the entrance, with a 17-block wide opening.
    for sign in (-1, 1):
        points = [(88 + sign * round(21*math.cos(t/50*math.pi/2)),
                   4 + round(30*math.sin(t/50*math.pi/2)), 153) for t in range(51)]
        stone_tube(s, points, 3, FUNGAL_GILL)
    for radius in (17, 29, 38):
        disk(s, 88, 72, radius, 3, FUNGAL_CHISELED, radius-1)
    for i in range(16):
        a = math.tau*i/16
        x, z = round(88+35*math.cos(a)), round(72+35*math.sin(a))
        s.set(x, 3, z, FUNGAL_LIGHT)
    detail_mushroom(s)
    s.set(88, 4, 72, "virtual_craft:mushroom_king_altar")
    s.ensure_marker_clearance()
    return s


def split_block_state(state: str) -> tuple[str, dict[str, str]]:
    if "[" not in state:
        return state, {}
    name, raw = state[:-1].split("[", 1)
    props = {}
    for pair in raw.split(","):
        key, value = pair.split("=", 1)
        props[key] = value
    return name, props


def nbt_name(name: str) -> bytes:
    encoded = name.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def tag_header(tag_type: int, name: str) -> bytes:
    return bytes((tag_type,)) + nbt_name(name)


def tag_int(name: str, value: int) -> bytes:
    return tag_header(3, name) + struct.pack(">i", value)


def tag_string(name: str, value: str) -> bytes:
    encoded = value.encode("utf-8")
    return tag_header(8, name) + struct.pack(">H", len(encoded)) + encoded


def tag_int_list(name: str, values: Iterable[int]) -> bytes:
    values = tuple(values)
    return tag_header(9, name) + bytes((3,)) + struct.pack(">i", len(values)) + b"".join(struct.pack(">i", v) for v in values)


def compound_payload(entries: Iterable[bytes]) -> bytes:
    return b"".join(entries) + b"\x00"


def palette_compound(state: str) -> bytes:
    name, props = split_block_state(state)
    entries = [tag_string("Name", name)]
    if props:
        prop_entries = [tag_string(key, value) for key, value in sorted(props.items())]
        entries.append(tag_header(10, "Properties") + compound_payload(prop_entries))
    return compound_payload(entries)


def block_compound(pos: tuple[int, int, int], state_index: int, marker: str | None) -> bytes:
    entries = [tag_int_list("pos", pos), tag_int("state", state_index)]
    if marker is not None:
        entries.append(
            tag_header(10, "nbt")
            + compound_payload((tag_string("mode", "DATA"), tag_string("metadata", marker)))
        )
    return compound_payload(entries)


def write_structure_nbt(structure: Structure, output: Path) -> None:
    marker_map = {marker.pos: marker.metadata for marker in structure.markers}
    palette = sorted(set(structure.blocks.values()))
    palette_index = {state: index for index, state in enumerate(palette)}
    palette_list = (
        tag_header(9, "palette")
        + bytes((10,))
        + struct.pack(">i", len(palette))
        + b"".join(palette_compound(state) for state in palette)
    )
    ordered_blocks = sorted(structure.blocks.items(), key=lambda item: (item[0][1], item[0][2], item[0][0]))
    blocks_list = (
        tag_header(9, "blocks")
        + bytes((10,))
        + struct.pack(">i", len(ordered_blocks))
        + b"".join(
            block_compound(pos, palette_index[state], marker_map.get(pos))
            for pos, state in ordered_blocks
        )
    )
    entities_list = tag_header(9, "entities") + bytes((10,)) + struct.pack(">i", 0)
    root = (
        bytes((10,))
        + nbt_name("")
        + compound_payload(
            (
                tag_int("DataVersion", DATA_VERSION),
                tag_int_list("size", (structure.width, structure.height, structure.depth)),
                palette_list,
                blocks_list,
                entities_list,
            )
        )
    )
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(gzip.compress(root, compresslevel=9, mtime=0))


COLORS = {
    SNAKE_FOUNDATION: (44, 35, 51),
    SNAKE_BRICK: (76, 42, 70),
    SNAKE_CHISELED: (54, 118, 111),
    SNAKE_FLOOR: (54, 78, 71),
    SNAKE_LATTICE: (184, 126, 31),
    SNAKE_LIGHT: (136, 232, 173),
    FUNGAL_FOUNDATION: (45, 36, 33),
    FUNGAL_BRICK: (100, 45, 66),
    FUNGAL_CHISELED: (44, 126, 121),
    FUNGAL_CAP: (121, 70, 91),
    FUNGAL_GILL: (190, 170, 139),
    FUNGAL_LIGHT: (242, 164, 75),
    "virtual_craft:snake_king_altar": (211, 166, 57),
    "virtual_craft:mushroom_king_altar": (230, 121, 82),
    "minecraft:structure_block[mode=data]": (115, 92, 135),
}


def shade(color: tuple[int, int, int], factor: float) -> tuple[int, int, int]:
    return tuple(max(0, min(255, round(channel * factor))) for channel in color)


def render_isometric(structure: Structure, output: Path, scale: int = 4) -> None:
    visible = []
    solid = {pos for pos, state in structure.blocks.items() if state != AIR}
    for (x, y, z), state in structure.blocks.items():
        if state == AIR:
            continue
        if any((x + dx, y + dy, z + dz) not in solid for dx, dy, dz in ((0, 1, 0), (1, 0, 0), (0, 0, 1))):
            visible.append((x, y, z, state))
    width = (structure.width + structure.depth) * scale + 80
    height = (structure.width + structure.depth) * scale // 2 + structure.height * scale * 2 + 100
    image = Image.new("RGBA", (width, height), (20, 23, 29, 255))
    draw = ImageDraw.Draw(image)
    origin_x = structure.depth * scale + 40
    origin_y = 35 + structure.height * scale * 2
    for x, y, z, state in sorted(visible, key=lambda item: (item[0] + item[2], item[1], item[0])):
        sx = origin_x + (x - z) * scale
        sy = origin_y + (x + z) * scale // 2 - y * scale * 2
        color = COLORS.get(state, (126, 126, 132))
        top = [(sx, sy - scale), (sx + scale, sy), (sx, sy + scale), (sx - scale, sy)]
        left = [(sx - scale, sy), (sx, sy + scale), (sx, sy + scale * 3), (sx - scale, sy + scale * 2)]
        right = [(sx + scale, sy), (sx, sy + scale), (sx, sy + scale * 3), (sx + scale, sy + scale * 2)]
        draw.polygon(left, fill=shade(color, 0.62))
        draw.polygon(right, fill=shade(color, 0.78))
        draw.polygon(top, fill=shade(color, 1.08))
    output.parent.mkdir(parents=True, exist_ok=True)
    image.save(output)


def render_floor_plan(structure: Structure, output: Path, y: int = 4, scale: int = 6) -> None:
    image = Image.new("RGBA", (structure.width * scale, structure.depth * scale), (20, 23, 29, 255))
    draw = ImageDraw.Draw(image)
    marker_positions = {marker.pos: marker.metadata for marker in structure.markers}
    for z in range(structure.depth):
        for x in range(structure.width):
            state = structure.blocks.get((x, y, z), AIR)
            if state == AIR:
                continue
            metadata = marker_positions.get((x, y, z), "")
            color = (255, 202, 74) if "chest" in metadata else COLORS.get(state, (126, 126, 132))
            draw.rectangle((x * scale, z * scale, (x + 1) * scale - 1, (z + 1) * scale - 1), fill=color)
    output.parent.mkdir(parents=True, exist_ok=True)
    image.save(output)


def render_elevations(structure: Structure, output: Path, scale: int = 4) -> None:
    def view(side: str) -> Image.Image:
        horizontal = structure.width if side in ("south", "north") else structure.depth
        image = Image.new("RGBA", (horizontal * scale, structure.height * scale), (20, 23, 29, 255))
        draw = ImageDraw.Draw(image)
        for h in range(horizontal):
            for y in range(structure.height):
                length = structure.depth if side in ("south", "north") else structure.width
                ray = range(length-1,-1,-1) if side in ("south","east") else range(length)
                state = AIR
                for depth in ray:
                    pos = (h,y,depth) if side in ("south","north") else (depth,y,h)
                    state = structure.blocks.get(pos,AIR)
                    if state != AIR:
                        break
                if state == AIR:
                    continue
                color = COLORS.get(state, (126, 126, 132))
                draw.rectangle(
                    (h * scale, (structure.height - y - 1) * scale,
                     (h + 1) * scale - 1, (structure.height - y) * scale - 1),
                    fill=color,
                )
        return image

    south, north, east, west = (view(side) for side in ("south", "north", "east", "west"))
    width = max(south.width, east.width)
    height = south.height
    sheet = Image.new("RGBA", (width * 2 + 12, height * 2 + 12), (14, 17, 22, 255))
    sheet.alpha_composite(south, (0, 0))
    sheet.alpha_composite(north, (width + 12, 0))
    sheet.alpha_composite(east, (0, height + 12))
    sheet.alpha_composite(west, (width + 12, height + 12))
    output.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(output)


def validate_structure(structure: Structure) -> None:
    if structure.width >= 160:
        validate_monument(structure)
        return
    gate = (
        (range(35, 45), range(4, 13), range(92, 96))
        if structure.name == "snake_king_temple"
        else (range(40, 48), range(4, 12), range(84, 88))
    )
    solids = {pos for pos, state in structure.blocks.items() if state != AIR}
    for x in gate[0]:
        for y in gate[1]:
            for z in gate[2]:
                solids.add((x, y, z))

    outside: set[tuple[int, int, int]] = set()
    queue: deque[tuple[int, int, int]] = deque()

    def seed(pos: tuple[int, int, int]) -> None:
        if pos not in solids and pos not in outside:
            outside.add(pos)
            queue.append(pos)

    for x in range(structure.width):
        for y in range(structure.height):
            seed((x, y, 0))
            seed((x, y, structure.depth - 1))
    for z in range(structure.depth):
        for y in range(structure.height):
            seed((0, y, z))
            seed((structure.width - 1, y, z))
    for x in range(structure.width):
        for z in range(structure.depth):
            seed((x, 0, z))
            seed((x, structure.height - 1, z))

    while queue:
        x, y, z = queue.popleft()
        for neighbor in (
            (x + 1, y, z), (x - 1, y, z),
            (x, y + 1, z), (x, y - 1, z),
            (x, y, z + 1), (x, y, z - 1),
        ):
            nx, ny, nz = neighbor
            if (
                0 <= nx < structure.width
                and 0 <= ny < structure.height
                and 0 <= nz < structure.depth
            ):
                seed(neighbor)

    leaks = sorted(
        pos for pos in structure.interior_cells
        if structure.blocks.get(pos, AIR) == AIR and pos in outside
    )
    if leaks:
        raise ValueError(
            f"{structure.name} has exterior-connected room cells: {leaks[:12]}"
        )

    blocked_chests = []
    inaccessible_chests = []
    for marker in structure.markers:
        if "chest" not in marker.metadata:
            continue
        x, y, z = marker.pos
        for clearance_y in (y + 1, y + 2):
            state = structure.blocks.get((x, clearance_y, z), AIR)
            if state != AIR:
                blocked_chests.append((marker.metadata, marker.pos, clearance_y, state))
        if not any(
            structure.blocks.get((x + dx, y, z + dz), AIR) == AIR
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
        ):
            inaccessible_chests.append((marker.metadata, marker.pos))
    if blocked_chests:
        raise ValueError(
            f"{structure.name} has blocked chests: {blocked_chests[:8]}"
        )
    if inaccessible_chests:
        raise ValueError(
            f"{structure.name} has inaccessible chests: {inaccessible_chests[:8]}"
        )


def validate_monument(s: Structure) -> None:
    snake = s.name == "snake_king_temple"
    cx, cz = (80, 56) if snake else (88, 72)
    altar = f"virtual_craft:{'snake' if snake else 'mushroom'}_king_altar"
    assert s.blocks.get((cx, 4, cz)) == altar
    assert sum(block == altar for block in s.blocks.values()) == 1
    # Verify a genuinely open battle volume, not merely a large bounding box.
    radius = 18 if snake else 28
    for x in range(cx-radius, cx+radius+1):
        for z in range(cz-radius, cz+radius+1):
            if (x-cx)**2+(z-cz)**2 > radius**2:
                continue
            assert s.blocks.get((x, 3, z), AIR) != AIR
            for y in range(4, 19):
                assert s.blocks.get((x, y, z)) == AIR or (x, y, z) == (cx, 4, cz), (x,y,z)
    # The final three rows are entrance steps from terrain up to the plinth.
    for step in range(3):
        z = s.depth-3+step
        y = 3-step
        assert s.blocks.get((cx,y,z),AIR) != AIR
        assert s.blocks.get((cx,y+1,z)) == AIR
        assert s.blocks.get((cx,y+2,z)) == AIR
    # Two-block-high walkability from the public entrance to altar and chests.
    def walkable(x: int, z: int) -> bool:
        return (s.blocks.get((x, 3, z), AIR) != AIR
                and s.blocks.get((x, 4, z)) == AIR
                and s.blocks.get((x, 5, z)) == AIR)
    start = (cx, s.depth-5)
    assert walkable(*start)
    seen = {start}
    queue = deque([start])
    while queue:
        x, z = queue.popleft()
        for p in ((x+1,z),(x-1,z),(x,z+1),(x,z-1)):
            if p not in seen and walkable(*p):
                seen.add(p)
                queue.append(p)
    for x,y,z in [(cx,4,cz)] + [m.pos for m in s.markers if "chest" in m.metadata]:
        assert any(p in seen for p in ((x+1,z),(x-1,z),(x,z+1),(x,z-1))), (x,y,z)
        assert s.blocks.get((x,y+1,z)) == AIR
    assert len({m.pos for m in s.markers}) == len(s.markers)
    print(f"  verified battle clearance and {len(seen)} connected walkable cells")


def compile_all() -> None:
    # Palette previews reflect the actual material PNGs, not obsolete hardcoded
    # purple/gold stand-ins. Pixel detail is separately shown on the tiling sheet.
    for state in COLORS:
        path = ROOT / 'src/main/resources/assets/virtual_craft/textures/block' / (state.split(':')[-1]+'.png')
        if path.exists():
            with Image.open(path) as texture:
                COLORS[state] = tuple(round(v) for v in ImageStat.Stat(texture.convert('RGB')).mean)
    for structure in (build_snake_temple(), build_mushroom_palace()):
        validate_structure(structure)
        write_structure_nbt(structure, STRUCTURE_DIR / f"{structure.name}.nbt")
        render_isometric(structure, PREVIEW_DIR / f"{structure.name}_isometric.png")
        render_floor_plan(structure, PREVIEW_DIR / f"{structure.name}_floor.png")
        render_elevations(structure, PREVIEW_DIR / f"{structure.name}_elevations.png")
        print(
            f"{structure.name}: {len(structure.blocks)} blocks, "
            f"{len(structure.markers)} markers, {structure.width}x{structure.height}x{structure.depth}"
        )


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.parse_args()
    compile_all()
