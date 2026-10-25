package com.divinitysreach.tapemeasure.math;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The numbers shown for a measurement. Rows marked {@code hud} appear in the on screen panel; every row
 * appears in the chat summary from {@code /measure info}.
 */
public final class Readout {
	/** Largest radius for which sphere counts are worked out (the work grows with the square of the radius). */
	public static final double MAX_SPHERE_RADIUS = 1024;
	/** Smaller limit while the edge follows the crosshair, so a big preview never stutters. */
	public static final double MAX_SPHERE_RADIUS_PREVIEW = 160;
	/** Largest polygon depth (in rows) for which block counts are worked out. */
	public static final int MAX_POLYGON_ROWS = 100_000;

	public enum Dim {
		OVERWORLD,
		NETHER,
		OTHER
	}

	public record Row(String label, String value, boolean hud) {
	}

	private final Mode mode;
	private final boolean preview;
	private final boolean complete;
	private final List<Row> rows;

	private Readout(Mode mode, boolean preview, boolean complete, List<Row> rows) {
		this.mode = mode;
		this.preview = preview;
		this.complete = complete;
		this.rows = Collections.unmodifiableList(rows);
	}

	public Mode mode() {
		return this.mode;
	}

	public boolean preview() {
		return this.preview;
	}

	public boolean complete() {
		return this.complete;
	}

	public List<Row> rows() {
		return this.rows;
	}

	public String title() {
		return this.mode.label() + (this.preview ? " (preview)" : "");
	}

	/** Plain text version, one row per line, for copying. */
	public String toPlainText() {
		StringBuilder sb = new StringBuilder("Tape Measure: ").append(title());
		for (Row row : this.rows) {
			sb.append('\n').append(row.label()).append(": ").append(row.value());
		}
		return sb.toString();
	}

	public static Readout build(Mode mode, List<Pt> pts, boolean preview, Dim dim) {
		List<Row> rows = new ArrayList<>();
		boolean complete = switch (mode) {
			case BOX -> box(pts, rows);
			case LINE -> line(pts, rows, dim);
			case PATH -> path(pts, rows);
			case POLYGON -> polygon(pts, rows);
			case CIRCLE -> circle(pts, rows, preview);
		};
		return new Readout(mode, preview, complete, rows);
	}

	// ------------------------------------------------------------------ modes

	private static boolean box(List<Pt> pts, List<Row> rows) {
		if (pts.size() < 2) {
			if (pts.size() == 1) {
				rows.add(hud("Corner", pts.get(0).coords()));
			}
			return false;
		}
		Pt a = pts.get(0);
		Pt b = pts.get(1);
		long sx = Math.abs((long) a.x() - b.x()) + 1;
		long sy = Math.abs((long) a.y() - b.y()) + 1;
		long sz = Math.abs((long) a.z() - b.z()) + 1;
		long volume = sx * sy * sz;
		long ring = Geometry.ring(sx, sz);
		long cw = Geometry.chunkSpan(Math.min(a.x(), b.x()), Math.max(a.x(), b.x()));
		long cl = Geometry.chunkSpan(Math.min(a.z(), b.z()), Math.max(a.z(), b.z()));

		rows.add(hud("Size", Fmt.size3(sx, sy, sz)));
		rows.add(hud("Volume", Fmt.num(volume)));
		rows.add(hud("Floor", Fmt.num(sx * sz)));
		rows.add(hud("Walls", Fmt.num(ring * sy) + " (" + Fmt.num(ring) + " per layer)"));
		rows.add(hud("Hollow box", Fmt.num(Geometry.shell(sx, sy, sz))));
		rows.add(hud("Fill", Fmt.stacks(volume) + " · " + Fmt.shulkers(volume)));
		rows.add(hud("Chunks", Fmt.num(cw * cl) + " (" + Fmt.num(cw) + " × " + Fmt.num(cl) + ")"));
		rows.add(chat("Corner 1", a.coords()));
		rows.add(chat("Corner 2", b.coords()));
		rows.add(chat("Min corner", Math.min(a.x(), b.x()) + ", " + Math.min(a.y(), b.y()) + ", " + Math.min(a.z(), b.z())));
		rows.add(chat("Max corner", Math.max(a.x(), b.x()) + ", " + Math.max(a.y(), b.y()) + ", " + Math.max(a.z(), b.z())));
		rows.add(chat("Corner to corner", Fmt.dec(Geometry.distance(a, b))));
		long rw = (long) Math.floorDiv(Math.max(a.x(), b.x()), 512) - Math.floorDiv(Math.min(a.x(), b.x()), 512) + 1;
		long rl = (long) Math.floorDiv(Math.max(a.z(), b.z()), 512) - Math.floorDiv(Math.min(a.z(), b.z()), 512) + 1;
		rows.add(chat("Region files", Fmt.num(rw * rl)));
		return true;
	}

