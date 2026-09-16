package dev.tatami.virtualcraft.world;

import java.util.Locale;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.tatami.virtualcraft.registry.ModStructures;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A fixed-layout boss arena generated through the structure system. The single
 * logical piece is clipped per chunk by vanilla, so the arena cannot race a
 * neighbouring feature while crossing chunk boundaries.
 */
public final class BossRuinStructure extends Structure {
	public static final MapCodec<BossRuinStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
			instance.group(
					settingsCodec(instance),
					Codec.STRING.fieldOf("kind").xmap(Kind::fromName, Kind::serializedName)
							.forGetter(BossRuinStructure::kind))
					.apply(instance, BossRuinStructure::new));

	private final Kind kind;

	public BossRuinStructure(StructureSettings settings, Kind kind) {
		super(settings);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		int centerX = chunk.getMiddleBlockX();
		int centerZ = chunk.getMiddleBlockZ();
		int surfaceY = context.chunkGenerator().getBaseHeight(centerX, centerZ,
				Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
		// The monumental roofs must fit entirely inside this dimension's height.
		if (surfaceY - 3 < context.heightAccessor().getMinBuildHeight()
				|| surfaceY - 3 + kind.height() > context.heightAccessor().getMaxBuildHeight()) {
			return Optional.empty();
		}
		BlockPos anchor = new BlockPos(centerX, surfaceY, centerZ);
		return Optional.of(new GenerationStub(anchor, builder -> {
			int x = centerX - kind.width() / 2;
			int z = centerZ - kind.depth() / 2;
			Rotation rotation = Rotation.getRandom(context.random());
			builder.addPiece(new BossRuinPiece(context.structureTemplateManager(),
					new BlockPos(x, surfaceY - 3, z), kind, rotation));
		}));
	}

	@Override
	public StructureType<?> type() {
		return ModStructures.BOSS_RUIN.get();
	}

	public enum Kind {
		SNAKE(160, 160, 72),
		MUSHROOM(176, 176, 96);

		private final int width;
		private final int depth;
		private final int height;

		Kind(int width, int depth, int height) {
			this.width = width;
			this.depth = depth;
			this.height = height;
		}

		public int width() {
			return width;
		}

		public int depth() {
			return depth;
		}

		public int height() {
			return height;
		}

		public String serializedName() {
			return name().toLowerCase(Locale.ROOT);
		}

		static Kind fromName(String name) {
			return "mushroom".equalsIgnoreCase(name) ? MUSHROOM : SNAKE;
		}
	}
}
