package com.divinitysreach.tapemeasure.math;

import java.util.Locale;

/** The six sides of a selection, in world directions (north is negative Z, east is positive X). */
public enum Side {
	UP(0, 1, 0),
	DOWN(0, -1, 0),
	NORTH(0, 0, -1),
	SOUTH(0, 0, 1),
	EAST(1, 0, 0),
	WEST(-1, 0, 0);

	private final int dx;
	private final int dy;
	private final int dz;

	Side(int dx, int dy, int dz) {
		this.dx = dx;
		this.dy = dy;
		this.dz = dz;
	}

	public int dx() {
		return this.dx;
	}

	public int dy() {
		return this.dy;
	}

	public int dz() {
		return this.dz;
	}

	public boolean vertical() {
		return this.dy != 0;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/**
	 * The side a player is looking toward.
	 *
	 * @param yaw Minecraft yaw (0 south, 90 west, 180 north, 270 or -90 east)
	 * @param pitch Minecraft pitch (negative looks up)
	 */
	public static Side looking(float yaw, float pitch) {
		if (pitch < -45) {
			return UP;
		}
		if (pitch > 45) {
			return DOWN;
		}
		int quarter = Math.floorMod((int) Math.floor(yaw / 90.0 + 0.5), 4);
		return switch (quarter) {
			case 0 -> SOUTH;
			case 1 -> WEST;
			case 2 -> NORTH;
			default -> EAST;
		};
	}

	/** Moves a point {@code amount} blocks toward this side. */
	public Pt move(Pt p, int amount) {
		return new Pt(p.x() + this.dx * amount, p.y() + this.dy * amount, p.z() + this.dz * amount);
	}
}
