package com.divinitysreach.tapemeasure.render;

import com.divinitysreach.tapemeasure.Selection;
import com.divinitysreach.tapemeasure.TapeMeasureClient;
import com.divinitysreach.tapemeasure.config.TapeConfig;
import com.divinitysreach.tapemeasure.math.Fmt;
import com.divinitysreach.tapemeasure.math.Geometry;
import com.divinitysreach.tapemeasure.math.Mode;
import com.divinitysreach.tapemeasure.math.Pt;
import com.divinitysreach.tapemeasure.math.Readout;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the selection in the world. Runs in Fabric's COLLECT_SUBMITS event, which is the point where extra
 * geometry can be queued for the frame that is being built.
 */
public final class MeasureRenderer {
	private static final double POINT_PAD = 0.03;
	private static final double BOX_PAD = 0.02;
	private static final int MAX_LABELS = 64;
	private static final int MAX_SEGMENT_LABELS = 32;
	/** Full brightness light value (sky 15, block 15). */
	private static final int FULL_BRIGHT = 0xF000F0;
	/**
	 * Name tags are drawn half a block above their anchor with the text hanging down from there. This offset
	 * cancels the half block and lifts the text by half its height (4.5 pixels at 0.025 blocks per pixel), so
	 * each label is centered on its point.
	 */
	private static final Vec3 LABEL_ANCHOR = new Vec3(0, -0.5 + 4.5 * 0.025, 0);

	private static final LineBatch BATCH = new LineBatch();
	private static final List<Label> LABELS = new ArrayList<>();

	private static double camX;
	private static double camY;
	private static double camZ;

	private record Label(double x, double y, double z, Component text) {
	}

	private MeasureRenderer() {
	}

	public static void render(LevelRenderContext context) {
		TapeMeasureClient tape = TapeMeasureClient.get();
		Minecraft mc = Minecraft.getInstance();
		if (tape == null || !tape.visible() || mc.level == null || mc.player == null) {
			return;
		}
		Selection.View view = tape.currentView(mc);
		if (view.isEmpty()) {
			return;
		}

		CameraRenderState camera = context.levelState().cameraRenderState;
		Vec3 cam = camera.pos;
		camX = cam.x;
		camY = cam.y;
		camZ = cam.z;
		BATCH.clear();
		LABELS.clear();

		TapeConfig config = tape.config();
		Mode mode = tape.selection().mode();
		switch (mode) {
			case BOX -> drawBox(view, config);
			case LINE -> drawLine(view, config);
			case PATH -> drawPath(view, config);
			case POLYGON -> drawPolygon(view, config, tape.currentReadout(mc));
			case CIRCLE -> drawCircle(view, config);
		}

		PoseStack poseStack = context.poseStack();
		SubmitNodeCollector collector = context.submitNodeCollector();
		if (!config.hideObstructedLines) {
			BATCH.submit(collector, poseStack, true);
		}
		BATCH.submit(collector, poseStack, false);

		if (config.showLabels) {
			for (Label label : LABELS) {
				double dx = label.x() - camX;
				double dy = label.y() - camY;
				double dz = label.z() - camZ;
				double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
				// Grow with distance so labels stay readable far away.
				float scale = (float) Math.max(1.0, Math.min(24.0, dist / 10.0));
				poseStack.pushPose();
				poseStack.translate(dx, dy, dz);
				poseStack.scale(scale, scale, scale);
				collector.submitNameTag(poseStack, LABEL_ANCHOR, 0, label.text(), true, FULL_BRIGHT, camera);
				poseStack.popPose();
			}
		}
	}

	// ------------------------------------------------------------------ modes

