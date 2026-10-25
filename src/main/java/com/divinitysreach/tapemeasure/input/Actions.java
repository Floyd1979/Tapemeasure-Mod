package com.divinitysreach.tapemeasure.input;

import com.divinitysreach.tapemeasure.Selection;
import com.divinitysreach.tapemeasure.TapeMeasureClient;
import com.divinitysreach.tapemeasure.math.Mode;
import com.divinitysreach.tapemeasure.math.Pt;
import com.divinitysreach.tapemeasure.math.Readout;
import com.divinitysreach.tapemeasure.math.Side;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Everything the tape can do. Tool clicks, keybinds and commands all end up here so they behave the same.
 * Feedback goes to the action bar so chat stays clean.
 */
public final class Actions {
	private Actions() {
	}

	/** Left click / point 1 key: first corner, start or center. Path and polygon start over. */
	public static void primary(Minecraft mc) {
		Pt target = Targeting.target(mc);
		if (target == null) {
			noTarget(mc);
			return;
		}
		primaryAt(mc, target);
	}

	public static void primaryAt(Minecraft mc, Pt p) {
		TapeMeasureClient tape = TapeMeasureClient.get();
		Selection sel = tape.selection();
		sel.primary(p, TapeMeasureClient.dimensionOf(mc));
		tape.onSelectionChanged();
		Mode mode = sel.mode();
		String what = mode.twoPoint() ? capital(mode.primaryName()) : "Started new " + mode.label().toLowerCase();
		actionBar(mc, Component.literal(what + ": ").withStyle(ChatFormatting.GREEN)
			.append(Component.literal(p.coords()).withStyle(ChatFormatting.WHITE))
			.append(summary(mc)));
	}

	/** Right click / point 2 key: second corner, end or edge. Path and polygon add a point. */
	public static void secondary(Minecraft mc) {
		Pt target = Targeting.target(mc);
		if (target == null) {
			noTarget(mc);
			return;
		}
		secondaryAt(mc, target);
	}

	public static void secondaryAt(Minecraft mc, Pt p) {
		TapeMeasureClient tape = TapeMeasureClient.get();
		Selection sel = tape.selection();
		if (!sel.secondary(p, TapeMeasureClient.dimensionOf(mc))) {
			actionBar(mc, Component.literal("Point limit reached (" + Selection.MAX_POINTS + ")").withStyle(ChatFormatting.RED));
			return;
		}
		tape.onSelectionChanged();
		Mode mode = sel.mode();
		String what = mode.twoPoint() ? capital(mode.secondaryName()) : "Point " + sel.count();
		actionBar(mc, Component.literal(what + ": ").withStyle(ChatFormatting.AQUA)
			.append(Component.literal(p.coords()).withStyle(ChatFormatting.WHITE))
			.append(summary(mc)));
	}

	public static void undo(Minecraft mc) {
		TapeMeasureClient tape = TapeMeasureClient.get();
		if (tape.selection().undo()) {
			tape.onSelectionChanged();
			actionBar(mc, Component.literal("Removed last point").withStyle(ChatFormatting.YELLOW));
		} else {
			actionBar(mc, Component.literal("Nothing to undo").withStyle(ChatFormatting.GRAY));
		}
	}

	public static void clear(Minecraft mc) {
		TapeMeasureClient tape = TapeMeasureClient.get();
		tape.selection().clear();
		tape.onSelectionChanged();
		tape.counter().cancel();
		actionBar(mc, Component.literal("Measurement cleared").withStyle(ChatFormatting.YELLOW));
	}

	public static void cycleMode(Minecraft mc, int direction) {
		setMode(mc, TapeMeasureClient.get().selection().mode().cycle(direction));
	}

	public static void setMode(Minecraft mc, Mode mode) {
		TapeMeasureClient tape = TapeMeasureClient.get();
		tape.selection().setMode(mode);
		tape.onSelectionChanged();
		actionBar(mc, Component.literal("Mode: ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal(mode.label()).withStyle(ChatFormatting.WHITE))
			.append(Component.literal("  (left click " + mode.primaryName() + ", right click " + mode.secondaryName() + ")").withStyle(ChatFormatting.GRAY)));
	}

	public static void toggleHud(Minecraft mc) {
		TapeMeasureClient tape = TapeMeasureClient.get();
		tape.config().showHud = !tape.config().showHud;
		tape.config().save();
		actionBar(mc, Component.literal("Measure panel " + (tape.config().showHud ? "shown" : "hidden")).withStyle(ChatFormatting.YELLOW));
	}

	public static void toggleVisible(Minecraft mc) {
		TapeMeasureClient tape = TapeMeasureClient.get();
		tape.setVisible(!tape.visible());
		actionBar(mc, Component.literal("Tape Measure " + (tape.visible() ? "shown" : "hidden")).withStyle(ChatFormatting.YELLOW));
	}

	/** The side the player is looking toward, or null outside a world. */
	public static Side lookSide(Minecraft mc) {
		return mc.player == null ? null : Side.looking(mc.player.getYRot(), mc.player.getXRot());
	}

	/**
	 * Grows (positive) or shrinks (negative) the shape on one or more sides.
	 *
	 * @param sides the sides to move; empty means the side you are looking toward
	 */
	public static void resize(Minecraft mc, int amount, Side... sides) {
		if (sides.length == 0) {
			Side look = lookSide(mc);
			if (look == null) {
				return;
			}
			sides = new Side[] {look};
		}
		TapeMeasureClient tape = TapeMeasureClient.get();
		StringBuilder names = new StringBuilder();
		for (Side side : sides) {
			String problem = tape.selection().resize(side, amount);
			if (problem != null) {
				actionBar(mc, Component.literal(problem).withStyle(ChatFormatting.RED));
				tape.onSelectionChanged();
				return;
			}
			names.append(names.length() == 0 ? "" : " and ").append(side.id());
		}
		tape.onSelectionChanged();
		String verb = amount >= 0 ? "Grew " : "Shrank ";
		actionBar(mc, Component.literal(verb + names + " by " + Math.abs(amount)).withStyle(ChatFormatting.GREEN)
			.append(summary(mc)));
	}

	/** Moves the whole shape. No side means the way you are looking. */
	public static void shift(Minecraft mc, int amount, Side side) {
		Side dir = side != null ? side : lookSide(mc);
		if (dir == null) {
			return;
		}
		TapeMeasureClient tape = TapeMeasureClient.get();
		String problem = tape.selection().shift(dir, amount);
		if (problem != null) {
			actionBar(mc, Component.literal(problem).withStyle(ChatFormatting.RED));
			return;
		}
		tape.onSelectionChanged();
		actionBar(mc, Component.literal("Moved " + dir.id() + " by " + amount).withStyle(ChatFormatting.GREEN)
			.append(summary(mc)));
	}

	public static void count(Minecraft mc) {
		TapeMeasureClient.get().counter().start(mc);
	}

	private static MutableComponent summary(Minecraft mc) {
		Readout readout = TapeMeasureClient.get().pickedReadout(mc);
		if (!readout.complete() || readout.rows().isEmpty()) {
			return Component.empty();
		}
		Readout.Row first = readout.rows().get(0);
		return Component.literal("  " + first.label() + " " + first.value()).withStyle(ChatFormatting.GOLD);
	}

	private static void noTarget(Minecraft mc) {
		int reach = TapeMeasureClient.get().config().reach;
		actionBar(mc, Component.literal("No block within " + reach + " blocks").withStyle(ChatFormatting.RED));
	}

	public static void actionBar(Minecraft mc, Component message) {
		mc.gui.hud.setOverlayMessage(message, false);
	}

	private static String capital(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
