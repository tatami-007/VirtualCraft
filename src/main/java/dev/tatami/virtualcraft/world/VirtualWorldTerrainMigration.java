package dev.tatami.virtualcraft.world;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.registry.ModDimensions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

/** Clears stale virtual-world chunks once when the terrain schema changes. */
public final class VirtualWorldTerrainMigration {
	// Bump whenever structure NBT, placement rules or virtual-world terrain changes.
	private static final String TERRAIN_VERSION = "30-ruin-galleries-weathered-footprints";
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
			Path expected = worldRoot.resolve("dimensions/virtual_craft/virtual_world").normalize();
			if (!dimension.equals(expected)) {
				throw new IOException("Refusing to clear unexpected virtual-world path: " + dimension);
			}

			// Moving the offline dimension removes stale chunks, entities and POIs
			// from active storage together, but permits recovery of player builds.
			if (Files.exists(dimension)) {
				Path realWorld = worldRoot.toRealPath();
				if (!dimension.toRealPath().equals(realWorld.resolve("dimensions/virtual_craft/virtual_world"))) {
					throw new IOException("Refusing to reset a redirected dimension path: " + dimension);
				}
				Path backupRoot = worldRoot.resolve("virtual_craft_dimension_backups");
				Files.createDirectories(backupRoot);
				if (!backupRoot.toRealPath().equals(realWorld.resolve("virtual_craft_dimension_backups"))) {
					throw new IOException("Refusing to use redirected backup path: " + backupRoot);
				}
				Path backup = Files.createTempDirectory(backupRoot,
						"before-" + TERRAIN_VERSION + "-" + Instant.now().toEpochMilli() + "-")
						.resolve("virtual_world");
				Files.move(dimension, backup);
				VirtualCraft.LOGGER.info("Backed up old virtual-world dimension to {}", backup);
			}
			Files.createDirectories(marker.getParent());
			Files.writeString(marker, TERRAIN_VERSION, StandardCharsets.UTF_8);
			VirtualCraft.LOGGER.info("Reset stale virtual-world chunks for terrain version {}", TERRAIN_VERSION);
		} catch (IOException exception) {
			throw new IllegalStateException("Failed to migrate virtual-world terrain", exception);
		}
	}

}
