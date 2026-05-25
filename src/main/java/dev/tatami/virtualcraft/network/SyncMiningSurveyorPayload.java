package dev.tatami.virtualcraft.network;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.mining.MiningSurveyTarget;
import dev.tatami.virtualcraft.mining.MiningSurveyTargetData;
import dev.tatami.virtualcraft.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncMiningSurveyorPayload(String targetKey, boolean locked, BlockPos pos, int clusterSize) implements CustomPacketPayload {
	public static final Type<SyncMiningSurveyorPayload> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "sync_mining_surveyor"));

	public static final StreamCodec<FriendlyByteBuf, SyncMiningSurveyorPayload> STREAM_CODEC =
			StreamCodec.of(SyncMiningSurveyorPayload::write, SyncMiningSurveyorPayload::read);

	public static SyncMiningSurveyorPayload clear(String targetKey) {
		return new SyncMiningSurveyorPayload(targetKey, false, BlockPos.ZERO, 0);
	}

	public static SyncMiningSurveyorPayload lock(MiningSurveyTarget target, BlockPos pos, int clusterSize) {
		return new SyncMiningSurveyorPayload(target.key(), true, pos, clusterSize);
	}

	@Override
	public Type<SyncMiningSurveyorPayload> type() {
		return TYPE;
	}

	private static SyncMiningSurveyorPayload read(FriendlyByteBuf buf) {
		String key = buf.readUtf(128);
		boolean locked = buf.readBoolean();
		BlockPos pos = locked ? buf.readBlockPos() : BlockPos.ZERO;
		int clusterSize = locked ? buf.readVarInt() : 0;
		return new SyncMiningSurveyorPayload(key, locked, pos, clusterSize);
	}

	private static void write(FriendlyByteBuf buf, SyncMiningSurveyorPayload payload) {
		buf.writeUtf(payload.targetKey, 128);
		buf.writeBoolean(payload.locked);
		if (payload.locked) {
			buf.writeBlockPos(payload.pos);
			buf.writeVarInt(payload.clusterSize);
		}
	}

	public static void handle(SyncMiningSurveyorPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> ClientHooks.apply(payload));
	}

	@OnlyIn(Dist.CLIENT)
	private static final class ClientHooks {
		private static void apply(SyncMiningSurveyorPayload payload) {
			MiningSurveyTarget target = MiningSurveyTarget.byKey(payload.targetKey()).orElse(MiningSurveyTarget.defaultTarget());
			Player player = net.minecraft.client.Minecraft.getInstance().player;
			if (player != null) {
				ItemStack stack = resolveSurveyor(player);
				if (!stack.isEmpty()) {
					MiningSurveyTargetData.setSelectedTarget(stack, target);
					if (payload.locked()) {
						MiningSurveyTargetData.setTarget(stack, payload.pos(), payload.clusterSize(), target);
					} else {
						MiningSurveyTargetData.clearLock(stack);
					}
				}
			}
			if (payload.locked()) {
				dev.tatami.virtualcraft.client.highlight.MiningSurveyorClientState.lock(payload.pos(), target, payload.clusterSize());
			} else {
				dev.tatami.virtualcraft.client.highlight.MiningSurveyorClientState.clear();
			}
		}

		private static ItemStack resolveSurveyor(Player player) {
			if (player.getMainHandItem().is(ModItems.MINING_SURVEYOR.get())) {
				return player.getMainHandItem();
			}
			if (player.getOffhandItem().is(ModItems.MINING_SURVEYOR.get())) {
				return player.getOffhandItem();
			}
			return ItemStack.EMPTY;
		}
	}
}
