package dev.tatami.virtualcraft.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SnakeBossConfig {
	public static final ModConfigSpec SPEC;
	public static final Values VALUES;

	static {
		ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
		VALUES = new Values(builder);
		SPEC = builder.build();
	}

	private SnakeBossConfig() {
	}

	public static final class Values {
		public final ModConfigSpec.DoubleValue healthMultiplier;
		public final ModConfigSpec.DoubleValue damageMultiplier;
		public final ModConfigSpec.DoubleValue damageCap;
		public final ModConfigSpec.DoubleValue dpsCap;
		public final ModConfigSpec.DoubleValue rangedDamageLimit;

		private Values(ModConfigSpec.Builder builder) {
			builder.push("virtual_snake_king");
			healthMultiplier = builder.comment("Multiplier applied to the Virtual Snake King's base health.")
					.defineInRange("health_multiplier", 1.0, 0.25, 20.0);
			damageMultiplier = builder.comment("Multiplier applied to all damage dealt by the boss.")
					.defineInRange("damage_multiplier", 1.0, 0.1, 20.0);
			damageCap = builder.comment("Maximum health damage accepted from one hit before stagger vulnerability.")
					.defineInRange("damage_cap", 28.0, 1.0, 2048.0);
			dpsCap = builder.comment("Maximum damage accepted in a rolling one-second bucket.")
					.defineInRange("dps_cap", 72.0, 1.0, 4096.0);
			rangedDamageLimit = builder.comment("Full ranged damage distance. Damage fades to zero at 1.5x this range.")
					.defineInRange("ranged_damage_limit", 32.0, 4.0, 256.0);
			builder.pop();
		}
	}
}
