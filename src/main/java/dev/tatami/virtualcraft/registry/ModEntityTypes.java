package dev.tatami.virtualcraft.registry;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.entity.VirtualSnakeKing;
import dev.tatami.virtualcraft.entity.SnakeFangProjectile;
import dev.tatami.virtualcraft.entity.SnakeSpellNode;
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
					.sized(2.6F * VirtualSnakeKing.GAMEPLAY_SCALE, 2.2F * VirtualSnakeKing.GAMEPLAY_SCALE)
					.eyeHeight(1.75F * VirtualSnakeKing.GAMEPLAY_SCALE)
					.fireImmune()
					.clientTrackingRange(14)
					.updateInterval(1)
					.build(VirtualCraft.MODID + ":virtual_snake_king"));
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

	public static void registerAttributes(EntityAttributeCreationEvent event) {
		event.put(VIRTUAL_SNAKE_KING.get(), VirtualSnakeKing.createAttributes().build());
	}
}
