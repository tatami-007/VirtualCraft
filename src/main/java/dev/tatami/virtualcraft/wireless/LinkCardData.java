package dev.tatami.virtualcraft.wireless;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class LinkCardData {
	private static final String OWNER_UUID = "owner_uuid";
	private static final String OWNER_NAME = "owner_name";
	private static final String CHANNEL = "channel";

	private LinkCardData() {
	}

	public static boolean hasOwner(ItemStack stack) {
		return readTag(stack).hasUUID(OWNER_UUID);
	}

	public static Optional<UUID> ownerUuid(ItemStack stack) {
		CompoundTag tag = readTag(stack);
		if (!tag.hasUUID(OWNER_UUID)) {
			return Optional.empty();
		}
		return Optional.of(tag.getUUID(OWNER_UUID));
	}

	public static String ownerName(ItemStack stack) {
		return readTag(stack).getString(OWNER_NAME);
	}

	public static int channel(ItemStack stack) {
		return Math.max(0, readTag(stack).getInt(CHANNEL));
	}

	public static void bind(ItemStack stack, UUID ownerUuid, String ownerName) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			tag.putUUID(OWNER_UUID, ownerUuid);
			tag.putString(OWNER_NAME, ownerName);
			if (!tag.contains(CHANNEL)) {
				tag.putInt(CHANNEL, 0);
			}
		});
	}

	public static void setChannel(ItemStack stack, int channel) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(CHANNEL, Math.max(0, channel)));
	}

	private static CompoundTag readTag(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
	}
}
