package com.divinitysreach.tapemeasure.count;

import com.divinitysreach.tapemeasure.Selection;
import com.divinitysreach.tapemeasure.TapeMeasureClient;
import com.divinitysreach.tapemeasure.config.TapeConfig;
import com.divinitysreach.tapemeasure.math.Fmt;
import com.divinitysreach.tapemeasure.math.Geometry;
import com.divinitysreach.tapemeasure.math.Mode;
import com.divinitysreach.tapemeasure.math.Pt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts the blocks inside a box, polygon or circle selection, a slice at a time so big areas never freeze the
 * game. Only chunks your client has loaded can be read; anything else is reported as skipped.
 */
public final class BlockCounter {
	private boolean running;
	private int revision;
	private String dimension;
	private Mode mode;
	private String shapeText;

	private Geometry.Footprint footprint;
	private int minY;
	private int maxY;
	private long totalBlocks;

	// cursor
	private int z;
	private int[] spans;
	private int spanIndex;
	private int x;
	private int y;

	private final Map<Block, long[]> counts = new HashMap<>();
	private long scanned;
	private long air;
	private long unloaded;
	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

	private String lastSummary;

	public boolean running() {
		return this.running;
	}

	/** Fraction done, 0 to 1. */
	public double progress() {
		return this.totalBlocks <= 0 ? 0 : Math.min(1.0, (double) this.scanned / this.totalBlocks);
	}

	/** Short text for the HUD after the last finished count, or null. */
	public String lastSummary() {
		return this.lastSummary;
	}

	public void cancel() {
		this.running = false;
	}

	public void cancelIfStale(int currentRevision) {
		if (this.running && currentRevision != this.revision) {
			this.running = false;
			send(Minecraft.getInstance(), Component.literal("Block count stopped: the selection changed").withStyle(ChatFormatting.GRAY));
		}
		this.lastSummary = null;
	}

	public void start(Minecraft mc) {
		TapeMeasureClient tape = TapeMeasureClient.get();
		Selection sel = tape.selection();
		Mode selMode = sel.mode();
		if (mc.level == null) {
			return;
		}
		if (!selMode.countable()) {
			send(mc, Component.literal("Block counting works in Box, Polygon and Circle modes").withStyle(ChatFormatting.RED));
			return;
		}
		if (!sel.isComplete()) {
			send(mc, Component.literal("Finish the " + selMode.label().toLowerCase() + " first").withStyle(ChatFormatting.RED));
			return;
		}
		String dim = TapeMeasureClient.dimensionOf(mc);
		if (sel.dimension() != null && !sel.dimension().equals(dim)) {
			send(mc, Component.literal("That selection is in another dimension").withStyle(ChatFormatting.RED));
			return;
		}

		List<Pt> pts;
		Geometry.Footprint fp;
		int y0;
		int y1;
		String shape;
		switch (selMode) {
			case BOX -> {
				Pt a = sel.first();
				Pt b = sel.second();
				pts = List.of(a, b);
				fp = Geometry.box(a, b);
				y0 = Math.min(a.y(), b.y());
				y1 = Math.max(a.y(), b.y());
				shape = "Box " + Fmt.size3((long) fp.maxX() - fp.minX() + 1, (long) y1 - y0 + 1, (long) fp.maxZ() - fp.minZ() + 1);
			}
			case POLYGON -> {
				pts = sel.points();
				fp = new Geometry.PolygonFootprint(pts);
				y0 = Integer.MAX_VALUE;
				y1 = Integer.MIN_VALUE;
				for (Pt p : pts) {
					y0 = Math.min(y0, p.y());
					y1 = Math.max(y1, p.y());
				}
				shape = "Polygon with " + pts.size() + " corners";
			}
			default -> {
				Pt c = sel.first();
				Pt e = sel.second();
				double r = Geometry.horizontal(c, e);
				if (r <= 0) {
					send(mc, Component.literal("Pick an edge block to the side of the center first").withStyle(ChatFormatting.RED));
					return;
				}
				pts = List.of(c, e);
				fp = new Geometry.DiscFootprint(c.x(), c.z(), r);
				y0 = Math.min(c.y(), e.y());
				y1 = Math.max(c.y(), e.y());
				shape = "Circle, radius " + Fmt.dec(r);
			}
		}

		ClientLevel level = mc.level;
		int worldMin = level.getMinY();
		int worldMax = level.getMaxY();
		y0 = Math.max(y0, worldMin);
		y1 = Math.min(y1, worldMax);
		if (y0 > y1) {
			send(mc, Component.literal("That selection is outside the world height").withStyle(ChatFormatting.RED));
			return;
		}

		long columns = fp.columns();
		long total = columns * ((long) y1 - y0 + 1);
		TapeConfig config = tape.config();
		if (total > config.countMaxVolume) {
			send(mc, Component.literal("Too big to count: " + Fmt.num(total) + " blocks (limit " + Fmt.num(config.countMaxVolume)
				+ ", change countMaxVolume in the config)").withStyle(ChatFormatting.RED));
			return;
		}

		this.running = true;
		this.revision = sel.revision();
		this.dimension = dim;
		this.mode = selMode;
		this.shapeText = shape;
		this.footprint = fp;
		this.minY = y0;
		this.maxY = y1;
		this.totalBlocks = total;
		this.counts.clear();
		this.scanned = 0;
		this.air = 0;
		this.unloaded = 0;
		this.lastSummary = null;
		this.z = fp.minZ();
		this.spans = fp.row(this.z);
		this.spanIndex = 0;
		this.x = this.spans.length > 0 ? this.spans[0] : 0;
		this.y = y0;
		send(mc, Component.literal("Counting " + Fmt.num(total) + " blocks in " + shape + "...").withStyle(ChatFormatting.GRAY));
	}

