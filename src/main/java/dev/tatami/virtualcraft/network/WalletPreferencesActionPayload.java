package dev.tatami.virtualcraft.network;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.contract.DailyContractService;
import dev.tatami.virtualcraft.gameplay.HolographicTerminalSession;
import dev.tatami.virtualcraft.wallet.WalletPreferences;
import dev.tatami.virtualcraft.wallet.WalletService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server mutation of a player's {@link WalletPreferences}.
 * Currently the payload covers favourite shop offers and clearing the recent
 * contact list; more preference surfaces can reuse the same envelope.
 */
public record WalletPreferencesActionPayload(int menuId, Action action, String payload) implements CustomPacketPayload {
	public enum Action {
		ADD_FAVORITE,
		REMOVE_FAVORITE,
		CLEAR_FAVORITES,
		CLEAR_CONTACTS,
		CLAIM_CONTRACT
	}

	public static final Type<WalletPreferencesActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "wallet_preferences_action"));
	public static final StreamCodec<FriendlyByteBuf, WalletPreferencesActionPayload> STREAM_CODEC = StreamCodec.of(WalletPreferencesActionPayload::write, WalletPreferencesActionPayload::read);

	public WalletPreferencesActionPayload {
		if (payload == null) {
			payload = "";
		}
	}

	@Override
	public Type<WalletPreferencesActionPayload> type() {
		return TYPE;
	}

	private static WalletPreferencesActionPayload read(FriendlyByteBuf buffer) {
		return new WalletPreferencesActionPayload(
				buffer.readVarInt(),
				buffer.readEnum(Action.class),
				buffer.readUtf(64));
	}

	private static void write(FriendlyByteBuf buffer, WalletPreferencesActionPayload payload) {
		buffer.writeVarInt(payload.menuId);
		buffer.writeEnum(payload.action);
		buffer.writeUtf(payload.payload, 64);
	}

	public static void handle(WalletPreferencesActionPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> {
			if (!(context.player() instanceof ServerPlayer player) || !HolographicTerminalSession.isOpen(player, payload.menuId())) {
				return;
			}
			WalletPreferences preferences = WalletService.getPreferences(player);
			boolean changed = switch (payload.action()) {
				case ADD_FAVORITE -> preferences.setFavorite(payload.payload(), true);
				case REMOVE_FAVORITE -> preferences.setFavorite(payload.payload(), false);
				case CLEAR_FAVORITES -> {
					if (preferences.favoriteOfferIds().isEmpty()) {
						yield false;
					}
					preferences.clearFavorites();
					yield true;
				}
				case CLEAR_CONTACTS -> {
					if (preferences.recentContacts().isEmpty()) {
						yield false;
					}
					preferences.clearContacts();
					yield true;
				}
				case CLAIM_CONTRACT -> DailyContractService.claim(player, payload.payload());
			};
			if (changed) {
				if (payload.action() == Action.CLAIM_CONTRACT) {
					context.reply(new SyncWalletPayload(
							payload.menuId(),
							WalletService.getWallet(player).snapshot(),
							WalletService.walletAddress(player),
							WalletService.recentEntries(player, 32).stream().map(SyncWalletPayload.Entry::fromLedgerEntry).toList()));
				}
				context.reply(SyncWalletPreferencesPayload.fromPreferences(payload.menuId(), player));
			}
		});
	}
}
