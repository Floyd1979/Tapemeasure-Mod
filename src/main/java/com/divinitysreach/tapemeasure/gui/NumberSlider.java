package com.divinitysreach.tapemeasure.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** A slider over a number range that snaps to a step and shows only its value. */
final class NumberSlider extends AbstractSliderButton {
	private final double min;
	private final double max;
	private final double step;
	private final DoubleConsumer setter;
	private final DoubleFunction<String> formatter;
	private double actual;

	NumberSlider(int width, int height, double min, double max, double step, double initial,
			DoubleConsumer setter, DoubleFunction<String> formatter) {
		super(0, 0, width, height, Component.empty(), toSlider(initial, min, max));
		this.min = min;
		this.max = max;
		this.step = step;
		this.setter = setter;
		this.formatter = formatter;
		// Show the stored value exactly until the slider is moved, even if it is not on a step.
		this.actual = initial;
		updateMessage();
	}

	/** Moves the slider to a value without reporting it back (used by the reset buttons). */
	void setActual(double newValue) {
		this.actual = newValue;
		this.value = toSlider(newValue, this.min, this.max);
		updateMessage();
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.literal(this.formatter.apply(this.actual)));
	}

	@Override
	protected void applyValue() {
		double raw = this.min + this.value * (this.max - this.min);
		double snapped = this.min + Math.round((raw - this.min) / this.step) * this.step;
		this.actual = Math.max(this.min, Math.min(this.max, snapped));
		this.setter.accept(this.actual);
	}

	private static double toSlider(double v, double min, double max) {
		if (max <= min) {
			return 0;
		}
		return Math.max(0, Math.min(1, (v - min) / (max - min)));
	}
}
