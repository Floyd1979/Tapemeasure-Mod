package com.divinitysreach.tapemeasure.math;

/**
 * An immutable block position. Kept free of Minecraft classes so the measuring math can be tested on its own.
 */
public record Pt(int x, int y, int z) {
	public Pt withY(int newY) {
		return new Pt(this.x, newY, this.z);
	}

	public String coords() {
		return this.x + ", " + this.y + ", " + this.z;
	}

	@Override
	public String toString() {
		return coords();
	}
}
