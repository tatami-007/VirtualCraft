package dev.tatami.virtualcraft.world;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.block.state.BlockState;
import dev.tatami.virtualcraft.registry.ModBlocks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Loads authored boss dungeons from vanilla structure templates. Geometry is
 * compiled offline so world generation only places a stable, reviewed asset.
 */
public final class BossRuinPiece extends TemplateStructurePiece {
	private static final ResourceLocation SNAKE_TEMPLATE =
			ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "boss/snake_king_temple");
	private static final ResourceLocation MUSHROOM_TEMPLATE =
			ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "boss/mushroom_king_palace");

	private static final ResourceKey<LootTable> SNAKE_COMMON_LOOT = loot("chests/snake_king_ruin_common");
	private static final ResourceKey<LootTable> SNAKE_TREASURE_LOOT = loot("chests/snake_king_ruin_treasure");
	private static final ResourceKey<LootTable> MUSHROOM_COMMON_LOOT = loot("chests/mushroom_king_ruin_common");
	private static final ResourceKey<LootTable> MUSHROOM_TREASURE_LOOT = loot("chests/mushroom_king_ruin_treasure");

	private final BossRuinStructure.Kind kind;

	public BossRuinPiece(StructureTemplateManager manager, BlockPos position,
			BossRuinStructure.Kind kind, Rotation rotation) {
		super(ModStructures.BOSS_RUIN_PIECE.get(), 0, manager, template(kind),
				template(kind).toString(), makeSettings(kind, rotation), position);
		this.kind = kind;
	}

	public BossRuinPiece(StructureTemplateManager manager, CompoundTag tag) {
		super(ModStructures.BOSS_RUIN_PIECE.get(), tag, manager,
				location -> makeSettings(BossRuinStructure.Kind.fromName(tag.getString("Kind")),
						readRotation(tag)));
		this.kind = BossRuinStructure.Kind.fromName(tag.getString("Kind"));
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		super.addAdditionalSaveData(context, tag);
		tag.putString("Kind", kind.serializedName());
		tag.putString("Rot", placeSettings.getRotation().name());
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structures,
			ChunkGenerator generator, RandomSource random, BoundingBox clip,
			ChunkPos chunk, BlockPos reference) {
		super.postProcess(level, structures, generator, random, clip, chunk, reference);
		BlockState foundation = (kind == BossRuinStructure.Kind.SNAKE
				? ModBlocks.SERPENT_STONE : ModBlocks.FUNGAL_STONE).get().defaultBlockState();
		int baseY = templatePosition.getY();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		// Clip reads and writes to this chunk. No neighbour chunk loads, and
		// never extend a pier through solid terrain into an underground cave.
		for (int x = Math.max(clip.minX(), boundingBox.minX()); x <= Math.min(clip.maxX(), boundingBox.maxX()); x++) {
			for (int z = Math.max(clip.minZ(), boundingBox.minZ()); z <= Math.min(clip.maxZ(), boundingBox.maxZ()); z++) {
				cursor.set(x, baseY, z);
				if (!level.getBlockState(cursor).is(foundation.getBlock())) continue;
				int minimum = Math.max(level.getMinBuildHeight(), baseY - BossRuinTerrainRules.MAX_FOUNDATION_DEPTH);
				for (int y = baseY - 1; y >= minimum; y--) {
					cursor.set(x, y, z);
					if (!clip.isInside(cursor)) break;
					BlockState old = level.getBlockState(cursor);
					if (!old.isAir() && old.getFluidState().isEmpty() && !old.canBeReplaced()) break;
					level.setBlock(cursor, foundation, 2);
				}
			}
		}
	}

	@Override
	protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level,
			RandomSource random, BoundingBox box) {
		if (!box.isInside(pos)) {
			return;
		}
		level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		switch (name) {
			case "snake_common_chest" -> placeChest(level, pos, random, SNAKE_COMMON_LOOT);
			case "snake_treasure_chest" -> placeChest(level, pos, random, SNAKE_TREASURE_LOOT);
			case "mushroom_common_chest" -> placeChest(level, pos, random, MUSHROOM_COMMON_LOOT);
			case "mushroom_treasure_chest" -> placeChest(level, pos, random, MUSHROOM_TREASURE_LOOT);
			case "snake_spawner_bogged" -> placeSpawner(level, pos, random, EntityType.BOGGED);
			case "snake_spawner_skeleton" -> placeSpawner(level, pos, random, EntityType.SKELETON);
			case "mushroom_spawner_spider" -> placeSpawner(level, pos, random, EntityType.CAVE_SPIDER);
			case "mushroom_spawner_slime" -> placeSpawner(level, pos, random, EntityType.SLIME);
			default -> VirtualCraft.LOGGER.warn("Unknown boss ruin data marker: {}", name);
		}
	}

	private static void placeChest(ServerLevelAccessor level, BlockPos pos, RandomSource random,
			ResourceKey<LootTable> lootTable) {
		level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
		RandomizableContainer.setBlockEntityLootTable(level, random, pos, lootTable);
	}

	private static void placeSpawner(ServerLevelAccessor level, BlockPos pos, RandomSource random,
			EntityType<?> entityType) {
		level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), 3);
		if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) {
			spawner.setEntityId(entityType, random);
			spawner.setChanged();
		}
	}

	private static StructurePlaceSettings makeSettings(BossRuinStructure.Kind kind, Rotation rotation) {
		return new StructurePlaceSettings()
				.setMirror(Mirror.NONE)
				.setRotation(rotation)
				.setRotationPivot(new BlockPos(kind.width() / 2, 0, kind.depth() / 2))
				.addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
	}

	private static Rotation readRotation(CompoundTag tag) {
		try {
			return Rotation.valueOf(tag.getString("Rot"));
		} catch (IllegalArgumentException ignored) {
			return Rotation.NONE;
		}
	}

	private static ResourceLocation template(BossRuinStructure.Kind kind) {
		return kind == BossRuinStructure.Kind.MUSHROOM ? MUSHROOM_TEMPLATE : SNAKE_TEMPLATE;
	}

	private static ResourceKey<LootTable> loot(String path) {
		return ResourceKey.create(Registries.LOOT_TABLE,
				ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, path));
	}
}