	private static void drawBox(Selection.View view, TapeConfig config) {
		List<Pt> pts = view.points();
		float width = config.lineWidth;
		for (int i = 0; i < pts.size(); i++) {
			pointCube(pts.get(i), pointColor(view, i, config.corner1Argb, config.corner2Argb, config), width);
		}
		if (pts.size() < 2) {
			return;
		}
		Pt a = pts.get(0);
		Pt b = pts.get(1);
		double x0 = Math.min(a.x(), b.x()) - BOX_PAD;
		double y0 = Math.min(a.y(), b.y()) - BOX_PAD;
		double z0 = Math.min(a.z(), b.z()) - BOX_PAD;
		double x1 = Math.max(a.x(), b.x()) + 1 + BOX_PAD;
		double y1 = Math.max(a.y(), b.y()) + 1 + BOX_PAD;
		double z1 = Math.max(a.z(), b.z()) + 1 + BOX_PAD;
		box(x0, y0, z0, x1, y1, z1, config.boxArgb, width);
		if (config.showGrid) {
			grid(x0, y0, z0, x1, y1, z1, config.gridArgb, Math.max(1.0f, width * 0.5f), config.gridRange);
		}

		long sx = Math.abs((long) a.x() - b.x()) + 1;
		long sy = Math.abs((long) a.y() - b.y()) + 1;
		long sz = Math.abs((long) a.z() - b.z()) + 1;
		double nearX = camX < (x0 + x1) / 2 ? x0 : x1;
		double nearY = camY < (y0 + y1) / 2 ? y0 : y1;
		double nearZ = camZ < (z0 + z1) / 2 ? z0 : z1;
		label((x0 + x1) / 2, nearY, nearZ, axisText("X", sx, ChatFormatting.RED));
		label(nearX, (y0 + y1) / 2, nearZ, axisText("Y", sy, ChatFormatting.GREEN));
		label(nearX, nearY, (z0 + z1) / 2, axisText("Z", sz, ChatFormatting.BLUE));
	}

	private static void drawLine(Selection.View view, TapeConfig config) {
		List<Pt> pts = view.points();
		float width = config.lineWidth;
		for (int i = 0; i < pts.size(); i++) {
			pointCube(pts.get(i), pointColor(view, i, config.corner1Argb, config.corner2Argb, config), width);
		}
		if (pts.size() < 2) {
			return;
		}
		Pt a = pts.get(0);
		Pt b = pts.get(1);
		centerLine(a, b, config.lineArgb, width);
		label(a.x() + 0.5 + (b.x() - a.x()) / 2.0, a.y() + 0.5 + (b.y() - a.y()) / 2.0 + 0.4, a.z() + 0.5 + (b.z() - a.z()) / 2.0,
			Component.literal(Fmt.dec(Geometry.distance(a, b))).withStyle(ChatFormatting.AQUA));
	}

	private static void drawPath(Selection.View view, TapeConfig config) {
		List<Pt> pts = view.points();
		float width = config.lineWidth;
		for (int i = 0; i < pts.size(); i++) {
			pointCube(pts.get(i), pointColor(view, i, config.corner1Argb, config.pathArgb, config), width);
		}
		double total = 0;
		boolean segmentLabels = pts.size() - 1 <= MAX_SEGMENT_LABELS;
		for (int i = 1; i < pts.size(); i++) {
			Pt p = pts.get(i - 1);
			Pt q = pts.get(i);
			centerLine(p, q, config.pathArgb, width);
			double len = Geometry.distance(p, q);
			total += len;
			if (segmentLabels && pts.size() > 2) {
				label(p.x() + 0.5 + (q.x() - p.x()) / 2.0, p.y() + 0.9 + (q.y() - p.y()) / 2.0, p.z() + 0.5 + (q.z() - p.z()) / 2.0,
					Component.literal(Fmt.dec(len, 1)).withStyle(ChatFormatting.GRAY));
			}
		}
		if (pts.size() >= 2) {
			Pt end = pts.get(pts.size() - 1);
			label(end.x() + 0.5, end.y() + 1.6, end.z() + 0.5,
				Component.literal("Total " + Fmt.dec(total)).withStyle(ChatFormatting.AQUA));
		}
	}

