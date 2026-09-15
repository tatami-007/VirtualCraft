package dev.tatami.virtualcraft.item;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Vanilla-rendered armor with a virtual-metal set identity. */
public final class VirtualMetalArmorItem extends ArmorItem {
	public enum Style {
		IRON("iron"),
		SILVER("silver"),
		TITANIUM("titanium"),
		CHROMIUM("chromium"),
		SNAKE_KING("snake_king"),
		MUSHROOM_KING("mushroom_king");

		private final String id;

		Style(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		public static Style fromItemName(String name) {
			for (Style style : values()) {
				if (name.startsWith("virtual_" + style.id + "_")) {
					return style;
				}
			}
			throw new IllegalArgumentException("Unknown virtual armor style: " + name);
		}
	}

	private final Style style;

	public VirtualMetalArmorItem(Holder<ArmorMaterial> material, Type type, Properties properties, Style style) {
		super(material, type, properties);
		this.style = style;
	}

	public Style style() {
		return style;
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
		super.inventoryTick(stack, level, entity, slotId, isSelected);
		if (getType() == Type.CHESTPLATE && entity instanceof LivingEntity wearer) {
			VirtualArmorSetEffects.tick(wearer, style);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context,
			List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.virtual_craft.armor." + style.id()));
	}
}
