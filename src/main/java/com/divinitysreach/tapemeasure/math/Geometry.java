package com.divinitysreach.tapemeasure.math;

import java.util.Arrays;
import java.util.List;

/**
 * Pure measuring math. Everything works on whole block coordinates.
 *
 * <p>A {@link Footprint} describes which block columns (x, z) belong to a flat shape. Box, polygon and disc
 * footprints are used both for the readout numbers and by the block counter, so the counter and the readout
 * always agree on what is "inside".</p>
 */
public final class Geometry {
	private Geometry() {
	}

	// ------------------------------------------------------------------ basic distances

	public static double distance(Pt a, Pt b) {
		double dx = b.x() - a.x();
		double dy = b.y() - a.y();
		double dz = b.z() - a.z();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	public static double horizontal(Pt a, Pt b) {
		double dx = b.x() - a.x();
		double dz = b.z() - a.z();
		return Math.sqrt(dx * dx + dz * dz);
	}

	public static long manhattan(Pt a, Pt b) {
		return (long) Math.abs(b.x() - a.x()) + Math.abs(b.y() - a.y()) + Math.abs(b.z() - a.z());
	}

	/** Blocks needed for a straight 3D line of blocks between two blocks, both ends included. */
	public static long lineBlocks(Pt a, Pt b) {
		return Math.max(Math.abs(b.x() - a.x()), Math.max(Math.abs(b.y() - a.y()), Math.abs(b.z() - a.z()))) + 1L;
	}

	/** Compass bearing from a to b in degrees: 0 is north (negative Z), 90 is east (positive X). */
	public static double bearing(Pt a, Pt b) {
		double dx = b.x() - a.x();
		double dz = b.z() - a.z();
		double deg = Math.toDegrees(Math.atan2(dx, -dz));
		return deg < 0 ? deg + 360.0 : deg;
	}

	/** Minecraft yaw (as shown on the F3 screen) that faces from a towards b. */
	public static double yaw(Pt a, Pt b) {
		double yaw = bearing(a, b) - 180.0;
		if (yaw <= -180.0) {
			yaw += 360.0;
		}
		return yaw;
	}

	/** Slope angle in degrees, positive when b is higher than a. */
	public static double pitch(Pt a, Pt b) {
		return Math.toDegrees(Math.atan2(b.y() - a.y(), horizontal(a, b)));
	}

	public static long gcd(long a, long b) {
		a = Math.abs(a);
		b = Math.abs(b);
		while (b != 0) {
			long t = a % b;
			a = b;
			b = t;
		}
		return a;
	}

	// ------------------------------------------------------------------ box

	public static long chunkSpan(int min, int max) {
		return (long) Math.floorDiv(max, 16) - Math.floorDiv(min, 16) + 1L;
	}

	/** Blocks in one layer of a hollow ring that traces the outside of a w by l rectangle. */
	public static long ring(long w, long l) {
		if (w <= 0 || l <= 0) {
			return 0;
		}
		if (w <= 2 || l <= 2) {
			return w * l;
		}
		return 2 * (w + l) - 4;
	}

	/** Blocks in a hollow box (walls, floor and ceiling) of the given outer size. */
	public static long shell(long w, long h, long l) {
		long inner = Math.max(0, w - 2) * Math.max(0, h - 2) * Math.max(0, l - 2);
		return w * h * l - inner;
	}

	// ------------------------------------------------------------------ polygon helpers

	/** Geometric area enclosed by the polygon through the block centers (shoelace formula). */
	public static double polygonArea(List<Pt> pts) {
		int n = pts.size();
		if (n < 3) {
			return 0;
		}
		long twice = 0;
		for (int i = 0; i < n; i++) {
			Pt p = pts.get(i);
			Pt q = pts.get((i + 1) % n);
			twice += (long) p.x() * q.z() - (long) q.x() * p.z();
		}
		return Math.abs(twice) / 2.0;
	}

	/** Horizontal length of the closed outline. */
	public static double polygonPerimeter(List<Pt> pts) {
		int n = pts.size();
		if (n < 2) {
			return 0;
		}
		double sum = 0;
		for (int i = 0; i < n; i++) {
			sum += horizontal(pts.get(i), pts.get((i + 1) % n));
		}
		return sum;
	}

	// ------------------------------------------------------------------ footprints

	/** A set of block columns, queried one Z row at a time. */
	public interface Footprint {
		int minX();

		int maxX();

		int minZ();

		int maxZ();

		/**
		 * Inclusive X ranges of the columns in row {@code z}, as a flat array {@code [x0, x1, x0, x1, ...]},
		 * sorted and not overlapping.
		 */
		int[] row(int z);

		/** Total number of columns. */
		default long columns() {
			long total = 0;
			for (int z = minZ(); z <= maxZ(); z++) {
				total += spanLength(row(z));
			}
			return total;
		}
	}

	public static long spanLength(int[] spans) {
		long total = 0;
		for (int i = 0; i < spans.length; i += 2) {
			total += (long) spans[i + 1] - spans[i] + 1;
		}
		return total;
	}

	public static boolean spanContains(int[] spans, int x) {
		for (int i = 0; i < spans.length; i += 2) {
			if (x >= spans[i] && x <= spans[i + 1]) {
				return true;
			}
		}
		return false;
	}

	public static Footprint box(Pt a, Pt b) {
		final int minX = Math.min(a.x(), b.x());
		final int maxX = Math.max(a.x(), b.x());
		final int minZ = Math.min(a.z(), b.z());
		final int maxZ = Math.max(a.z(), b.z());
		final int[] span = {minX, maxX};
		return new Footprint() {
			@Override
			public int minX() {
				return minX;
			}

			@Override
			public int maxX() {
				return maxX;
			}

			@Override
			public int minZ() {
				return minZ;
			}

			@Override
			public int maxZ() {
				return maxZ;
			}

			@Override
			public int[] row(int z) {
				return z < minZ || z > maxZ ? new int[0] : span;
			}

			@Override
			public long columns() {
				return ((long) maxX - minX + 1) * ((long) maxZ - minZ + 1);
			}
		};
	}

	/**
	 * Polygon with corners on block coordinates. A column counts when its block position lies inside the
	 * polygon or exactly on one of its edges, so the outline blocks themselves are always included.
	 */
	public static final class PolygonFootprint implements Footprint {
		private final int[] xs;
		private final int[] zs;
		private final int n;
		private final int minX;
		private final int maxX;
		private final int minZ;
		private final int maxZ;

		public PolygonFootprint(List<Pt> pts) {
			this.n = pts.size();
			this.xs = new int[this.n];
			this.zs = new int[this.n];
			int x0 = Integer.MAX_VALUE;
			int x1 = Integer.MIN_VALUE;
			int z0 = Integer.MAX_VALUE;
			int z1 = Integer.MIN_VALUE;
			for (int i = 0; i < this.n; i++) {
				Pt p = pts.get(i);
				this.xs[i] = p.x();
				this.zs[i] = p.z();
				x0 = Math.min(x0, p.x());
				x1 = Math.max(x1, p.x());
				z0 = Math.min(z0, p.z());
				z1 = Math.max(z1, p.z());
			}
			this.minX = x0;
			this.maxX = x1;
			this.minZ = z0;
			this.maxZ = z1;
		}

		@Override
		public int minX() {
			return this.minX;
		}

		@Override
		public int maxX() {
			return this.maxX;
		}

		@Override
		public int minZ() {
			return this.minZ;
		}

		@Override
		public int maxZ() {
			return this.maxZ;
		}

		@Override
		public int[] row(int z) {
			if (this.n == 0 || z < this.minZ || z > this.maxZ) {
				return new int[0];
			}
			int[] spans = new int[(this.n * 2 + this.n) * 2];
			int count = 0;

			if (this.n >= 3) {
				// Interior: even odd crossings with a half open rule, so a vertex is never counted twice.
				double[] crossings = new double[this.n];
				int c = 0;
				for (int i = 0; i < this.n; i++) {
					int j = (i + 1) % this.n;
					int za = this.zs[i];
					int zb = this.zs[j];
					if ((za <= z && z < zb) || (zb <= z && z < za)) {
						int xa = this.xs[i];
						int xb = this.xs[j];
						crossings[c++] = xa + (double) ((long) (z - za) * (xb - xa)) / (zb - za);
					}
				}
				Arrays.sort(crossings, 0, c);
				for (int k = 0; k + 1 < c; k += 2) {
					int lo = (int) Math.ceil(crossings[k]);
					int hi = (int) Math.floor(crossings[k + 1]);
					if (lo <= hi) {
						spans[count++] = lo;
						spans[count++] = hi;
					}
				}
			}

			count = addBoundary(z, spans, count);
			return merge(spans, count);
		}

		/** Columns in this row that sit exactly on the outline. */
		public int[] boundaryRow(int z) {
			if (this.n == 0 || z < this.minZ || z > this.maxZ) {
				return new int[0];
			}
			int[] spans = new int[this.n * 4 + 4];
			int count = addBoundary(z, spans, 0);
			return merge(spans, count);
		}

		public long boundaryColumns() {
			long total = 0;
			for (int z = this.minZ; z <= this.maxZ; z++) {
				total += spanLength(boundaryRow(z));
			}
			return total;
		}

		private int addBoundary(int z, int[] spans, int count) {
			int edges = this.n >= 3 ? this.n : this.n - 1;
			if (this.n == 1) {
				if (this.zs[0] == z) {
					spans[count++] = this.xs[0];
					spans[count++] = this.xs[0];
				}
				return count;
			}
			for (int i = 0; i < edges; i++) {
				int j = (i + 1) % this.n;
				int xa = this.xs[i];
				int za = this.zs[i];
				int xb = this.xs[j];
				int zb = this.zs[j];
				if (za == zb) {
					if (za == z) {
						spans[count++] = Math.min(xa, xb);
						spans[count++] = Math.max(xa, xb);
					}
				} else if (z >= Math.min(za, zb) && z <= Math.max(za, zb)) {
					long num = (long) (z - za) * (xb - xa);
					long den = zb - za;
					if (num % den == 0) {
						int x = (int) (xa + num / den);
						spans[count++] = x;
						spans[count++] = x;
					}
				}
			}
			return count;
		}
	}

	/** Sorts and merges inclusive spans held in the first {@code count} entries of a flat array. */
	static int[] merge(int[] spans, int count) {
		int pairs = count / 2;
		if (pairs == 0) {
			return new int[0];
		}
		long[] packed = new long[pairs];
		for (int i = 0; i < pairs; i++) {
			// Start in the high half (signed, so the sort orders by start), end in the low half.
			packed[i] = ((long) spans[i * 2] << 32) | (spans[i * 2 + 1] & 0xFFFFFFFFL);
		}
		Arrays.sort(packed);
		int[] out = new int[pairs * 2];
		int o = 0;
		for (long p : packed) {
			int lo = (int) (p >> 32);
			int hi = (int) p;
			if (o > 0 && lo <= (long) out[o - 1] + 1) {
				out[o - 1] = Math.max(out[o - 1], hi);
			} else {
				out[o++] = lo;
				out[o++] = hi;
			}
		}
		return Arrays.copyOf(out, o);
	}

	/**
	 * Filled disc around a center block. A column counts when the distance between its center and the center
	 * block's center is at most {@code radius}, so the block you click as the edge is always inside.
	 */
	public static final class DiscFootprint implements Footprint {
		private final int cx;
		private final int cz;
		private final double r2;
		private final int reach;

		public DiscFootprint(int cx, int cz, double radius) {
			this.cx = cx;
			this.cz = cz;
			this.r2 = radius * radius + 1.0E-7;
			this.reach = (int) Math.floor(Math.sqrt(this.r2));
		}

		/** Half width of the disc in row offset {@code dz}, or -1 when the row is empty. */
		public int halfWidth(int dz) {
			double rem = this.r2 - (double) dz * dz;
			return rem < 0 ? -1 : (int) Math.floor(Math.sqrt(rem));
		}

		public int reach() {
			return this.reach;
		}

		@Override
		public int minX() {
			return this.cx - this.reach;
		}

		@Override
		public int maxX() {
			return this.cx + this.reach;
		}

		@Override
		public int minZ() {
			return this.cz - this.reach;
		}

		@Override
		public int maxZ() {
			return this.cz + this.reach;
		}

		@Override
		public int[] row(int z) {
			int m = halfWidth(z - this.cz);
			return m < 0 ? new int[0] : new int[] {this.cx - m, this.cx + m};
		}

		@Override
		public long columns() {
			long total = 0;
			for (int dz = -this.reach; dz <= this.reach; dz++) {
				int m = halfWidth(dz);
				if (m >= 0) {
					total += 2L * m + 1;
				}
			}
			return total;
		}

		/** Columns of the disc that touch the outside on at least one side (the outline you would build). */
		public long ringColumns() {
			long total = 0;
			for (int dz = -this.reach; dz <= this.reach; dz++) {
				int m = halfWidth(dz);
				if (m < 0) {
					continue;
				}
				int k = Math.min(halfWidth(dz - 1), halfWidth(dz + 1));
				if (k < 0) {
					total += 2L * m + 1;
				} else if (k >= m) {
					total += m == 0 ? 1 : 2;
				} else {
					total += 2L * (m - k);
				}
			}
			return total;
		}
	}

	/** Blocks in a solid sphere whose block centers are within {@code radius} of the center block's center. */
	public static long sphereBlocks(double radius, boolean upperHalfOnly) {
		double r2 = radius * radius + 1.0E-7;
		int reach = (int) Math.floor(Math.sqrt(r2));
		long total = 0;
		for (int dy = upperHalfOnly ? 0 : -reach; dy <= reach; dy++) {
			for (int dx = -reach; dx <= reach; dx++) {
				double rem = r2 - (double) dy * dy - (double) dx * dx;
				if (rem >= 0) {
					total += 2L * (long) Math.floor(Math.sqrt(rem)) + 1;
				}
			}
		}
		return total;
	}
}
