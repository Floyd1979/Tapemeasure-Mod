package com.divinitysreach.tapemeasure;

import com.divinitysreach.tapemeasure.math.Mode;
import com.divinitysreach.tapemeasure.math.Pt;
import com.divinitysreach.tapemeasure.math.Side;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * What the player has picked. Box, line and circle share one pair of points; path and polygon share one list.
 * Points always belong to a single dimension; picking a point somewhere else starts over.
 */
public final class Selection {
	public static final int MAX_POINTS = 256;

	private Mode mode = Mode.BOX;
	private Pt first;
	private Pt second;
	private final List<Pt> points = new ArrayList<>();
	private String dimension;
	private int revision;

	/** Points to draw and measure, with the crosshair preview filled in where a point is still missing. */
	public record View(List<Pt> points, boolean preview, int previewIndex) {
		public static final View EMPTY = new View(List.of(), false, -1);

		public boolean isEmpty() {
			return this.points.isEmpty();
		}
	}

	public Mode mode() {
		return this.mode;
	}

	public void setMode(Mode mode) {
		if (this.mode != mode) {
			this.mode = mode;
			this.revision++;
		}
	}

	public int revision() {
		return this.revision;
	}

	public String dimension() {
		return this.dimension;
	}

	public Pt first() {
		return this.first;
	}

	public Pt second() {
		return this.second;
	}

	public List<Pt> points() {
		return Collections.unmodifiableList(this.points);
	}

	/** Left click: first corner, start, or center. In path and polygon modes this starts a new list. */
	public void primary(Pt p, String dim) {
		enterDimension(dim);
		if (this.mode.twoPoint()) {
			this.first = p;
		} else {
			this.points.clear();
			this.points.add(p);
		}
		this.revision++;
	}

	/**
	 * Right click: second corner, end, or edge. In path and polygon modes this adds a point.
	 *
	 * @return false when the point list is full
	 */
	public boolean secondary(Pt p, String dim) {
		enterDimension(dim);
		if (this.mode.twoPoint()) {
			this.second = p;
		} else {
			if (this.points.size() >= MAX_POINTS) {
				return false;
			}
			if (!this.points.isEmpty() && this.points.get(this.points.size() - 1).equals(p)) {
				return true;
			}
			this.points.add(p);
		}
		this.revision++;
		return true;
	}

	/** Removes the most recent point of the current mode. */
	public boolean undo() {
		boolean changed;
		if (this.mode.twoPoint()) {
			if (this.second != null) {
				this.second = null;
				changed = true;
			} else if (this.first != null) {
				this.first = null;
				changed = true;
			} else {
				changed = false;
			}
		} else {
			changed = !this.points.isEmpty();
			if (changed) {
				this.points.remove(this.points.size() - 1);
			}
		}
		if (changed) {
			this.revision++;
		}
		return changed;
	}

	public void clear() {
		this.first = null;
		this.second = null;
		this.points.clear();
		this.dimension = null;
		this.revision++;
	}

	public boolean isEmpty() {
		return this.mode.twoPoint() ? this.first == null && this.second == null : this.points.isEmpty();
	}

	/** Whether the current mode has every point it needs (without any preview). */
	public boolean isComplete() {
		return switch (this.mode) {
			case BOX, LINE, CIRCLE -> this.first != null && this.second != null;
			case PATH -> this.points.size() >= 2;
			case POLYGON -> this.points.size() >= 3;
		};
	}

	public int count() {
		if (this.mode.twoPoint()) {
			return (this.first != null ? 1 : 0) + (this.second != null ? 1 : 0);
		}
		return this.points.size();
	}

	/**
	 * Builds the view for drawing and measuring.
	 *
	 * @param currentDim the dimension the player is in now; nothing is shown in other dimensions
	 * @param preview the block under the crosshair, or null when there is no preview
	 */
	public View view(String currentDim, Pt preview) {
		if (this.dimension != null && !Objects.equals(this.dimension, currentDim)) {
			return View.EMPTY;
		}
		if (this.mode.twoPoint()) {
			if (this.first != null && this.second != null) {
				return new View(List.of(this.first, this.second), false, -1);
			}
			if (this.first != null) {
				return preview != null ? new View(List.of(this.first, preview), true, 1) : new View(List.of(this.first), false, -1);
			}
			if (this.second != null) {
				return preview != null ? new View(List.of(preview, this.second), true, 0) : new View(List.of(this.second), false, -1);
			}
			return View.EMPTY;
		}
		if (this.points.isEmpty()) {
			return View.EMPTY;
		}
		Pt last = this.points.get(this.points.size() - 1);
		if (preview != null && !preview.equals(last) && this.points.size() < MAX_POINTS) {
			List<Pt> list = new ArrayList<>(this.points);
			list.add(preview);
			return new View(Collections.unmodifiableList(list), true, list.size() - 1);
		}
		return new View(List.copyOf(this.points), false, -1);
	}

