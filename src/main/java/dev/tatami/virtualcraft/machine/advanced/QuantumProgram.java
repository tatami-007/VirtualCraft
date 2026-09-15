package dev.tatami.virtualcraft.machine.advanced;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

public enum QuantumProgram implements StringRepresentable {
	DEEP_ORE("deep_ore"),
	RELIC_TOPOLOGY("relic_topology"),
	STORM_PATH("storm_path");

	public static final Codec<QuantumProgram> CODEC = StringRepresentable.fromEnum(QuantumProgram::values);

	private final String id;

	QuantumProgram(String id) {
		this.id = id;
	}

	@Override
	public String getSerializedName() {
		return id;
	}

	public static QuantumProgram byIndex(int index) {
		QuantumProgram[] values = values();
		return values[Math.floorMod(index, values.length)];
	}
}
