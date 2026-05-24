package dev.tatami.virtualcraft.blockentity;

import dev.tatami.virtualcraft.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 全息商店方块实体。
 *
 * <p>历史上这个类同时 {@code implements MenuProvider}，为的是在 {@code HolographicShopBlock.useWithoutItem}
 * 里调 {@code openMenu(provider, ...)} 时能返回 {@code HolographicShopMenu}。重构后 GUI 不再走 menu
 * 系统（完全退出 AbstractContainerScreen 路径、避免 JEI 识别），本类不再需要 MenuProvider 职责，
 * 退化为纯标识型 BlockEntity：仅表明该方块位置上是 "全息商店"，以便
 * {@code HolographicShopBlock.useWithoutItem} 用 {@code instanceof} 过滤、能被未来能量/交互逻辑引用。</p>
 */
public class HolographicShopBlockEntity extends BlockEntity {
	public HolographicShopBlockEntity(BlockPos pos, BlockState blockState) {
		super(ModBlockEntities.HOLOGRAPHIC_SHOP.get(), pos, blockState);
	}
}