	private static boolean line(List<Pt> pts, List<Row> rows, Dim dim) {
		if (pts.size() < 2) {
			if (pts.size() == 1) {
				rows.add(hud("Start", pts.get(0).coords()));
			}
			return false;
		}
		Pt a = pts.get(0);
		Pt b = pts.get(1);
		double dist = Geometry.distance(a, b);
		double flat = Geometry.horizontal(a, b);
		rows.add(hud("Distance", Fmt.dec(dist)));
		rows.add(hud("Horizontal", Fmt.dec(flat)));
		rows.add(hud("Delta", "X " + Fmt.signed(b.x() - a.x()) + " · Y " + Fmt.signed(b.y() - a.y()) + " · Z " + Fmt.signed(b.z() - a.z())));
		if (flat > 0) {
			double bearing = Geometry.bearing(a, b);
			rows.add(hud("Heading", Fmt.dec(bearing, 0) + "° " + Fmt.compass(bearing) + " · yaw " + Fmt.dec(Geometry.yaw(a, b), 1)));
		}
		if (b.y() != a.y()) {
			if (flat > 0) {
				double grade = (b.y() - a.y()) / flat * 100.0;
				rows.add(hud("Slope", Fmt.dec(Geometry.pitch(a, b), 1) + "° (" + Fmt.dec(grade, 0) + "% grade)"));
			} else {
				rows.add(hud("Slope", "straight " + (b.y() > a.y() ? "up" : "down")));
			}
		}
		rows.add(hud("Line blocks", Fmt.num(Geometry.lineBlocks(a, b))));
		if (dim == Dim.OVERWORLD) {
			rows.add(hud("In the Nether", Fmt.dec(flat / 8.0)));
			rows.add(chat("Start in Nether", Math.floorDiv(a.x(), 8) + ", " + Math.floorDiv(a.z(), 8)));
			rows.add(chat("End in Nether", Math.floorDiv(b.x(), 8) + ", " + Math.floorDiv(b.z(), 8)));
		} else if (dim == Dim.NETHER) {
			rows.add(hud("In the Overworld", Fmt.dec(flat * 8.0)));
			rows.add(chat("Start in Overworld", (long) a.x() * 8 + ", " + (long) a.z() * 8));
			rows.add(chat("End in Overworld", (long) b.x() * 8 + ", " + (long) b.z() * 8));
		}
		rows.add(chat("Walking distance", Fmt.num(Geometry.manhattan(a, b)) + " (no diagonals)"));
		rows.add(chat("Start", a.coords()));
		rows.add(chat("End", b.coords()));
		return true;
	}

	private static boolean path(List<Pt> pts, List<Row> rows) {
		int n = pts.size();
		if (n < 2) {
			if (n == 1) {
				rows.add(hud("Start", pts.get(0).coords()));
			}
			return false;
		}
		double length = 0;
		double flat = 0;
		long climb = 0;
		long drop = 0;
		long blocks = 1;
		for (int i = 1; i < n; i++) {
			Pt p = pts.get(i - 1);
			Pt q = pts.get(i);
			length += Geometry.distance(p, q);
			flat += Geometry.horizontal(p, q);
			int dy = q.y() - p.y();
			if (dy > 0) {
				climb += dy;
			} else {
				drop -= dy;
			}
			blocks += Geometry.lineBlocks(p, q) - 1;
		}
		Pt last = pts.get(n - 2);
		Pt end = pts.get(n - 1);
		rows.add(hud("Points", Fmt.num(n)));
		rows.add(hud("Length", Fmt.dec(length)));
		rows.add(hud("Horizontal", Fmt.dec(flat)));
		if (climb != 0 || drop != 0) {
			rows.add(hud("Climb", "+" + Fmt.num(climb) + " / -" + Fmt.num(drop)));
		}
		rows.add(hud("Line blocks", Fmt.num(blocks)));
		String leg = Fmt.dec(Geometry.distance(last, end));
		if (Geometry.horizontal(last, end) > 0) {
			double bearing = Geometry.bearing(last, end);
			leg += " · " + Fmt.dec(bearing, 0) + "° " + Fmt.compass(bearing);
		}
		rows.add(hud("Last leg", leg));
		rows.add(hud("Start to end", Fmt.dec(Geometry.distance(pts.get(0), end))));
		rows.add(chat("Start", pts.get(0).coords()));
		rows.add(chat("End", end.coords()));
		return true;
	}

