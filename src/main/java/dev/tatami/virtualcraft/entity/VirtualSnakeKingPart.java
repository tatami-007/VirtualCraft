package dev.tatami.virtualcraft.entity;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;

public final class VirtualSnakeKingPart extends PartEntity<VirtualSnakeKing> {
	private final String name;
	private final EntityDimensions dimensions;
	private final float damageMultiplier;

	public VirtualSnakeKingPart(VirtualSnakeKing parent, String name, float width, float height, float damageMultiplier) {
		super(parent);
		this.name = name;
		this.dimensions = EntityDimensions.scalable(width, height);
		this.damageMultiplier = damageMultiplier;
		refreshDimensions();
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean canBeCollidedWith() {
		// Keep the part pickable for combat, but exclude it from vanilla player
		// pose collision checks. The parent performs safe horizontal separation.
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void push(double x, double y, double z) {
		// Player.attack() pushes non-LivingEntity targets directly. Multipart
		// hitboxes must ignore that impulse or a weapon hit visibly displaces them.
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return !isInvulnerableTo(source) && getParent().hurt(this, source, amount * damageMultiplier);
	}

	@Override
	public boolean is(Entity entity) {
		return entity == this || entity == getParent();
	}

	@Override
	public EntityDimensions getDimensions(Pose pose) {
		return dimensions;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Nullable
	@Override
	public ItemStack getPickResult() {
		return getParent().getPickResult();
	}

	@Override
	public String toString() {
		return "VirtualSnakeKingPart[" + name + "]";
	}
}
