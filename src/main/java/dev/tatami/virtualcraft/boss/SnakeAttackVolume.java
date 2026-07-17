package dev.tatami.virtualcraft.boss;

public record SnakeAttackVolume(Shape shape, double originX, double originY, double originZ,
		double forwardX, double forwardZ, double range, double width, double minY, double maxY) {
	public static final double BITE_RANGE = 6.5;
	public static final double BITE_HALF_ANGLE = Math.toRadians(55.0);
	public static final double TAIL_INNER_RADIUS = 1.43;
	public static final double TAIL_OUTER_RADIUS = 7.02;
	public static final double HIGH_POISON_RANGE = 12.0;
	public static final double HIGH_POISON_HALF_WIDTH = 1.5;
	public static final double GROUND_POISON_RANGE = 9.0;
	public static final double GROUND_POISON_HALF_ANGLE = Math.toRadians(55.0);
	public static final double DASH_RANGE = 8.0;
	public static final double DASH_HALF_WIDTH = 1.885;
	public static final double OVERHEAD_RANGE = 7.8;
	public static final double OVERHEAD_HALF_ANGLE = Math.toRadians(66.0);
	public static SnakeAttackVolume cone(double x, double y, double z, double forwardX, double forwardZ,
			double range, double halfAngleRadians, double minY, double maxY) {
		return new SnakeAttackVolume(Shape.CONE, x, y, z, forwardX, forwardZ, range,
				halfAngleRadians, minY, maxY).normalized();
	}

	public static SnakeAttackVolume beam(double x, double y, double z, double forwardX, double forwardZ,
			double range, double halfWidth, double minY, double maxY) {
		return new SnakeAttackVolume(Shape.BEAM, x, y, z, forwardX, forwardZ, range,
				halfWidth, minY, maxY).normalized();
	}

	public static SnakeAttackVolume ring(double x, double y, double z, double radius,
			double innerRadius, double minY, double maxY) {
		return new SnakeAttackVolume(Shape.RING, x, y, z, 0.0, 1.0, radius,
				innerRadius, minY, maxY);
	}

	public boolean contains(double x, double y, double z) {
		if (y < originY + minY || y > originY + maxY) {
			return false;
		}
		double dx = x - originX;
		double dz = z - originZ;
		double distanceSquared = dx * dx + dz * dz;
		if (shape == Shape.RING) {
			return distanceSquared <= range * range && distanceSquared >= width * width;
		}
		double along = dx * forwardX + dz * forwardZ;
		if (along < 0.0 || along > range) {
			return false;
		}
		double lateral = Math.abs(dx * -forwardZ + dz * forwardX);
		if (shape == Shape.BEAM) {
			return lateral <= width;
		}
		double angle = Math.atan2(lateral, Math.max(1.0E-6, along));
		return angle <= width;
	}

	private SnakeAttackVolume normalized() {
		double length = Math.sqrt(forwardX * forwardX + forwardZ * forwardZ);
		return length < 1.0E-6 ? new SnakeAttackVolume(shape, originX, originY, originZ,
				0.0, 1.0, range, width, minY, maxY) : new SnakeAttackVolume(shape,
				originX, originY, originZ, forwardX / length, forwardZ / length,
				range, width, minY, maxY);
	}

	public enum Shape {
		CONE,
		BEAM,
		RING
	}
}
