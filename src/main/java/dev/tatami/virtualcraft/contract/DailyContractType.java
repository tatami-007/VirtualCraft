package dev.tatami.virtualcraft.contract;

public enum DailyContractType {
	SETTLE_VOUCHERS("settle_vouchers"),
	SHOP_TRADE("shop_trade"),
	SURVEY_VEIN("survey_vein"),
	CONVERT_CURRENCY("convert_currency");

	private final String id;

	DailyContractType(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}
}
