package dev.tatami.virtualcraft.energy;

/** A machine may receive tower power only while its own tier-three cell is installed. */
public interface WirelessPowerReceiver {
	boolean canReceiveWirelessPower();
}
