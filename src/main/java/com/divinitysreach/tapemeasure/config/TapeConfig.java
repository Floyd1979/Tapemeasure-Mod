package com.divinitysreach.tapemeasure.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Settings stored in {@code config/tapemeasure.json}. Most of them can also be changed in game with
 * {@code /measure}.
 */
public final class TapeConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("tapemeasure");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "tapemeasure.json";

	public enum HudCorner {
		TOP_LEFT,
		TOP_RIGHT,
		BOTTOM_LEFT,
		BOTTOM_RIGHT;

		public static HudCorner parse(String value) {
			try {
				return valueOf(value.trim().toUpperCase(Locale.ROOT));
			} catch (RuntimeException ex) {
				return TOP_LEFT;
			}
		}
	}

	/**
	 * Item that works as the tape. Left click picks the first point, right click the next one. String by default
	 * because Litematica already uses the stick for its own area selections.
	 */
	public String toolItem = "minecraft:string";
	public boolean toolEnabled = true;
	/** How far away (in blocks) a point can be picked. */
	public int reach = 128;
	/** Pick the air block in front of the face you look at instead of the block itself. */
	public boolean targetFace = false;
	/** While holding the tape, the next point follows your crosshair. */
	public boolean livePreview = true;
	/** Sneak and scroll while holding the tape to change mode instead of hotbar slot. */
	public boolean sneakScrollChangesMode = true;
	/** Hold Ctrl and scroll while holding the tape to grow or shrink the side you look at. */
	public boolean ctrlScrollResizes = true;

	public boolean showGrid = true;
	public boolean showLabels = true;
	public boolean showHud = true;
	public String hudCorner = "TOP_LEFT";
	public int hudOffsetX = 4;
	public int hudOffsetY = 4;
	/** Hide the faint lines that show through blocks. */
	public boolean hideObstructedLines = false;
	public float lineWidth = 3.0f;
	/** Grid lines further than this from you (along each axis) are skipped to keep huge boxes cheap. */
	public int gridRange = 96;

	public int countBlocksPerTick = 200_000;
	public int countTopEntries = 15;
	public long countMaxVolume = 64_000_000L;

	public Colors colors = new Colors();

	public static final class Colors {
		public String box = "#FFB000E6";
		public String grid = "#FFB00050";
		public String corner1 = "#33DD33E6";
		public String corner2 = "#3388FFE6";
		public String line = "#33DDFFE6";
		public String path = "#33DDFFE6";
		public String polygon = "#C070FFE6";
		public String circle = "#FF55CCE6";
		public String preview = "#FFFFFFA0";
	}

	/** Colors converted to ARGB once after loading. */
	public transient int boxArgb;
	public transient int gridArgb;
	public transient int corner1Argb;
	public transient int corner2Argb;
	public transient int lineArgb;
	public transient int pathArgb;
	public transient int polygonArgb;
	public transient int circleArgb;
	public transient int previewArgb;

	public HudCorner corner() {
		return HudCorner.parse(this.hudCorner);
	}

	public static TapeConfig load() {
		Path file = path();
		TapeConfig config = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				config = GSON.fromJson(reader, TapeConfig.class);
			} catch (Exception ex) {
				LOGGER.warn("Could not read {}, using defaults", file, ex);
			}
		}
		if (config == null) {
			config = new TapeConfig();
		}
		config.sanitize();
		config.save();
		return config;
	}

	/** A fresh config with every option at its default. */
	public static TapeConfig defaults() {
		TapeConfig config = new TapeConfig();
		config.sanitize();
		return config;
	}

	/** An independent copy, used by the settings screen so Cancel can throw changes away. */
	public TapeConfig copy() {
		TapeConfig config = GSON.fromJson(GSON.toJson(this), TapeConfig.class);
		config.sanitize();
		return config;
	}

	public void save() {
		Path file = path();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (Exception ex) {
			LOGGER.warn("Could not save {}", file, ex);
		}
	}

	/** Clamp values and rebuild the parsed colors. Call after any change. */
	public void sanitize() {
		if (this.toolItem == null || this.toolItem.isBlank()) {
			this.toolItem = "minecraft:string";
		}
		this.toolItem = normalizeId(this.toolItem);
		this.reach = clamp(this.reach, 4, 512);
		this.lineWidth = Math.max(0.5f, Math.min(10f, this.lineWidth));
		this.gridRange = clamp(this.gridRange, 8, 512);
		this.countBlocksPerTick = clamp(this.countBlocksPerTick, 1_000, 5_000_000);
		this.countTopEntries = clamp(this.countTopEntries, 1, 100);
		this.countMaxVolume = Math.max(1_000L, Math.min(1_000_000_000L, this.countMaxVolume));
		this.hudCorner = corner().name();
		if (this.colors == null) {
			this.colors = new Colors();
		}
		Colors d = new Colors();
		this.boxArgb = parseColor(this.colors.box, d.box);
		this.gridArgb = parseColor(this.colors.grid, d.grid);
		this.corner1Argb = parseColor(this.colors.corner1, d.corner1);
		this.corner2Argb = parseColor(this.colors.corner2, d.corner2);
		this.lineArgb = parseColor(this.colors.line, d.line);
		this.pathArgb = parseColor(this.colors.path, d.path);
		this.polygonArgb = parseColor(this.colors.polygon, d.polygon);
		this.circleArgb = parseColor(this.colors.circle, d.circle);
		this.previewArgb = parseColor(this.colors.preview, d.preview);
	}

	public static String normalizeId(String id) {
		String trimmed = id.trim().toLowerCase(Locale.ROOT);
		return trimmed.contains(":") ? trimmed : "minecraft:" + trimmed;
	}

	/** Formats ARGB as {@code #RRGGBBAA}, the form used in the config file. */
	public static String formatColor(int argb) {
		int rgba = (argb << 8) | (argb >>> 24);
		return String.format(Locale.ROOT, "#%08X", rgba);
	}

	/** ARGB for a color string, or null when it is not a valid {@code #RRGGBB} or {@code #RRGGBBAA}. */
	public static Integer colorOrNull(String value) {
		return tryParseColor(value);
	}

	/** Parses {@code #RRGGBB} or {@code #RRGGBBAA} into ARGB. */
	public static int parseColor(String value, String fallback) {
		Integer parsed = tryParseColor(value);
		if (parsed == null) {
			parsed = tryParseColor(fallback);
		}
		return parsed == null ? 0xFFFFFFFF : parsed;
	}

	private static Integer tryParseColor(String value) {
		if (value == null) {
			return null;
		}
		String hex = value.trim();
		if (hex.startsWith("#")) {
			hex = hex.substring(1);
		}
		try {
			if (hex.length() == 6) {
				return 0xFF000000 | Integer.parseUnsignedInt(hex, 16);
			}
			if (hex.length() == 8) {
				int rgba = Integer.parseUnsignedInt(hex, 16);
				return (rgba >>> 8) | (rgba << 24);
			}
		} catch (NumberFormatException ignored) {
			// fall through
		}
		return null;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}
}
