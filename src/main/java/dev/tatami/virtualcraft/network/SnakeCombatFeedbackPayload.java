package dev.tatami.virtualcraft.network;

import dev.tatami.virtualcraft.VirtualCraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SnakeCombatFeedbackPayload(int bossEntityId, int kind, float strength)
		implements CustomPacketPayload {
	public static final int HIT = 0;
	public static final int PARRY = 1;
	public static final int SHIELD_BREAK = 2;
	public static final int POSTURE_BREAK = 3;
	public static final int HIGH_DODGE = 4;
	public static final int LOW_DODGE = 5;
	public static final int HIGH_CUE = 6;
	public static final int MID_CUE = 7;
	public static final int LOW_CUE = 8;
	public static final int COMBO_BITE_BITE_BITE_CUE = 9;
	public static final int COMBO_BITE_BITE_TAIL_CUE = 10;
	public static final int COMBO_BITE_TAIL_BITE_CUE = 11;

	public static final Type<SnakeCombatFeedbackPayload> TYPE = new Type<>(
			ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, "snake_combat_feedback"));
	public static final StreamCodec<FriendlyByteBuf, SnakeCombatFeedbackPayload> STREAM_CODEC =
			StreamCodec.of(SnakeCombatFeedbackPayload::write, SnakeCombatFeedbackPayload::read);

	@Override
	public Type<SnakeCombatFeedbackPayload> type() {
		return TYPE;
	}

	private static SnakeCombatFeedbackPayload read(FriendlyByteBuf buffer) {
		return new SnakeCombatFeedbackPayload(buffer.readVarInt(), buffer.readUnsignedByte(), buffer.readFloat());
	}

	private static void write(FriendlyByteBuf buffer, SnakeCombatFeedbackPayload payload) {
		buffer.writeVarInt(payload.bossEntityId());
		buffer.writeByte(payload.kind());
		buffer.writeFloat(payload.strength());
	}

	public static void handle(SnakeCombatFeedbackPayload payload, IPayloadContext context) {
		context.enqueueWork(() -> ClientHooks.accept(payload));
	}

	@OnlyIn(Dist.CLIENT)
	private static final class ClientHooks {
		private static void accept(SnakeCombatFeedbackPayload payload) {
			dev.tatami.virtualcraft.client.SnakeBossCombatFeedback.accept(
					payload.bossEntityId(), payload.kind(), payload.strength());
		}
	}
}
