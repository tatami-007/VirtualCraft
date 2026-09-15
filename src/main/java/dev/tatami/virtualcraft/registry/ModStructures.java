package dev.tatami.virtualcraft.registry;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.world.BossRuinPiece;
import dev.tatami.virtualcraft.world.BossRuinStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModStructures {
	public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
			DeferredRegister.create(Registries.STRUCTURE_TYPE, VirtualCraft.MODID);
	public static final DeferredRegister<StructurePieceType> PIECE_TYPES =
			DeferredRegister.create(Registries.STRUCTURE_PIECE, VirtualCraft.MODID);

	public static final DeferredHolder<StructureType<?>, StructureType<BossRuinStructure>> BOSS_RUIN =
			STRUCTURE_TYPES.register("boss_ruin", () -> () -> BossRuinStructure.CODEC);
	public static final DeferredHolder<StructurePieceType, StructurePieceType> BOSS_RUIN_PIECE =
			PIECE_TYPES.register("boss_ruin",
					() -> (StructurePieceType.StructureTemplateType) BossRuinPiece::new);

	private ModStructures() {
	}
}
