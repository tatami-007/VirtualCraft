package dev.tatami.virtualcraft.item;

import dev.tatami.virtualcraft.gameplay.HolographicTerminalSession;
import dev.tatami.virtualcraft.network.HolographicShopSyncDispatcher;
import dev.tatami.virtualcraft.network.OpenHolographicTerminalPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Holographic Terminal — 打开全息终端 GUI 的手持物品。
 *
 * <h3>历史与改造</h3>
 * <p>旧路径调 {@code serverPlayer.openMenu(MenuProvider, ...)}，vanilla 随即以 {@code HolographicShopMenu}
 * 为后端、在客户端打开一个 {@code AbstractContainerScreen}——正是 JEI 识别为 "容器 GUI"
 * 并打 Ingredient Overlay 的 Screen 类型。为了避免 overlay，历史代码写过 {@code JeiOverlayController}
 * 用反射魔法隐藏 JEI overlay；但这背后的本质问题是 "不适合用 menu 系统的 GUI 被强套进了 menu"。</p>
 *
 * <p>新路径：只发一个 {@link OpenHolographicTerminalPayload}，客户端收到后用
 * {@code MuiModApi.openScreen(new HolographicShopFragment(...))} 打开 ModernUI 的 {@code SimpleScreen}
 * ——这是普通 {@code Screen} 而非 {@code AbstractContainerScreen}，JEI 默认不识别为容器 GUI，
 * 越不过 overlay 问题。Session ID 由 {@link HolographicTerminalSession} 管理，替代旧的 {@code containerId}
 * 做后续 ActionPayload/SyncPayload 的对账。</p>
 */
public class HolographicTerminalItem extends Item {
	public HolographicTerminalItem() {
		super(new Item.Properties().stacksTo(1));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
		ItemStack stack = player.getItemInHand(usedHand);
		if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
			int sessionId = HolographicTerminalSession.open(serverPlayer);
			PacketDistributor.sendToPlayer(serverPlayer, new OpenHolographicTerminalPayload(sessionId, /*itemMode=*/ true, BlockPos.ZERO));
			// Push initial snapshot AFTER the open packet — TCP 保序，客户端 Open 包先被处理
			// （setScreen + Fragment.onCreate），Sync 包随后到达时 Fragment 已在 currentScreen，
			// {@code fromCurrentScreen(menuId)} 能正确定位。
			HolographicShopSyncDispatcher.pushInitialSnapshot(serverPlayer);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}
}
