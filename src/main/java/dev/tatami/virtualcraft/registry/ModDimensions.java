package dev.tatami.virtualcraft.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

import dev.tatami.virtualcraft.VirtualCraft;

/**
 * 维度键集中管理。
 * <p>
 * 旧 1.16.5 项目里 "虚拟世界" 是一整套含群系/方块/装备/电流伤害的完整维度，
 * 现在按用户决策化简为：超平坦/虚空空间，无掉血机制，纯靠 {@code Computer}
 * 物品在 OVERWORLD ↔ VIRTUAL_WORLD 之间切换。
 * </p>
 */
public final class ModDimensions {
	private ModDimensions() {
	}

	/** 虚拟世界维度键（数据驱动注册，JSON 见 data/virtual_craft/dimension/virtual_world.json）。 */
	public static final ResourceKey<Level> VIRTUAL_WORLD = ResourceKey.create(
			Registries.DIMENSION,
			ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "virtual_world"));

	/** 虚拟世界维度类型键（JSON 见 data/virtual_craft/dimension_type/virtual_world.json）。 */
	public static final ResourceKey<DimensionType> VIRTUAL_WORLD_TYPE = ResourceKey.create(
			Registries.DIMENSION_TYPE,
			ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "virtual_world"));
}
