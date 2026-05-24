package dev.tatami.virtualcraft.gameplay;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * Per-player attachment that remembers <em>where the player came from</em> right
 * before stepping into the Virtual World, so that a Sneak+right-click exit on the
 * {@code Computer} item teleports them back to the exact tile/yaw/pitch they left
 * — not the world spawn (which the previous, stateless implementation defaulted to).
 *
 * <p>The capture happens at the moment the player triggers the entrance side of
 * {@code ComputerItem.use(...)}. It is overwritten on every entrance, so chaining
 * "main world → virtual → main world (move) → virtual" keeps the freshest return
 * coordinate. The {@link #valid} flag distinguishes "never captured" (e.g. a
 * fresh player or a save migrated from before this feature) from a real,
 * persisted return point — the exit path falls back to the overworld spawn only
 * in the truly-no-data case.</p>
 */
public final class VirtualWorldReturnPoint implements INBTSerializable<CompoundTag> {
	private boolean valid = false;
	private ResourceKey<Level> dimension = Level.OVERWORLD;
	private double x;
	private double y;
	private double z;
	private float yRot;
	private float xRot;

	public boolean isValid() {
		return valid;
	}

	public ResourceKey<Level> dimension() {
		return dimension;
	}

	public Vec3 position() {
		return new Vec3(x, y, z);
	}

	public float yRot() {
		return yRot;
	}

	public float xRot() {
		return xRot;
	}

	/** Snapshot the player's current position so the exit code can teleport them back exactly here. */
	public void capture(ResourceKey<Level> dim, Vec3 pos, float yaw, float pitch) {
		this.valid = true;
		this.dimension = dim != null ? dim : Level.OVERWORLD;
		this.x = pos.x;
		this.y = pos.y;
		this.z = pos.z;
		this.yRot = yaw;
		this.xRot = pitch;
	}

	/** Mark the return point as consumed — used after a successful exit so we do not teleport twice to the same spot. */
	public void clear() {
		this.valid = false;
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("valid", valid);
		tag.putString("dim", dimension.location().toString());
		tag.putDouble("x", x);
		tag.putDouble("y", y);
		tag.putDouble("z", z);
		tag.putFloat("yRot", yRot);
		tag.putFloat("xRot", xRot);
		return tag;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
		valid = nbt.getBoolean("valid");
		if (nbt.contains("dim")) {
			String dimId = nbt.getString("dim");
			ResourceLocation parsed = ResourceLocation.tryParse(dimId);
			// Defensive parse: a corrupted / hand-edited save should never crash login —
			// fall back to overworld so the player still has a reachable home.
			dimension = parsed != null
					? ResourceKey.create(Registries.DIMENSION, parsed)
					: Level.OVERWORLD;
		}
		x = nbt.getDouble("x");
		y = nbt.getDouble("y");
		z = nbt.getDouble("z");
		yRot = nbt.getFloat("yRot");
		xRot = nbt.getFloat("xRot");
	}
}
