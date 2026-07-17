package dev.tatami.virtualcraft.world;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.registry.ModDimensions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

/** Clears stale virtual-world chunks once when the terrain schema changes. */
public final class VirtualWorldTerrainMigration {
	private static final String TERRAIN_VERSION = "6-virtual-iron-ore";
	private static final String MARKER_FILE = "virtual_craft_virtual_world_terrain.version";

	private VirtualWorldTerrainMigration() {
	}

	public static void migrateIfNeeded(MinecraftServer server) {
		Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
		Path marker = worldRoot.resolve("data").resolve(MARKER_FILE);
		try {
			if (Files.isRegularFile(marker)
					&& TERRAIN_VERSION.equals(Files.readString(marker, StandardCharsets.UTF_8).trim())) {
				return;
			}

			Path dimension = DimensionType.getStorageFolder(ModDimensions.VIRTUAL_WORLD_LEVEL, worldRoot)
					.toAbsolutePath().normalize();
			Path dimensionsRoot = worldRoot.resolve("dimensions").toAbsolutePath().normalize();
			if (!dimension.startsWith(dimensionsRoot) || dimension.equals(dimensionsRoot)) {
				throw new IOException("Refusing to clear unexpected virtual-world path: " + dimension);
			}

			deleteTree(dimension);
			Files.createDirectories(marker.getParent());
			Files.writeString(marker, TERRAIN_VERSION, StandardCharsets.UTF_8);
			VirtualCraft.LOGGER.info("Reset stale virtual-world chunks for terrain version {}", TERRAIN_VERSION);
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to migrate virtual-world terrain", exception);
		}
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root)) {
			return;
		}
		try (var paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		}
	}
}
