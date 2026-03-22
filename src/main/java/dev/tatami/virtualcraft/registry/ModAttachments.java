package dev.tatami.virtualcraft.registry;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.wallet.WalletBalances;
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
}
