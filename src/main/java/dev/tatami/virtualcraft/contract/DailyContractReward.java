package dev.tatami.virtualcraft.contract;

public record DailyContractReward(Kind kind, long amount) {
	public enum Kind {
		USDT,
		BTC,
		EXPLORER_HASH_VOUCHER,
		ENCRYPTED_HASH_VOUCHER,
		QUANTUM_HASH_VOUCHER
	}
}
