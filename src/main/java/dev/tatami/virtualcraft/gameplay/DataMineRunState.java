package dev.tatami.virtualcraft.gameplay;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/**
 * 每玩家「本局下潜」状态(肉鸽 run)。记录本局种子、当前/最深层数、未结算的
 * 「算力袋」(挖到的矿脉单位)，以及当前层的下行井 / 撤离台世界坐标(供 tick 探测)。
 *
 * <p>故意 <b>不</b> {@code copyOnDeath()}:玩家阵亡后重生会得到全新的空状态,
 * 即「本局算力袋自动清零」——这是下潜失败的惩罚。撤离(结算)与开新局都会显式重置。</p>
 */
public final class DataMineRunState implements INBTSerializable<CompoundTag> {
	private boolean active = false;
	private long seed = 0L;
	private int depth = 0;
	private int deepest = 0;
	private int oreUnits = 0;

	private boolean hasShaft = false;
	private int shaftX;
	private int shaftY;
	private int shaftZ;

	private boolean hasPad = false;
	private int padX;
	private int padY;
	private int padZ;

	public boolean isActive() {
		return active;
	}

	public long seed() {
		return seed;
	}

	public int depth() {
		return depth;
	}

	public int deepest() {
		return deepest;
	}

	public int oreUnits() {
		return oreUnits;
	}

	public boolean hasShaft() {
		return hasShaft;
	}

	public int shaftX() {
		return shaftX;
	}

	public int shaftY() {
		return shaftY;
	}

	public int shaftZ() {
		return shaftZ;
	}

	public boolean hasPad() {
		return hasPad;
	}

	public int padX() {
		return padX;
	}

	public int padY() {
		return padY;
	}

	public int padZ() {
		return padZ;
	}

	/** 开新局:重置一切并落到第 1 层。 */
	public void startRun(long seed) {
		this.active = true;
		this.seed = seed;
		this.depth = 1;
		this.deepest = 1;
		this.oreUnits = 0;
		this.hasShaft = false;
		this.hasPad = false;
	}

	public void setDepth(int depth) {
		this.depth = depth;
		if (depth > deepest) {
			deepest = depth;
		}
	}

	public void addOre(int units) {
		if (units > 0) {
			oreUnits += units;
		}
	}

	public void setShaft(int x, int y, int z) {
		this.hasShaft = true;
		this.shaftX = x;
		this.shaftY = y;
		this.shaftZ = z;
	}

	public void setPad(int x, int y, int z) {
		this.hasPad = true;
		this.padX = x;
		this.padY = y;
		this.padZ = z;
	}

	/** 结算/退出:清空本局,返回被清空的算力袋数(供提示「丢失/已结算多少」)。 */
	public int reset() {
		int cleared = oreUnits;
		active = false;
		depth = 0;
		oreUnits = 0;
		hasShaft = false;
		hasPad = false;
		return cleared;
	}

	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("active", active);
		tag.putLong("seed", seed);
		tag.putInt("depth", depth);
		tag.putInt("deepest", deepest);
		tag.putInt("ore", oreUnits);
		tag.putBoolean("hasShaft", hasShaft);
		tag.putInt("sx", shaftX);
		tag.putInt("sy", shaftY);
		tag.putInt("sz", shaftZ);
		tag.putBoolean("hasPad", hasPad);
		tag.putInt("px", padX);
		tag.putInt("py", padY);
		tag.putInt("pz", padZ);
		return tag;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
		active = nbt.getBoolean("active");
		seed = nbt.getLong("seed");
		depth = nbt.getInt("depth");
		deepest = nbt.getInt("deepest");
		oreUnits = nbt.getInt("ore");
		hasShaft = nbt.getBoolean("hasShaft");
		shaftX = nbt.getInt("sx");
		shaftY = nbt.getInt("sy");
		shaftZ = nbt.getInt("sz");
		hasPad = nbt.getBoolean("hasPad");
		padX = nbt.getInt("px");
		padY = nbt.getInt("py");
		padZ = nbt.getInt("pz");
	}
}
