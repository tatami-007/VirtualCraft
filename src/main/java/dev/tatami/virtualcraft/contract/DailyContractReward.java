package dev.tatami.virtualcraft.contract;

public record DailyContractReward(Kind kind, long amount) {
	public enum Kind {
		USDT,
		BTC,
		DATA_FRAGMENT,
		EXPLORER_HASH_VOUCHER,
		ENCRYPTED_HASH_VOUCHER,
		QUANTUM_HASH_VOUCHER
	}
}
