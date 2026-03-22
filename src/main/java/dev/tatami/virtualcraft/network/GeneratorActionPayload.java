package dev.tatami.virtualcraft.network;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.blockentity.CurrencyGeneratorBlockEntity;
import dev.tatami.virtualcraft.menu.CurrencyGeneratorMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record GeneratorActionPayload(int menuId, Action action) implements CustomPacketPayload {
	public enum Action {
		REQUEST_SYNC,
		BIND_OWNER,
		CYCLE_MODE
	}

	public static final Type<GeneratorActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "generator_action"));
	public static final StreamCodec<FriendlyByteBuf, GeneratorActionPayload> STREAM_CODEC = StreamCodec.of(GeneratorActionPayload::write, GeneratorActionPayload::read);

	@Override
	public Type<GeneratorActionPayload> type() {
		return TYPE;
	}

	private static GeneratorActionPayload read(FriendlyByteBuf buffer) {
		return new GeneratorActionPayload(buffer.readVarInt(), buffer.readEnum(Action.class));
	}

	private static void write(FriendlyByteBuf buffer, GeneratorActionPayload payload) {
		buffer.writeVarInt(payload.menuId);
		buffer.writeEnum(payload.action);
	}

	public static void handle(GeneratorActionPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player) || !(player.containerMenu instanceof CurrencyGeneratorMenu menu) || menu.containerId != payload.menuId()) {
				return;
			}
			CurrencyGeneratorBlockEntity blockEntity = menu.getBlockEntity();
			switch (payload.action) {
				case REQUEST_SYNC -> {
				}
				case BIND_OWNER -> {
					if (blockEntity.bindOwner(player)) {
						player.displayClientMessage(Component.translatable("message.virtual_craft.generator.bound"), true);
					} else {
						player.displayClientMessage(Component.translatable("message.virtual_craft.generator.owner_locked"), true);
					}
				}
				case CYCLE_MODE -> {
					if (blockEntity.canConfigure(player)) {
						blockEntity.cycleFundingMode(player);
						player.containerMenu.broadcastChanges();
					} else {
						player.displayClientMessage(Component.translatable("message.virtual_craft.generator.owner_locked"), true);
					}
				}
			}
			context.reply(new SyncGeneratorPayload(menu.containerId, blockEntity.getOwnerName()));
		});
	}
}