	public void tick(Minecraft mc) {
		if (!this.running) {
			return;
		}
		ClientLevel level = mc.level;
		if (level == null || !this.dimension.equals(TapeMeasureClient.dimensionOf(mc))) {
			this.running = false;
			return;
		}
		long budget = TapeMeasureClient.get().config().countBlocksPerTick;
		while (budget > 0) {
			// advance to a column that exists
			while (this.spanIndex >= this.spans.length) {
				this.z++;
				if (this.z > this.footprint.maxZ()) {
					finish(mc);
					return;
				}
				this.spans = this.footprint.row(this.z);
				this.spanIndex = 0;
				if (this.spans.length > 0) {
					this.x = this.spans[0];
					this.y = this.minY;
				}
			}

			int height = this.maxY - this.y + 1;
			if (!level.hasChunk(this.x >> 4, this.z >> 4)) {
				this.unloaded += height;
				this.scanned += height;
				budget -= 16;
				nextColumn();
				continue;
			}

			int steps = (int) Math.min(budget, height);
			for (int i = 0; i < steps; i++) {
				this.cursor.set(this.x, this.y + i, this.z);
				BlockState state = level.getBlockState(this.cursor);
				if (state.isAir()) {
					this.air++;
				} else {
					this.counts.computeIfAbsent(state.getBlock(), k -> new long[1])[0]++;
				}
			}
			this.scanned += steps;
			budget -= steps;
			this.y += steps;
			if (this.y > this.maxY) {
				nextColumn();
			}
		}
	}

	private void nextColumn() {
		this.y = this.minY;
		this.x++;
		if (this.x > this.spans[this.spanIndex + 1]) {
			this.spanIndex += 2;
			if (this.spanIndex < this.spans.length) {
				this.x = this.spans[this.spanIndex];
			}
		}
	}

	private void finish(Minecraft mc) {
		this.running = false;
		List<Map.Entry<Block, long[]>> entries = new ArrayList<>(this.counts.entrySet());
		entries.sort((p, q) -> Long.compare(q.getValue()[0], p.getValue()[0]));
		long solid = 0;
		for (Map.Entry<Block, long[]> e : entries) {
			solid += e.getValue()[0];
		}
		long loadedTotal = this.scanned - this.unloaded;

		send(mc, Component.literal("Block count: ").withStyle(ChatFormatting.GOLD)
			.append(Component.literal(this.shapeText).withStyle(ChatFormatting.WHITE)));
		send(mc, Component.literal("  " + Fmt.num(solid) + " blocks, " + Fmt.num(this.air) + " air, "
			+ entries.size() + " block types").withStyle(ChatFormatting.GRAY));
		if (this.unloaded > 0) {
			send(mc, Component.literal("  " + Fmt.num(this.unloaded) + " blocks skipped in chunks that are not loaded")
				.withStyle(ChatFormatting.RED));
		}

		int top = TapeMeasureClient.get().config().countTopEntries;
		StringBuilder csv = new StringBuilder("block,count\n");
		for (int i = 0; i < entries.size(); i++) {
			Block block = entries.get(i).getKey();
			long n = entries.get(i).getValue()[0];
			String id = BuiltInRegistries.BLOCK.getKey(block).toString();
			csv.append(id).append(',').append(n).append('\n');
			if (i < top) {
				MutableComponent line = Component.literal("  " + Fmt.num(n) + "  ").withStyle(ChatFormatting.YELLOW)
					.append(block.getName().withStyle(ChatFormatting.WHITE))
					.append(Component.literal(n >= 64 ? "  (" + Fmt.stacks(n) + ")" : "").withStyle(ChatFormatting.DARK_GRAY));
				line.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(id))));
				send(mc, line);
			}
		}
		if (entries.size() > top) {
			send(mc, Component.literal("  ... and " + (entries.size() - top) + " more types").withStyle(ChatFormatting.GRAY));
		}
		String csvText = csv.toString();
		send(mc, Component.literal("  [Copy full list]").withStyle(style -> style
			.withColor(ChatFormatting.AQUA)
			.withClickEvent(new ClickEvent.CopyToClipboard(csvText))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal("Copies a CSV of every block type and count"))))
		);
		this.lastSummary = Fmt.num(solid) + " blocks, " + entries.size() + " types"
			+ (this.unloaded > 0 ? " (" + Fmt.num(this.unloaded) + " unloaded)" : "");
		if (loadedTotal <= 0) {
			this.lastSummary = "nothing loaded to count";
		}
	}

	private static void send(Minecraft mc, Component message) {
		mc.gui.hud.getChat().addClientSystemMessage(message);
	}

	/** Which mode the last or current count was for. */
	public Mode mode() {
		return this.mode;
	}
}