	/**
	 * Moves one side of the shape out (positive amount) or in (negative amount), like WorldEdit's expand.
	 * Boxes can grow on any side; polygons and circles only up and down (their height).
	 *
	 * @return null when it worked, otherwise a short reason why not
	 */
	public String resize(Side side, int amount) {
		switch (this.mode) {
			case BOX -> {
				if (this.first == null || this.second == null) {
					return "Pick both corners first";
				}
				Pt[] pair = resizePair(this.first, this.second, side, amount);
				this.first = pair[0];
				this.second = pair[1];
			}
			case CIRCLE -> {
				if (!side.vertical()) {
					return "Circles only grow up or down (change the radius by picking a new edge)";
				}
				if (this.first == null || this.second == null) {
					return "Pick the center and an edge first";
				}
				Pt[] pair = resizePair(this.first, this.second, side, amount);
				this.first = pair[0];
				this.second = pair[1];
			}
			case POLYGON -> {
				if (!side.vertical()) {
					return "Polygons only grow up or down (move a corner to change the outline)";
				}
				if (this.points.isEmpty()) {
					return "Pick some corners first";
				}
				int extreme = 0;
				int min = Integer.MAX_VALUE;
				int max = Integer.MIN_VALUE;
				for (int i = 0; i < this.points.size(); i++) {
					int y = this.points.get(i).y();
					if (side.dy() > 0 ? y > this.points.get(extreme).y() : y < this.points.get(extreme).y()) {
						extreme = i;
					}
					min = Math.min(min, y);
					max = Math.max(max, y);
				}
				Pt p = this.points.get(extreme);
				int y = side.dy() > 0 ? Math.max(min, p.y() + amount) : Math.min(max, p.y() - amount);
				this.points.set(extreme, p.withY(y));
			}
			default -> {
				return "Expand works in Box, Polygon and Circle modes";
			}
		}
		this.revision++;
		return null;
	}

	/**
	 * Moves the whole shape {@code amount} blocks toward a side.
	 *
	 * @return null when it worked, otherwise a short reason why not
	 */
	public String shift(Side side, int amount) {
		if (isEmpty()) {
			return "Nothing to move yet";
		}
		if (this.mode.twoPoint()) {
			if (this.first != null) {
				this.first = side.move(this.first, amount);
			}
			if (this.second != null) {
				this.second = side.move(this.second, amount);
			}
		} else {
			this.points.replaceAll(p -> side.move(p, amount));
		}
		this.revision++;
		return null;
	}

	/**
	 * Moves the face of a two point shape that lies on {@code side}. Positive amounts push it outward,
	 * negative amounts pull it in, but never past the opposite face.
	 */
	private static Pt[] resizePair(Pt a, Pt b, Side side, int amount) {
		int ca = coord(a, side);
		int cb = coord(b, side);
		boolean positive = side.dx() + side.dy() + side.dz() > 0;
		// The point on that side moves; on a tie the second point grows up/east/south, the first down/west/north.
		boolean moveSecond = positive ? cb >= ca : cb < ca;
		Pt mover = moveSecond ? b : a;
		int other = moveSecond ? ca : cb;
		int next = positive ? Math.max(other, coord(mover, side) + amount) : Math.min(other, coord(mover, side) - amount);
		Pt moved = withCoord(mover, side, next);
		return moveSecond ? new Pt[] {a, moved} : new Pt[] {moved, b};
	}

	private static int coord(Pt p, Side side) {
		if (side.dx() != 0) {
			return p.x();
		}
		return side.dy() != 0 ? p.y() : p.z();
	}

	private static Pt withCoord(Pt p, Side side, int value) {
		if (side.dx() != 0) {
			return new Pt(value, p.y(), p.z());
		}
		return side.dy() != 0 ? new Pt(p.x(), value, p.z()) : new Pt(p.x(), p.y(), value);
	}

	private void enterDimension(String dim) {
		if (this.dimension != null && !this.dimension.equals(dim)) {
			this.first = null;
			this.second = null;
			this.points.clear();
		}
		this.dimension = dim;
	}
}
