package dev.tatami.virtualcraft.network;

import dev.tatami.virtualcraft.VirtualCraft;
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
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MiningSurveyorActionPayload(String targetKey, boolean clear) implements CustomPacketPayload {
	private static final int HORIZONTAL_RANGE = 24;
	private static final int VERTICAL_RANGE = 18;
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
				return;
			}

			MiningSurveyTargetData.setTarget(stack, result.anchor(), result.clusterSize(), target);
			player.getInventory().setChanged();
			player.containerMenu.broadcastChanges();
			PacketDistributor.sendToPlayer(player, SyncMiningSurveyorPayload.lock(target, result.anchor(), result.clusterSize()));
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
