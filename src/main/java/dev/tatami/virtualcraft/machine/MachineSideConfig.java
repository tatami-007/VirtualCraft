package dev.tatami.virtualcraft.machine;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

import java.util.EnumMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class MachineSideConfig {
	public static final Direction[] DATA_DIRECTIONS = {
			Direction.UP,
			Direction.DOWN,
			Direction.NORTH,
			Direction.SOUTH,
			Direction.WEST,
			Direction.EAST
	};

	private static final String SIDE_MODES_TAG = "side_modes";

	private MachineSideConfig() {
	}

	public static ContainerData createData(
			EnumMap<Direction, SideMode> sideModes,
			BooleanSupplier autoImport,
			Consumer<Boolean> setAutoImport,
			BooleanSupplier autoExport,
			Consumer<Boolean> setAutoExport) {
		return new SimpleContainerData(8) {
			@Override
			public int get(int index) {
				if (index >= 0 && index < DATA_DIRECTIONS.length) {
					return sideModes.get(DATA_DIRECTIONS[index]).ordinal();
				}
				return switch (index) {
					case 6 -> autoImport.getAsBoolean() ? 1 : 0;
					case 7 -> autoExport.getAsBoolean() ? 1 : 0;
					default -> 0;
				};
			}

			@Override
			public void set(int index, int value) {
				if (index >= 0 && index < DATA_DIRECTIONS.length) {
					sideModes.put(DATA_DIRECTIONS[index], sideModeByOrdinal(value));
				} else if (index == 6) {
					setAutoImport.accept(value != 0);
				} else if (index == 7) {
					setAutoExport.accept(value != 0);
				}
			}
		};
	}

	public static void initDefaults(EnumMap<Direction, SideMode> sideModes) {
		for (Direction direction : DATA_DIRECTIONS) {
			sideModes.put(direction, SideMode.BOTH);
		}
	}

	public static void save(CompoundTag tag, EnumMap<Direction, SideMode> sideModes) {
		CompoundTag sideTag = new CompoundTag();
		for (Direction direction : DATA_DIRECTIONS) {
			sideTag.putString(direction.getName(), sideModes.getOrDefault(direction, SideMode.BOTH).name());
		}
		tag.put(SIDE_MODES_TAG, sideTag);
	}

	public static void load(CompoundTag tag, EnumMap<Direction, SideMode> sideModes) {
		initDefaults(sideModes);
		if (!tag.contains(SIDE_MODES_TAG)) {
			return;
		}

		CompoundTag sideTag = tag.getCompound(SIDE_MODES_TAG);
		for (Direction direction : DATA_DIRECTIONS) {
			if (sideTag.contains(direction.getName())) {
				try {
					sideModes.put(direction, SideMode.valueOf(sideTag.getString(direction.getName())));
				} catch (IllegalArgumentException ignored) {
				}
			}
		}
	}

	private static SideMode sideModeByOrdinal(int value) {
		SideMode[] modes = SideMode.values();
		return modes[Math.max(0, Math.min(modes.length - 1, value))];
	}
}
