package dev.tatami.virtualcraft.registry;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.entity.VirtualSnakeKing;
import dev.tatami.virtualcraft.entity.VirtualMushroomKing;
import dev.tatami.virtualcraft.entity.SnakeFangProjectile;
import dev.tatami.virtualcraft.entity.SnakeSpellNode;
import dev.tatami.virtualcraft.entity.MushroomRootWaveEntity;
import dev.tatami.virtualcraft.entity.MushroomSporeMassEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntityTypes {
	private ModEntityTypes() {
	}

	public static final DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister.create(Registries.ENTITY_TYPE, VirtualCraft.MODID);
	public static final DeferredHolder<EntityType<?>, EntityType<VirtualSnakeKing>> VIRTUAL_SNAKE_KING = REGISTRY.register(
			"virtual_snake_king",
			() -> EntityType.Builder.of(VirtualSnakeKing::new, MobCategory.MONSTER)
					.sized(1.55F, 1.5F)
					.eyeHeight(1.3F)
					.fireImmune()
					.clientTrackingRange(14)
					.updateInterval(1)
					.build(VirtualCraft.MODID + ":virtual_snake_king"));
	public static final DeferredHolder<EntityType<?>, EntityType<VirtualMushroomKing>> VIRTUAL_MUSHROOM_KING = REGISTRY.register(
			"virtual_mushroom_king",
			() -> EntityType.Builder.of(VirtualMushroomKing::new, MobCategory.MONSTER)
					.sized(3.0F, 5.25F)
					.eyeHeight(4.55F)
					.fireImmune()
					.clientTrackingRange(16)
					.updateInterval(1)
					.build(VirtualCraft.MODID + ":virtual_mushroom_king"));
	public static final DeferredHolder<EntityType<?>, EntityType<SnakeFangProjectile>> SNAKE_FANG = REGISTRY.register(
			"snake_fang",
			() -> EntityType.Builder.<SnakeFangProjectile>of(SnakeFangProjectile::new, MobCategory.MISC)
					.sized(0.45F, 0.45F)
					.clientTrackingRange(12)
					.updateInterval(1)
					.build(VirtualCraft.MODID + ":snake_fang"));
	public static final DeferredHolder<EntityType<?>, EntityType<SnakeSpellNode>> SNAKE_SPELL_NODE = REGISTRY.register(
			"snake_spell_node",
			() -> EntityType.Builder.<SnakeSpellNode>of(SnakeSpellNode::new, MobCategory.MISC)
					.sized(0.1F, 0.1F)
					.clientTrackingRange(16)
					.updateInterval(2)
					.build(VirtualCraft.MODID + ":snake_spell_node"));
	public static final DeferredHolder<EntityType<?>, EntityType<MushroomSporeMassEntity>> MUSHROOM_SPORE_MASS =
			REGISTRY.register("mushroom_spore_mass",
					() -> EntityType.Builder.<MushroomSporeMassEntity>of(
									MushroomSporeMassEntity::new, MobCategory.MISC)
							.sized(1.5F, 1.5F)
							.clientTrackingRange(16)
							.updateInterval(1)
							.build(VirtualCraft.MODID + ":mushroom_spore_mass"));
	public static final DeferredHolder<EntityType<?>, EntityType<MushroomRootWaveEntity>> MUSHROOM_ROOT_WAVE =
			REGISTRY.register("mushroom_root_wave",
					() -> EntityType.Builder.<MushroomRootWaveEntity>of(
									MushroomRootWaveEntity::new, MobCategory.MISC)
							.sized(1.2F, 1.4F)
							.clientTrackingRange(16)
							.updateInterval(1)
							.build(VirtualCraft.MODID + ":mushroom_root_wave"));

	public static void registerAttributes(EntityAttributeCreationEvent event) {
		event.put(VIRTUAL_SNAKE_KING.get(), VirtualSnakeKing.createAttributes().build());
		event.put(VIRTUAL_MUSHROOM_KING.get(), VirtualMushroomKing.createAttributes().build());
	}
}
