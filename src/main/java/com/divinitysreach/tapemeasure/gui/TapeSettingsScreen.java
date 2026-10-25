package com.divinitysreach.tapemeasure.gui;

import com.divinitysreach.tapemeasure.TapeMeasureClient;
import com.divinitysreach.tapemeasure.config.TapeConfig;
import com.divinitysreach.tapemeasure.math.Fmt;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Tape Measure settings. Opens from the Tape Measure keybind, {@code /measure config}, or Mod Menu.
 *
 * <p>Changes are made to a copy of the config. Done (or Escape) saves them, Cancel throws them away.</p>
 */
public final class TapeSettingsScreen extends Screen {
	private static final int TITLE_COLOR = 0xFFFFFFFF;
	private static final int BUTTON_WIDTH = 100;
	private static final int BUTTON_HEIGHT = 20;

	private final Screen parent;
	private TapeConfig working;
	private boolean discard;

	public TapeSettingsScreen(Screen parent) {
		super(Component.literal("Tape Measure Settings"));
		this.parent = parent;
		this.working = TapeMeasureClient.get().config().copy();
	}

	@Override
	protected void init() {
		SettingsList list = new SettingsList(this.minecraft, this.width, this.height - 64, 28, () -> this.working);
		fill(list);
		this.addRenderableWidget(list);

		int y = this.height - 28;
		int x = this.width / 2 - (BUTTON_WIDTH * 3 + 8) / 2;
		this.addRenderableWidget(Button.builder(Component.literal("Reset all"), b -> {
				this.working = TapeConfig.defaults();
				this.rebuildWidgets();
			})
			.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
			.tooltip(Tooltip.create(Component.literal("Put every option back to its default. Nothing is saved until you press Done.")))
			.build());
		this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> {
				this.discard = true;
				this.onClose();
			})
			.bounds(x + BUTTON_WIDTH + 4, y, BUTTON_WIDTH, BUTTON_HEIGHT)
			.build());
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
			.bounds(x + (BUTTON_WIDTH + 4) * 2, y, BUTTON_WIDTH, BUTTON_HEIGHT)
			.build());
	}

	private static void fill(SettingsList list) {
		list.header("Tape");
		list.toggle("Tape item", "Clicks with the tape item measure instead of attacking or using it",
			c -> c.toolEnabled, (c, v) -> c.toolEnabled = v);
		list.item("Item", "The item that works as the tape. Type an item id or press Held to use the one in your hand.");
		list.slider("Reach", "How far away a point can be picked", 8, 512, 8,
			c -> (double) c.reach, (c, v) -> c.reach = (int) Math.round(v), v -> Fmt.num(Math.round(v)) + " blocks");
		list.toggle("Live preview", "While you hold the tape, the next point follows your crosshair",
			c -> c.livePreview, (c, v) -> c.livePreview = v);
		list.toggle("Face targeting", "Pick the air block in front of the face you look at instead of the block itself",
			c -> c.targetFace, (c, v) -> c.targetFace = v);
		list.toggle("Sneak scroll mode", "Sneak and scroll while holding the tape to change mode instead of hotbar slot",
			c -> c.sneakScrollChangesMode, (c, v) -> c.sneakScrollChangesMode = v);
		list.toggle("Ctrl scroll resize", "Hold Ctrl and scroll while holding the tape to grow (scroll up) or shrink (scroll down) the side you look at",
			c -> c.ctrlScrollResizes, (c, v) -> c.ctrlScrollResizes = v);

		list.header("In the world");
		list.toggle("Grid", "One line per block on the faces of a box", c -> c.showGrid, (c, v) -> c.showGrid = v);
		list.slider("Grid range", "Grid lines further than this from you are skipped, which keeps huge boxes cheap",
			16, 256, 8, c -> (double) c.gridRange, (c, v) -> c.gridRange = (int) Math.round(v), v -> Fmt.num(Math.round(v)) + " blocks");
		list.toggle("Floating labels", "Sizes and distances drawn next to the shape", c -> c.showLabels, (c, v) -> c.showLabels = v);
		list.toggle("Lines through blocks", "Draw the hidden parts of outlines faintly so you can see them through walls",
			c -> !c.hideObstructedLines, (c, v) -> c.hideObstructedLines = !v);
		list.slider("Line width", "Outline thickness", 1, 8, 0.5,
			c -> (double) c.lineWidth, (c, v) -> c.lineWidth = (float) (double) v, v -> Fmt.dec(v, 1));

		list.header("Readout panel");
		list.toggle("Show panel", "The numbers panel in a corner of the screen", c -> c.showHud, (c, v) -> c.showHud = v);
		list.corner("Corner", "Which corner of the screen the panel sits in");
		list.slider("Offset across", "Distance from the side of the screen", 0, 200, 1,
			c -> (double) c.hudOffsetX, (c, v) -> c.hudOffsetX = (int) Math.round(v), v -> Fmt.num(Math.round(v)) + " px");
		list.slider("Offset down", "Distance from the top or bottom of the screen", 0, 200, 1,
			c -> (double) c.hudOffsetY, (c, v) -> c.hudOffsetY = (int) Math.round(v), v -> Fmt.num(Math.round(v)) + " px");

		list.header("Block counting");
		list.slider("Speed", "Blocks checked per tick. Higher finishes sooner but costs more frame time.", 10_000, 2_000_000, 10_000,
			c -> (double) c.countBlocksPerTick, (c, v) -> c.countBlocksPerTick = (int) Math.round(v), v -> Fmt.num(Math.round(v)) + " / tick");
		list.slider("Types listed", "How many block types are listed in chat after a count", 5, 50, 1,
			c -> (double) c.countTopEntries, (c, v) -> c.countTopEntries = (int) Math.round(v), v -> Fmt.num(Math.round(v)));
		list.slider("Largest area", "Selections bigger than this are refused", 1, 500, 1,
			c -> c.countMaxVolume / 1_000_000.0, (c, v) -> c.countMaxVolume = Math.round(v) * 1_000_000L,
			v -> Fmt.num(Math.round(v)) + " million");

		list.header("Colors");
		list.color("Box", c -> c.box, (c, v) -> c.box = v);
		list.color("Grid", c -> c.grid, (c, v) -> c.grid = v);
		list.color("First point", c -> c.corner1, (c, v) -> c.corner1 = v);
		list.color("Second point", c -> c.corner2, (c, v) -> c.corner2 = v);
		list.color("Line", c -> c.line, (c, v) -> c.line = v);
		list.color("Path", c -> c.path, (c, v) -> c.path = v);
		list.color("Polygon", c -> c.polygon, (c, v) -> c.polygon = v);
		list.color("Circle", c -> c.circle, (c, v) -> c.circle = v);
		list.color("Preview point", c -> c.preview, (c, v) -> c.preview = v);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(this.font, this.title, this.width / 2, 10, TITLE_COLOR);
	}

	@Override
	public void onClose() {
		if (!this.discard) {
			TapeMeasureClient.get().applyConfig(this.working);
		}
		this.minecraft.gui.setScreen(this.parent);
	}
}