	private static void drawPolygon(Selection.View view, TapeConfig config, Readout readout) {
		List<Pt> pts = view.points();
		float width = config.lineWidth;
		int color = config.polygonArgb;
		for (int i = 0; i < pts.size(); i++) {
			pointCube(pts.get(i), pointColor(view, i, config.corner1Argb, color, config), width);
		}
		if (pts.size() < 2) {
			return;
		}
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		double sumX = 0;
		double sumZ = 0;
		for (Pt p : pts) {
			minY = Math.min(minY, p.y());
			maxY = Math.max(maxY, p.y());
			sumX += p.x() + 0.5;
			sumZ += p.z() + 0.5;
		}
		double bottom = minY - BOX_PAD;
		double top = maxY + 1 + BOX_PAD;
		int n = pts.size();
		int edges = n >= 3 ? n : n - 1;
		for (int i = 0; i < edges; i++) {
			Pt p = pts.get(i);
			Pt q = pts.get((i + 1) % n);
			line(p.x() + 0.5, bottom, p.z() + 0.5, q.x() + 0.5, bottom, q.z() + 0.5, color, width);
			line(p.x() + 0.5, top, p.z() + 0.5, q.x() + 0.5, top, q.z() + 0.5, color, width);
		}
		for (Pt p : pts) {
			line(p.x() + 0.5, bottom, p.z() + 0.5, p.x() + 0.5, top, p.z() + 0.5, color, Math.max(1.0f, width * 0.5f));
		}
		if (n >= 3) {
			// Use the cached readout rather than recounting the polygon every frame.
			String text = Fmt.dec(Geometry.polygonArea(pts)) + " area";
			for (Readout.Row row : readout.rows()) {
				if (row.label().equals("Per layer")) {
					text = row.value().replace(" blocks", "") + " per layer";
					break;
				}
			}
			label(sumX / n, top + 0.6, sumZ / n, Component.literal(text).withStyle(ChatFormatting.LIGHT_PURPLE));
		}
	}

