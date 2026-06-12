package dev.tatami.virtualcraft.entity;

import dev.tatami.virtualcraft.registry.ModEntityTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class VirtualSheep extends Sheep {
	public VirtualSheep(EntityType<? extends Sheep> entityType, Level level) {
		super(entityType, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Sheep.createAttributes();
	}

	@Override
	public boolean readyForShearing() {
		return false;
	}

	@Override
	@Nullable
	public VirtualSheep getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
		VirtualSheep child = ModEntityTypes.VIRTUAL_SHEEP.get().create(level);
		if (child != null) {
			child.setColor(DyeColor.WHITE);
		}
		return child;
	}
}
