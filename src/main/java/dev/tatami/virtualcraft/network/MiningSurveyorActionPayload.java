package dev.tatami.virtualcraft.network;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.contract.DailyContractService;
import dev.tatami.virtualcraft.contract.DailyContractType;
import dev.tatami.virtualcraft.mining.MiningSurveyTarget;
import dev.tatami.virtualcraft.mining.MiningSurveyTargetData;
import dev.tatami.virtualcraft.registry.ModItems;
import dev.tatami.virtualcraft.util.OreClusterFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MiningSurveyorActionPayload(String targetKey, boolean clear) implements CustomPacketPayload {
	// Range reduced from 24/18 to 16/12: 49×49×37=~88k cells → 33×33×25=~27k cells
	// (~62% less work per click). Players don't naturally hunt veins 24 blocks
	// out — the surveyor is a "what's near me?" tool, so a 16-block horizontal
	// reach matches the actual ergonomic use case. 12 vertical still covers
	// y-level±12, more than enough to span a single ore-vein layer.
	private static final int HORIZONTAL_RANGE = 16;
	private static final int VERTICAL_RANGE = 12;
	private static final int MAX_CLUSTER_SIZE = 128;

	public static final Type<MiningSurveyorActionPayload> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "mining_surveyor_action"));

	public static final StreamCodec<FriendlyByteBuf, MiningSurveyorActionPayload> STREAM_CODEC =
			StreamCodec.of(MiningSurveyorActionPayload::write, MiningSurveyorActionPayload::read);

	@Override
	public Type<MiningSurveyorActionPayload> type() {
		return TYPE;
	}

	private static MiningSurveyorActionPayload read(FriendlyByteBuf buf) {
		return new MiningSurveyorActionPayload(buf.readUtf(128), buf.readBoolean());
	}

	private static void write(FriendlyByteBuf buf, MiningSurveyorActionPayload payload) {
		buf.writeUtf(payload.targetKey, 128);
		buf.writeBoolean(payload.clear);
	}

	public static void handle(MiningSurveyorActionPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player)) {
				return;
			}

			ItemStack stack = resolveSurveyor(player);
			if (stack.isEmpty()) {
				return;
			}

			if (payload.clear()) {
				MiningSurveyTargetData.clearLock(stack);
				player.getInventory().setChanged();
				player.containerMenu.broadcastChanges();
				PacketDistributor.sendToPlayer(player, SyncMiningSurveyorPayload.clear(MiningSurveyTargetData.selectedTarget(stack).key()));
				player.displayClientMessage(Component.translatable("message.virtual_craft.mining_surveyor.clear"), true);
				return;
			}

			MiningSurveyTarget target = MiningSurveyTarget.byKey(payload.targetKey()).orElse(MiningSurveyTarget.defaultTarget());
			MiningSurveyTargetData.setSelectedTarget(stack, target);
			OreClusterFinder.SurveyResult result = OreClusterFinder.findSurveyTarget(player.level(), player.blockPosition(), HORIZONTAL_RANGE, VERTICAL_RANGE, MAX_CLUSTER_SIZE, target);
			if (result == null) {
				MiningSurveyTargetData.clearLock(stack);
				player.getInventory().setChanged();
				player.containerMenu.broadcastChanges();
				PacketDistributor.sendToPlayer(player, SyncMiningSurveyorPayload.clear(target.key()));
				player.displayClientMessage(Component.translatable("message.virtual_craft.mining_surveyor.none", target.name()), true);
				player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.6F, 0.7F);
				return;
			}

			MiningSurveyTargetData.setTarget(stack, result.anchor(), result.clusterSize(), target);
			player.getInventory().setChanged();
			player.containerMenu.broadcastChanges();
			PacketDistributor.sendToPlayer(player, SyncMiningSurveyorPayload.lock(target, result.anchor(), result.clusterSize()));
			DailyContractService.recordProgress(player, DailyContractType.SURVEY_VEIN, 1L);
			player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 1.5F);
			BlockPos pos = result.anchor();
			player.displayClientMessage(Component.translatable("message.virtual_craft.mining_surveyor.found", target.name(), result.clusterSize(), pos.getX(), pos.getY(), pos.getZ()), true);
		});
	}

	private static ItemStack resolveSurveyor(ServerPlayer player) {
		if (player.getMainHandItem().is(ModItems.MINING_SURVEYOR.get())) {
			return player.getMainHandItem();
		}
		if (player.getOffhandItem().is(ModItems.MINING_SURVEYOR.get())) {
			return player.getOffhandItem();
		}
		return ItemStack.EMPTY;
	}
}
