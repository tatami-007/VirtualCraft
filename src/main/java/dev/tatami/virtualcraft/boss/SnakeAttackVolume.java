package dev.tatami.virtualcraft.boss;

public record SnakeAttackVolume(Shape shape, double originX, double originY, double originZ,
		double forwardX, double forwardZ, double range, double width, double minY, double maxY) {
	public static final double BITE_RANGE = 6.8;
	public static final double BITE_HALF_ANGLE = Math.toRadians(62.0);
	public static final double TAIL_INNER_RADIUS = 1.0;
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

	public boolean intersects(double minX, double minY, double minZ,
			double maxX, double maxY, double maxZ) {
		if (maxY < originY + minY() || minY > originY + maxY()) {
			return false;
		}
		double centerX = (minX + maxX) * 0.5;
		double centerZ = (minZ + maxZ) * 0.5;
		double sampleY = Math.max(minY, Math.min(maxY, originY + (minY() + maxY()) * 0.5));
		double[] xs = {minX, centerX, maxX};
		double[] zs = {minZ, centerZ, maxZ};
		for (double x : xs) {
			for (double z : zs) {
				if (contains(x, sampleY, z)) {
					return true;
				}
			}
		}
		return minX <= originX && maxX >= originX && minZ <= originZ && maxZ >= originZ;
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
