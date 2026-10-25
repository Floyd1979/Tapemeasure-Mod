package com.divinitysreach.tapemeasure.math;

import java.util.Locale;

/**
 * Measuring modes. Two point modes share the same pair of corners, list modes share the same point list,
 * so switching between related modes keeps what you already picked.
 */
public enum Mode {
	BOX("Box", true, "corner 1", "corner 2"),
	LINE("Line", true, "start", "end"),
	PATH("Path", false, "start", "next point"),
	POLYGON("Polygon", false, "first corner", "next corner"),
	CIRCLE("Circle", true, "center", "edge");

	private final String label;
	private final boolean twoPoint;
	private final String primaryName;
	private final String secondaryName;

	Mode(String label, boolean twoPoint, String primaryName, String secondaryName) {
		this.label = label;
		this.twoPoint = twoPoint;
		this.primaryName = primaryName;
		this.secondaryName = secondaryName;
	}

	public String label() {
		return this.label;
	}

	public boolean twoPoint() {
		return this.twoPoint;
	}

	public String primaryName() {
		return this.primaryName;
	}

	public String secondaryName() {
		return this.secondaryName;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Whether the block counter can scan this shape. */
	public boolean countable() {
		return this == BOX || this == POLYGON || this == CIRCLE;
	}

	public Mode cycle(int direction) {
		Mode[] all = values();
		int next = Math.floorMod(ordinal() + (direction >= 0 ? 1 : -1), all.length);
		return all[next];
	}

	public static Mode byId(String id) {
		for (Mode mode : values()) {
			if (mode.id().equalsIgnoreCase(id)) {
				return mode;
			}
		}
		return null;
	}
}
