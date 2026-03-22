package dev.tatami.virtualcraft.wallet;

public enum WalletLedgerType {
	DEPOSIT("deposit"),
	WITHDRAW("withdraw"),
	SWAP("swap"),
	TRANSFER("transfer"),
	PREMIUM_BUY("premium_buy"),
	PREMIUM_SELL("premium_sell"),
	POWER_LINK("power_link"),
	MINING_SETTLEMENT("mining_settlement");

	private final String id;

	WalletLedgerType(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static WalletLedgerType byId(String id) {
		for (WalletLedgerType type : values()) {
			if (type.id.equals(id)) {
				return type;
			}
		}
		return DEPOSIT;
	}
}