	private static void drawCircle(Selection.View view, TapeConfig config) {
		List<Pt> pts = view.points();
		float width = config.lineWidth;
		int color = config.circleArgb;
		for (int i = 0; i < pts.size(); i++) {
			pointCube(pts.get(i), pointColor(view, i, config.corner1Argb, config.corner2Argb, config), width);
		}
		if (pts.size() < 2) {
			return;
		}
		Pt c = pts.get(0);
		Pt e = pts.get(1);
		double r = Geometry.horizontal(c, e);
		if (r <= 0) {
			return;
		}
		double cx = c.x() + 0.5;
		double cz = c.z() + 0.5;
		double bottom = Math.min(c.y(), e.y()) - BOX_PAD;
		double top = Math.max(c.y(), e.y()) + 1 + BOX_PAD;
		double ringRadius = r + 0.5;
		int segments = (int) Math.max(24, Math.min(360, Math.ceil(ringRadius * 2)));
		for (int i = 0; i < segments; i++) {
			double a0 = Math.PI * 2 * i / segments;
			double a1 = Math.PI * 2 * (i + 1) / segments;
			double x0 = cx + Math.cos(a0) * ringRadius;
			double z0 = cz + Math.sin(a0) * ringRadius;
			double x1 = cx + Math.cos(a1) * ringRadius;
			double z1 = cz + Math.sin(a1) * ringRadius;
			line(x0, bottom, z0, x1, bottom, z1, color, width);
			if (top - bottom > 1.5) {
				line(x0, top, z0, x1, top, z1, color, width);
			}
		}
		if (top - bottom > 1.5) {
			float thin = Math.max(1.0f, width * 0.5f);
			for (int k = 0; k < 4; k++) {
				double a = Math.PI / 2 * k;
				double x = cx + Math.cos(a) * ringRadius;
				double z = cz + Math.sin(a) * ringRadius;
				line(x, bottom, z, x, top, z, color, thin);
			}
		}
		double cy = c.y() + 0.5;
		line(cx, cy, cz, e.x() + 0.5, cy, e.z() + 0.5, color, width);
		label(cx + (e.x() - c.x()) / 2.0, cy + 0.4, cz + (e.z() - c.z()) / 2.0,
			Component.literal("r " + Fmt.dec(r)).withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	// ------------------------------------------------------------------ primitives

	private static int pointColor(Selection.View view, int index, int firstColor, int otherColor, TapeConfig config) {
		if (view.preview() && index == view.previewIndex()) {
			return config.previewArgb;
		}
		return index == 0 ? firstColor : otherColor;
	}

	private static Component axisText(String axis, long size, ChatFormatting color) {
		return Component.literal(axis + " ").withStyle(color).append(Component.literal(Fmt.num(size)).withStyle(ChatFormatting.WHITE));
	}

	private static void label(double x, double y, double z, Component text) {
		if (LABELS.size() < MAX_LABELS) {
			LABELS.add(new Label(x, y, z, text));
		}
	}

	private static void pointCube(Pt p, int argb, float width) {
		box(p.x() - POINT_PAD, p.y() - POINT_PAD, p.z() - POINT_PAD,
			p.x() + 1 + POINT_PAD, p.y() + 1 + POINT_PAD, p.z() + 1 + POINT_PAD, argb, width);
	}

	private static void centerLine(Pt a, Pt b, int argb, float width) {
		line(a.x() + 0.5, a.y() + 0.5, a.z() + 0.5, b.x() + 0.5, b.y() + 0.5, b.z() + 0.5, argb, width);
	}

	private static void box(double x0, double y0, double z0, double x1, double y1, double z1, int argb, float width) {
		// bottom
		line(x0, y0, z0, x1, y0, z0, argb, width);
		line(x1, y0, z0, x1, y0, z1, argb, width);
		line(x1, y0, z1, x0, y0, z1, argb, width);
		line(x0, y0, z1, x0, y0, z0, argb, width);
		// top
		line(x0, y1, z0, x1, y1, z0, argb, width);
		line(x1, y1, z0, x1, y1, z1, argb, width);
		line(x1, y1, z1, x0, y1, z1, argb, width);
		line(x0, y1, z1, x0, y1, z0, argb, width);
		// sides
		line(x0, y0, z0, x0, y1, z0, argb, width);
		line(x1, y0, z0, x1, y1, z0, argb, width);
		line(x1, y0, z1, x1, y1, z1, argb, width);
		line(x0, y0, z1, x0, y1, z1, argb, width);
	}

	/** One line per block on every face, skipping lines far from the camera along that axis. */
	private static void grid(double x0, double y0, double z0, double x1, double y1, double z1, int argb, float width, int range) {
		// Interior lines only; the outline already marks the outer faces.
		int fromX = (int) Math.max(Math.ceil(x0) + 1, Math.floor(camX) - range);
		int toX = (int) Math.min(Math.floor(x1) - 1, Math.floor(camX) + range);
		for (int x = fromX; x <= toX; x++) {
			line(x, y0, z0, x, y1, z0, argb, width);
			line(x, y0, z1, x, y1, z1, argb, width);
			line(x, y0, z0, x, y0, z1, argb, width);
			line(x, y1, z0, x, y1, z1, argb, width);
		}
		int fromY = (int) Math.max(Math.ceil(y0) + 1, Math.floor(camY) - range);
		int toY = (int) Math.min(Math.floor(y1) - 1, Math.floor(camY) + range);
		for (int y = fromY; y <= toY; y++) {
			line(x0, y, z0, x1, y, z0, argb, width);
			line(x0, y, z1, x1, y, z1, argb, width);
			line(x0, y, z0, x0, y, z1, argb, width);
			line(x1, y, z0, x1, y, z1, argb, width);
		}
		int fromZ = (int) Math.max(Math.ceil(z0) + 1, Math.floor(camZ) - range);
		int toZ = (int) Math.min(Math.floor(z1) - 1, Math.floor(camZ) + range);
		for (int z = fromZ; z <= toZ; z++) {
			line(x0, y0, z, x1, y0, z, argb, width);
			line(x0, y1, z, x1, y1, z, argb, width);
			line(x0, y0, z, x0, y1, z, argb, width);
			line(x1, y0, z, x1, y1, z, argb, width);
		}
	}

	private static void line(double x0, double y0, double z0, double x1, double y1, double z1, int argb, float width) {
		BATCH.line(x0 - camX, y0 - camY, z0 - camZ, x1 - camX, y1 - camY, z1 - camZ, argb, width);
	}
}
