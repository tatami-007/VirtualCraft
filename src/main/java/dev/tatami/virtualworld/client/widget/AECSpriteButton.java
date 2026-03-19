package dev.tatami.virtualworld.client.widget;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import dev.tatami.virtualworld.VirtualWorld;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class AECSpriteButton extends Button {
	public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(VirtualWorld.MODID, "textures/gui/icons.png");
	private static final int TEXTURE_SIZE = 256;

	public record Sprite(int u, int v, int width, int height) {
	}

	private final Supplier<Sprite> normalBackground;
	private final Supplier<Sprite> hoverBackground;
	private final Supplier<@Nullable Sprite> icon;
	private final Supplier<Component> label;

	public AECSpriteButton(int x, int y, OnPress onPress, Supplier<Sprite> normalBackground, Supplier<Sprite> hoverBackground,
			Supplier<@Nullable Sprite> icon, Supplier<Component> label) {
		super(x, y, 16, 18, Component.empty(), onPress, DEFAULT_NARRATION);
		this.normalBackground = normalBackground;
		this.hoverBackground = hoverBackground;
		this.icon = icon;
		this.label = label;
	}

	@Override
	protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		Sprite background = isHoveredOrFocused() ? hoverBackground.get() : normalBackground.get();
		guiGraphics.blit(TEXTURE, getX() - 1, getY(), background.u(), background.v(), background.width(), background.height(), TEXTURE_SIZE, TEXTURE_SIZE);

		Sprite iconSprite = icon.get();
		if (iconSprite != null) {
			guiGraphics.blit(TEXTURE, getX(), getY() + 1, iconSprite.u(), iconSprite.v(), iconSprite.width(), iconSprite.height(), TEXTURE_SIZE, TEXTURE_SIZE);
		}

		Component text = label.get();
		if (!text.getString().isEmpty()) {
			int textWidth = MinecraftAccessor.font().width(text);
			int textX = getX() + 8 - textWidth / 2;
			guiGraphics.drawString(MinecraftAccessor.font(), text, textX, getY() + 6, 0xF7F7FF, false);
		}
	}

	private static final class MinecraftAccessor {
		private static net.minecraft.client.gui.Font font() {
			return net.minecraft.client.Minecraft.getInstance().font;
		}
	}
}
