package dev.tatami.virtualcraft.network;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.client.screen.HolographicShopScreen;
import dev.tatami.virtualcraft.wallet.WalletLedgerEntry;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record SyncWalletPayload(int menuId, long[] balances, String walletAddress, List<Entry> entries) implements CustomPacketPayload {
	public record Entry(String typeId, String primaryCurrencyId, long primaryAmountMinor, String secondaryCurrencyId, long secondaryAmountMinor, String detail, long timestampSeconds) {
		private static Entry read(FriendlyByteBuf buffer) {
			return new Entry(
					buffer.readUtf(32),
					buffer.readUtf(16),
					buffer.readVarLong(),
					buffer.readUtf(16),
					buffer.readVarLong(),
					buffer.readUtf(128),
					buffer.readVarLong());
		}

		private static void write(FriendlyByteBuf buffer, Entry entry) {
			buffer.writeUtf(entry.typeId, 32);
			buffer.writeUtf(entry.primaryCurrencyId, 16);
			buffer.writeVarLong(entry.primaryAmountMinor);
			buffer.writeUtf(entry.secondaryCurrencyId, 16);
			buffer.writeVarLong(entry.secondaryAmountMinor);
			buffer.writeUtf(entry.detail, 128);
			buffer.writeVarLong(entry.timestampSeconds);
		}

		public static Entry fromLedgerEntry(WalletLedgerEntry entry) {
			return new Entry(
					entry.type().id(),
					entry.primaryCurrencyId(),
					entry.primaryAmountMinor(),
					entry.secondaryCurrencyId(),
					entry.secondaryAmountMinor(),
					entry.detail(),
					entry.timestampSeconds());
		}
	}

	public static final Type<SyncWalletPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "sync_wallet"));
	public static final StreamCodec<FriendlyByteBuf, SyncWalletPayload> STREAM_CODEC = StreamCodec.of(SyncWalletPayload::write, SyncWalletPayload::read);

	@Override
	public Type<SyncWalletPayload> type() {
		return TYPE;
	}

	private static SyncWalletPayload read(FriendlyByteBuf buffer) {
		int menuId = buffer.readVarInt();
		int size = buffer.readVarInt();
		long[] balances = new long[size];
		for (int index = 0; index < size; index++) {
			balances[index] = buffer.readVarLong();
		}
		String walletAddress = buffer.readUtf(32);
		int entryCount = buffer.readVarInt();
		List<Entry> entries = new ArrayList<>(entryCount);
		for (int index = 0; index < entryCount; index++) {
			entries.add(Entry.read(buffer));
		}
		return new SyncWalletPayload(menuId, balances, walletAddress, entries);
	}

	private static void write(FriendlyByteBuf buffer, SyncWalletPayload payload) {
		buffer.writeVarInt(payload.menuId);
		buffer.writeVarInt(payload.balances.length);
		for (long balance : payload.balances) {
			buffer.writeVarLong(balance);
		}
		buffer.writeUtf(payload.walletAddress, 32);
		buffer.writeVarInt(payload.entries.size());
		for (Entry entry : payload.entries) {
			Entry.write(buffer, entry);
		}
	}

	public static void handle(SyncWalletPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> ClientHooks.handle(payload));
	}

	@OnlyIn(Dist.CLIENT)
	private static final class ClientHooks {
		private static void handle(SyncWalletPayload payload) {
			if (Minecraft.getInstance().screen instanceof HolographicShopScreen screen && screen.matchesMenu(payload.menuId())) {
				screen.applyWalletSync(payload.balances(), payload.walletAddress(), payload.entries());
			}
		}
	}
}
