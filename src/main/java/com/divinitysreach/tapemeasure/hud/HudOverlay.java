package com.divinitysreach.tapemeasure.hud;

import com.divinitysreach.tapemeasure.Selection;
import com.divinitysreach.tapemeasure.TapeMeasureClient;
import com.divinitysreach.tapemeasure.config.TapeConfig;
import com.divinitysreach.tapemeasure.count.BlockCounter;
import com.divinitysreach.tapemeasure.input.ToolInput;
import com.divinitysreach.tapemeasure.math.Mode;
import com.divinitysreach.tapemeasure.math.Readout;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** The readout panel in a corner of the screen. */
public final class HudOverlay {
	private static final int TITLE = 0xFFFFC040;
	private static final int LABEL = 0xFFA8A8A8;
	private static final int VALUE = 0xFFFFFFFF;
	private static final int HINT = 0xFF7F8C99;
	private static final int BUSY = 0xFFFFE066;
	private static final int BACKGROUND = 0x90000000;
	private static final int PAD = 4;
	private static final int GAP = 8;
	private static final int LINE = 10;

	private record Line(String left, String right, int leftColor) {
	}

	private HudOverlay() {
	}

	public static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		TapeMeasureClient tape = TapeMeasureClient.get();
		if (tape == null || mc.player == null || mc.level == null) {
			return;
		}
		TapeConfig config = tape.config();
		if (!config.showHud || !tape.visible()) {
			return;
		}
		boolean holding = ToolInput.holdingTool(mc);
		BlockCounter counter = tape.counter();
		Selection.View view = tape.currentView(mc);
		if (view.isEmpty() && !holding && !counter.running()) {
			return;
		}

		Selection selection = tape.selection();
		Mode mode = selection.mode();
		Readout readout = tape.currentReadout(mc);
		List<Line> lines = new ArrayList<>();
		for (Readout.Row row : readout.rows()) {
			if (row.hud()) {
				lines.add(new Line(row.label(), row.value(), LABEL));
			}
		}

		if (counter.running()) {
			lines.add(new Line("Counting", Math.round(counter.progress() * 100) + "%", BUSY));
		} else if (counter.lastSummary() != null && counter.mode() == mode) {
			lines.add(new Line("Counted", counter.lastSummary(), LABEL));
		}

		if (holding) {
			lines.addAll(hints(selection, mode, config));
		}

		Font font = mc.font;
		String title = "Tape Measure · " + readout.title();
		int labelWidth = 0;
		int valueWidth = 0;
		int hintWidth = 0;
		for (Line line : lines) {
			if (line.right() == null) {
				hintWidth = Math.max(hintWidth, font.width(line.left()));
			} else {
				labelWidth = Math.max(labelWidth, font.width(line.left()));
				valueWidth = Math.max(valueWidth, font.width(line.right()));
			}
		}
		int contentWidth = Math.max(font.width(title), Math.max(hintWidth, labelWidth + GAP + valueWidth));
		int width = contentWidth + PAD * 2;
		int height = PAD * 2 + LINE * (lines.size() + 1) - 1;

		int screenW = graphics.guiWidth();
		int screenH = graphics.guiHeight();
		int x;
		int y;
		switch (config.corner()) {
			case TOP_RIGHT -> {
				x = screenW - width - config.hudOffsetX;
				y = config.hudOffsetY;
			}
			case BOTTOM_LEFT -> {
				x = config.hudOffsetX;
				y = screenH - height - config.hudOffsetY;
			}
			case BOTTOM_RIGHT -> {
				x = screenW - width - config.hudOffsetX;
				y = screenH - height - config.hudOffsetY;
			}
			default -> {
				x = config.hudOffsetX;
				y = config.hudOffsetY;
			}
		}

		graphics.fill(x, y, x + width, y + height, BACKGROUND);
		int tx = x + PAD;
		int ty = y + PAD;
		graphics.text(font, title, tx, ty, TITLE, true);
		ty += LINE;
		for (Line line : lines) {
			if (line.right() == null) {
				graphics.text(font, line.left(), tx, ty, HINT, false);
			} else {
				graphics.text(font, line.left(), tx, ty, line.leftColor(), true);
				graphics.text(font, line.right(), tx + labelWidth + GAP, ty, VALUE, true);
			}
			ty += LINE;
		}
	}

	private static List<Line> hints(Selection selection, Mode mode, TapeConfig config) {
		List<Line> hints = new ArrayList<>();
		switch (mode) {
			case BOX, LINE, CIRCLE -> {
				if (selection.first() == null) {
					hints.add(hint("Left click: " + mode.primaryName()));
				} else if (selection.second() == null) {
					hints.add(hint("Right click: " + mode.secondaryName()));
				} else {
					hints.add(hint("Left / right click to move the points"));
				}
			}
			case PATH, POLYGON -> {
				if (selection.points().isEmpty()) {
					hints.add(hint("Left click: " + mode.primaryName()));
				} else {
					hints.add(hint("Right click: add point · Left click: start over"));
				}
			}
		}
		if (config.ctrlScrollResizes && selection.isComplete()) {
			if (mode == Mode.BOX) {
				hints.add(hint("Ctrl + scroll: grow or shrink the side you look at"));
			} else if (mode == Mode.POLYGON || mode == Mode.CIRCLE) {
				hints.add(hint("Ctrl + scroll looking up or down: change the height"));
			}
		}
		hints.add(hint(config.sneakScrollChangesMode
			? "Sneak: left clears, right undoes, scroll changes mode"
			: "Sneak: left clears, right undoes"));
		return hints;
	}

	private static Line hint(String text) {
		return new Line(text, null, HINT);
	}
}