	private static boolean polygon(List<Pt> pts, List<Row> rows) {
		int n = pts.size();
		if (n < 3) {
			rows.add(hud("Corners", n + " of at least 3"));
			return false;
		}
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		for (Pt p : pts) {
			minY = Math.min(minY, p.y());
			maxY = Math.max(maxY, p.y());
		}
		long height = (long) maxY - minY + 1;
		Geometry.PolygonFootprint footprint = new Geometry.PolygonFootprint(pts);
		rows.add(hud("Corners", Fmt.num(n)));
		if ((long) footprint.maxZ() - footprint.minZ() <= MAX_POLYGON_ROWS) {
			long layer = footprint.columns();
			long outline = footprint.boundaryColumns();
			rows.add(hud("Per layer", Fmt.num(layer) + " blocks"));
			rows.add(hud("Height", Fmt.num(height) + " (Y " + minY + " to " + maxY + ")"));
			rows.add(hud("Volume", Fmt.num(layer * height)));
			rows.add(hud("Outline", Fmt.num(outline) + " per layer"));
			rows.add(hud("Walls", Fmt.num(outline * height)));
			rows.add(chat("Fill", Fmt.stacks(layer * height) + " · " + Fmt.shulkers(layer * height)));
		} else {
			rows.add(hud("Per layer", "too large to count"));
		}
		rows.add(hud("Perimeter", Fmt.dec(Geometry.polygonPerimeter(pts))));
		rows.add(hud("Exact area", Fmt.dec(Geometry.polygonArea(pts))));
		long cw = Geometry.chunkSpan(footprint.minX(), footprint.maxX());
		long cl = Geometry.chunkSpan(footprint.minZ(), footprint.maxZ());
		rows.add(chat("Bounding box", Fmt.size3((long) footprint.maxX() - footprint.minX() + 1, height, (long) footprint.maxZ() - footprint.minZ() + 1)));
		rows.add(chat("Chunks (bounds)", Fmt.num(cw * cl)));
		return true;
	}

	private static boolean circle(List<Pt> pts, List<Row> rows, boolean preview) {
		if (pts.size() < 2) {
			if (pts.size() == 1) {
				rows.add(hud("Center", pts.get(0).coords()));
			}
			return false;
		}
		Pt c = pts.get(0);
		Pt e = pts.get(1);
		double r = Geometry.horizontal(c, e);
		if (r <= 0) {
			rows.add(hud("Center", c.coords()));
			rows.add(hud("Radius", "pick an edge block to the side"));
			return false;
		}
		Geometry.DiscFootprint disc = new Geometry.DiscFootprint(c.x(), c.z(), r);
		long discBlocks = disc.columns();
		long ring = disc.ringColumns();
		long height = Math.abs((long) e.y() - c.y()) + 1;
		rows.add(hud("Radius", Fmt.dec(r) + " (width " + Fmt.num(2L * disc.reach() + 1) + ")"));
		rows.add(hud("Disc", Fmt.num(discBlocks) + " blocks"));
		rows.add(hud("Ring", Fmt.num(ring) + " blocks"));
		rows.add(hud("Circumference", Fmt.dec(2 * Math.PI * r)));
		rows.add(hud("Exact area", Fmt.dec(Math.PI * r * r)));
		if (height > 1) {
			rows.add(hud("Cylinder", Fmt.num(discBlocks * height) + " (" + height + " high)"));
			rows.add(hud("Tube walls", Fmt.num(ring * height)));
		}
		if (r <= (preview ? MAX_SPHERE_RADIUS_PREVIEW : MAX_SPHERE_RADIUS)) {
			rows.add(hud("Sphere", Fmt.num(Geometry.sphereBlocks(r, false)) + " · dome " + Fmt.num(Geometry.sphereBlocks(r, true))));
		}
		rows.add(chat("Center", c.coords()));
		rows.add(chat("Edge", e.coords()));
		return true;
	}

	private static Row hud(String label, String value) {
		return new Row(label, value, true);
	}

	private static Row chat(String label, String value) {
		return new Row(label, value, false);
	}
}
