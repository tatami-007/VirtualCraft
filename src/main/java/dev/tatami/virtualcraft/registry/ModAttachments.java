package dev.tatami.virtualcraft.registry;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.gameplay.VirtualWorldReturnPoint;
import dev.tatami.virtualcraft.wallet.WalletBalances;
import dev.tatami.virtualcraft.wallet.WalletPreferences;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
	private ModAttachments() {
	}

	public static final DeferredRegister<AttachmentType<?>> REGISTRY = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, VirtualCraft.MODID);

	public static final DeferredHolder<AttachmentType<?>, AttachmentType<WalletBalances>> WALLET_BALANCES = REGISTRY.register("wallet_balances",
			() -> AttachmentType.serializable(WalletBalances::new).copyOnDeath().build());

	public static final DeferredHolder<AttachmentType<?>, AttachmentType<WalletPreferences>> WALLET_PREFERENCES = REGISTRY.register("wallet_preferences",
			() -> AttachmentType.serializable(WalletPreferences::new).copyOnDeath().build());

	/**
	 * Per-player snapshot of the (dimension, x, y, z, yaw, pitch) the player was standing on
	 * before they entered the Virtual World via the Computer item. {@code copyOnDeath()} keeps
	 * the return point through respawns so a player who dies inside the Virtual World still
	 * lands back at the right tile after using a Computer again.
	 */
	public static final DeferredHolder<AttachmentType<?>, AttachmentType<VirtualWorldReturnPoint>> VIRTUAL_WORLD_RETURN_POINT = REGISTRY.register("virtual_world_return_point",
			() -> AttachmentType.serializable(VirtualWorldReturnPoint::new).copyOnDeath().build());
}
