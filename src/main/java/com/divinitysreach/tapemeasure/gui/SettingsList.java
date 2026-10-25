package com.divinitysreach.tapemeasure.gui;

import com.divinitysreach.tapemeasure.config.TapeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The scrolling list of options. Every row edits the screen's working copy of the config, so nothing takes
 * effect until the screen is closed with Done.
 */
final class SettingsList extends ContainerObjectSelectionList<SettingsList.Row> {
	private static final int ROW_WIDTH = 320;
	private static final int CONTROL_X = 150;
	private static final int CONTROL_WIDTH = 106;
	private static final int RESET_X = 262;
	private static final int RESET_WIDTH = 50;
	private static final int HEIGHT = 20;
	private static final int HEADER_COLOR = 0xFFFFC040;
	private static final int VALID_TEXT = 0xFFE0E0E0;
	private static final int INVALID_TEXT = 0xFFFF5555;

	private final Supplier<TapeConfig> working;
	private final TapeConfig defaults = TapeConfig.defaults();

	SettingsList(Minecraft minecraft, int width, int height, int y, Supplier<TapeConfig> working) {
		super(minecraft, width, height, y, 24);
		this.working = working;
	}

	@Override
	public int getRowWidth() {
		return ROW_WIDTH;
	}

	TapeConfig config() {
		return this.working.get();
	}

	TapeConfig defaults() {
		return this.defaults;
	}

	Font font() {
		return this.minecraft.font;
	}

	// ------------------------------------------------------------------ builders used by the screen

	void header(String title) {
		addEntry(new HeaderRow(title));
	}

	void toggle(String label, String tooltip, Function<TapeConfig, Boolean> getter, BiConsumer<TapeConfig, Boolean> setter) {
		addEntry(new ToggleRow(label, tooltip, getter, setter));
	}

	void slider(String label, String tooltip, double min, double max, double step,
			Function<TapeConfig, Double> getter, BiConsumer<TapeConfig, Double> setter, DoubleFunction<String> formatter) {
		addEntry(new SliderRow(label, tooltip, min, max, step, getter, setter, formatter));
	}

	void corner(String label, String tooltip) {
		addEntry(new CornerRow(label, tooltip));
	}

	void item(String label, String tooltip) {
		addEntry(new ItemRow(label, tooltip));
	}

	void color(String label, Function<TapeConfig.Colors, String> getter, BiConsumer<TapeConfig.Colors, String> setter) {
		addEntry(new ColorRow(label, getter, setter));
	}

	// ------------------------------------------------------------------ rows

	abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
	}

	/** A section title. */
	final class HeaderRow extends Row {
		private final Component title;

		HeaderRow(String title) {
			this.title = Component.literal(title);
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return List.of();
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return List.of();
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			graphics.text(font(), this.title, getRowLeft(), getY() + 8, HEADER_COLOR, true);
		}
	}

	/** Label on the left, one control in the middle, a reset button on the right. */
	abstract class OptionRow extends Row {
		protected final StringWidget label;
		protected final Button reset;
		/** Everything that gets drawn. */
		private final List<AbstractWidget> widgets = new ArrayList<>();
		/** Everything that takes clicks and keyboard focus (not the label). */
		private final List<AbstractWidget> interactive = new ArrayList<>();

		OptionRow(String labelText, String tooltip) {
			this.label = new StringWidget(Component.literal(labelText), font());
			if (tooltip != null) {
				this.label.setTooltip(Tooltip.create(Component.literal(tooltip)));
			}
			this.reset = Button.builder(Component.literal("Reset"), button -> resetToDefault())
				.bounds(0, 0, RESET_WIDTH, HEIGHT)
				.build();
		}

		/** Registers the controls of this row; call once from the subclass constructor. */
		protected void controls(AbstractWidget... controls) {
			this.interactive.clear();
			this.interactive.addAll(List.of(controls));
			this.interactive.add(this.reset);
			this.widgets.clear();
			this.widgets.add(this.label);
			this.widgets.addAll(this.interactive);
		}

		protected abstract void resetToDefault();

		protected abstract boolean isDefault();

		/** Places the row's controls; the label and reset button are placed by the row itself. */
		protected abstract void layout(int left, int top);

		@Override
		public List<? extends GuiEventListener> children() {
			return this.interactive;
		}

		@Override
		public List<? extends NarratableEntry> narratables() {
			return this.interactive;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			int left = getRowLeft();
			int top = getY();
			this.label.setX(left);
			this.label.setY(top + (HEIGHT - font().lineHeight) / 2);
			this.reset.setX(left + RESET_X);
			this.reset.setY(top);
			this.reset.active = !isDefault();
			layout(left, top);
			for (AbstractWidget widget : this.widgets) {
				widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
			}
		}
	}

	final class ToggleRow extends OptionRow {
		private final Function<TapeConfig, Boolean> getter;
		private final BiConsumer<TapeConfig, Boolean> setter;
		private final CycleButton<Boolean> button;

		ToggleRow(String label, String tooltip, Function<TapeConfig, Boolean> getter, BiConsumer<TapeConfig, Boolean> setter) {
			super(label, tooltip);
			this.getter = getter;
			this.setter = setter;
			this.button = CycleButton.onOffBuilder(getter.apply(config()))
				.displayOnlyValue()
				.create(0, 0, CONTROL_WIDTH, HEIGHT, Component.literal(label), (b, value) -> setter.accept(config(), value));
			controls(this.button);
		}

		@Override
		protected void resetToDefault() {
			boolean value = this.getter.apply(defaults());
			this.setter.accept(config(), value);
			this.button.setValue(value);
		}

		@Override
		protected boolean isDefault() {
			return Objects.equals(this.getter.apply(config()), this.getter.apply(defaults()));
		}

		@Override
		protected void layout(int left, int top) {
			this.button.setX(left + CONTROL_X);
			this.button.setY(top);
		}
	}

	final class SliderRow extends OptionRow {
		private final Function<TapeConfig, Double> getter;
		private final BiConsumer<TapeConfig, Double> setter;
		private final NumberSlider slider;

		SliderRow(String label, String tooltip, double min, double max, double step,
				Function<TapeConfig, Double> getter, BiConsumer<TapeConfig, Double> setter, DoubleFunction<String> formatter) {
			super(label, tooltip);
			this.getter = getter;
			this.setter = setter;
			this.slider = new NumberSlider(CONTROL_WIDTH, HEIGHT, min, max, step, getter.apply(config()),
				value -> setter.accept(config(), value), formatter);
			controls(this.slider);
		}

		@Override
		protected void resetToDefault() {
			double value = this.getter.apply(defaults());
			this.setter.accept(config(), value);
			this.slider.setActual(value);
		}

		@Override
		protected boolean isDefault() {
			return Math.abs(this.getter.apply(config()) - this.getter.apply(defaults())) < 1.0E-6;
		}

		@Override
		protected void layout(int left, int top) {
			this.slider.setX(left + CONTROL_X);
			this.slider.setY(top);
		}
	}

	final class CornerRow extends OptionRow {
		private final CycleButton<TapeConfig.HudCorner> button;

		CornerRow(String label, String tooltip) {
			super(label, tooltip);
			this.button = CycleButton.builder(SettingsList::cornerName, config().corner())
				.withValues(List.of(TapeConfig.HudCorner.values()))
				.displayOnlyValue()
				.create(0, 0, CONTROL_WIDTH, HEIGHT, Component.literal(label), (b, value) -> config().hudCorner = value.name());
			controls(this.button);
		}

		@Override
		protected void resetToDefault() {
			config().hudCorner = defaults().hudCorner;
			this.button.setValue(defaults().corner());
		}

		@Override
		protected boolean isDefault() {
			return config().corner() == defaults().corner();
		}

		@Override
		protected void layout(int left, int top) {
			this.button.setX(left + CONTROL_X);
			this.button.setY(top);
		}
	}

	/** The tape item: type an item id, or take the item in your hand. */
	final class ItemRow extends OptionRow {
		private static final int HELD_WIDTH = 34;
		private final EditBox box;
		private final Button held;

		ItemRow(String label, String tooltip) {
			super(label, tooltip);
			this.box = new EditBox(font(), 0, 0, CONTROL_WIDTH - HELD_WIDTH - 2, HEIGHT, Component.literal(label));
			this.box.setMaxLength(128);
			this.box.setValue(config().toolItem);
			this.box.setResponder(this::onEdit);
			this.held = Button.builder(Component.literal("Held"), b -> useHeldItem())
				.bounds(0, 0, HELD_WIDTH, HEIGHT)
				.tooltip(Tooltip.create(Component.literal("Use the item in your main hand")))
				.build();
			controls(this.box, this.held);
			onEdit(this.box.getValue());
		}

		private void onEdit(String text) {
			String id = TapeConfig.normalizeId(text);
			Identifier parsed = Identifier.tryParse(id);
			boolean valid = parsed != null && !id.equals("minecraft:air") && BuiltInRegistries.ITEM.containsKey(parsed);
			this.box.setTextColor(valid ? VALID_TEXT : INVALID_TEXT);
			if (valid) {
				config().toolItem = id;
			}
		}

		private void useHeldItem() {
			if (minecraft.player == null) {
				return;
			}
			ItemStack stack = minecraft.player.getMainHandItem();
			if (stack.isEmpty()) {
				return;
			}
			this.box.setValue(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		}

		@Override
		protected void resetToDefault() {
			this.box.setValue(defaults().toolItem);
		}

		@Override
		protected boolean isDefault() {
			return config().toolItem.equals(defaults().toolItem);
		}

		@Override
		protected void layout(int left, int top) {
			this.box.setX(left + CONTROL_X);
			this.box.setY(top);
			this.held.setX(left + CONTROL_X + CONTROL_WIDTH - HELD_WIDTH);
			this.held.setY(top);
		}
	}

	/** A color as {@code #RRGGBBAA} text with a live swatch. */
	final class ColorRow extends OptionRow {
		private static final int SWATCH = 20;
		private final Function<TapeConfig.Colors, String> getter;
		private final BiConsumer<TapeConfig.Colors, String> setter;
		private final EditBox box;
		private String accepted;
		private int swatchX;
		private int swatchY;

		ColorRow(String label, Function<TapeConfig.Colors, String> getter, BiConsumer<TapeConfig.Colors, String> setter) {
			super(label, "Hex color as #RRGGBB or #RRGGBBAA (the last pair is opacity)");
			this.getter = getter;
			this.setter = setter;
			this.box = new EditBox(font(), 0, 0, CONTROL_WIDTH - SWATCH - 4, HEIGHT, Component.literal(label));
			this.box.setMaxLength(9);
			this.accepted = display(getter.apply(config().colors));
			this.box.setValue(this.accepted);
			this.box.setResponder(this::onEdit);
			controls(this.box);
		}

		private static String display(String stored) {
			Integer argb = TapeConfig.colorOrNull(stored);
			return argb == null ? stored : TapeConfig.formatColor(argb);
		}

		private void onEdit(String text) {
			if (!isHexInput(text)) {
				this.box.setValue(this.accepted);
				return;
			}
			this.accepted = text;
			boolean valid = TapeConfig.colorOrNull(text) != null;
			this.box.setTextColor(valid ? VALID_TEXT : INVALID_TEXT);
			if (valid) {
				this.setter.accept(config().colors, text.toUpperCase(java.util.Locale.ROOT));
			}
		}

		private static boolean isHexInput(String text) {
			if (text.isEmpty()) {
				return true;
			}
			if (text.charAt(0) != '#') {
				return false;
			}
			for (int i = 1; i < text.length(); i++) {
				char c = text.charAt(i);
				boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
				if (!hex) {
					return false;
				}
			}
			return true;
		}

		private int currentArgb() {
			Integer argb = TapeConfig.colorOrNull(this.getter.apply(config().colors));
			return argb == null ? 0xFFFFFFFF : argb;
		}

		@Override
		protected void resetToDefault() {
			this.box.setValue(display(this.getter.apply(defaults().colors)));
		}

		@Override
		protected boolean isDefault() {
			Integer mine = TapeConfig.colorOrNull(this.getter.apply(config().colors));
			Integer original = TapeConfig.colorOrNull(this.getter.apply(defaults().colors));
			return Objects.equals(mine, original);
		}

		@Override
		protected void layout(int left, int top) {
			this.box.setX(left + CONTROL_X);
			this.box.setY(top);
			this.swatchX = left + CONTROL_X + CONTROL_WIDTH - SWATCH;
			this.swatchY = top;
		}

		@Override
		public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
			super.extractContent(graphics, mouseX, mouseY, hovered, partialTick);
			int x = this.swatchX;
			int y = this.swatchY;
			// Border, then a light and dark half so see-through colors read correctly, then the color itself.
			graphics.fill(x, y, x + SWATCH, y + SWATCH, 0xFF000000);
			graphics.fill(x + 1, y + 1, x + SWATCH / 2, y + SWATCH - 1, 0xFFFFFFFF);
			graphics.fill(x + SWATCH / 2, y + 1, x + SWATCH - 1, y + SWATCH - 1, 0xFF404040);
			graphics.fill(x + 1, y + 1, x + SWATCH - 1, y + SWATCH - 1, currentArgb());
		}
	}

	private static Component cornerName(TapeConfig.HudCorner corner) {
		return Component.literal(switch (corner) {
			case TOP_LEFT -> "Top left";
			case TOP_RIGHT -> "Top right";
			case BOTTOM_LEFT -> "Bottom left";
			case BOTTOM_RIGHT -> "Bottom right";
		});
	}
}
