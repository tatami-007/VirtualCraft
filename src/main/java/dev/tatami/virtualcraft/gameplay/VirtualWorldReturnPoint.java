package dev.tatami.virtualcraft.gameplay;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;

public record VirtualWorldReturnPoint(ResourceKey<Level> dimension, double x, double y, double z, float yaw,
		float pitch) {

	public static final Codec<VirtualWorldReturnPoint> CODEC = RecordCodecBuilder.create(i -> i
			.group(Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(VirtualWorldReturnPoint::dimension),
					Codec.DOUBLE.fieldOf("x").forGetter(VirtualWorldReturnPoint::x),
					Codec.DOUBLE.fieldOf("y").forGetter(VirtualWorldReturnPoint::y),
					Codec.DOUBLE.fieldOf("z").forGetter(VirtualWorldReturnPoint::z),
					Codec.FLOAT.fieldOf("yaw").forGetter(VirtualWorldReturnPoint::yaw),
					Codec.FLOAT.fieldOf("pitch").forGetter(VirtualWorldReturnPoint::pitch))
			.apply(i, VirtualWorldReturnPoint::new));

	public VirtualWorldReturnPoint() {
		this(Level.OVERWORLD, 0, 64, 0, 0, 0);
	}
}
