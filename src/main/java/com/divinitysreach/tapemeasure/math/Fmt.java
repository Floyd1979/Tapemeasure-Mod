package com.divinitysreach.tapemeasure.math;

import java.util.Locale;

/** Number formatting shared by the HUD, chat output and labels. */
public final class Fmt {
	private static final String[] COMPASS = {
		"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
		"S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
	};

	private Fmt() {
	}

	public static String num(long value) {
		return String.format(Locale.US, "%,d", value);
	}

	public static String dec(double value) {
		return dec(value, 2);
	}

	public static String dec(double value, int places) {
		return String.format(Locale.US, "%,." + places + "f", value);
	}

	/** Shortest sensible form: whole numbers without decimals, everything else with up to two. */
	public static String len(double value) {
		if (Math.abs(value - Math.rint(value)) < 0.005) {
			return num(Math.round(value));
		}
		return dec(value, 2);
	}

	public static String signed(long value) {
		return value > 0 ? "+" + num(value) : num(value);
	}

	public static String compass(double bearing) {
		int index = (int) Math.floor((bearing + 11.25) / 22.5) & 15;
		return COMPASS[index];
	}

	/** "19 stacks + 48" style count for a number of blocks. */
	public static String stacks(long blocks) {
		if (blocks < 64) {
			return num(blocks);
		}
		long stacks = blocks / 64;
		long rest = blocks % 64;
		String s = num(stacks) + (stacks == 1 ? " stack" : " stacks");
		return rest == 0 ? s : s + " + " + rest;
	}

	/** Shulker boxes needed for a number of blocks (27 stacks of 64 each). */
	public static String shulkers(long blocks) {
		double boxes = blocks / 1728.0;
		if (boxes < 10) {
			return dec(boxes, 2) + " shulkers";
		}
		return dec(boxes, 1) + " shulkers";
	}

	public static String size3(long x, long y, long z) {
		return num(x) + " × " + num(y) + " × " + num(z);
	}
}
