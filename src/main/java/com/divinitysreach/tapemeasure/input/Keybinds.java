package com.divinitysreach.tapemeasure.input;

import com.divinitysreach.tapemeasure.TapeMeasureClient;
import com.divinitysreach.tapemeasure.gui.TapeSettingsScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Optional keybinds, all unbound by default so they never clash with Litematica, Xaero or vanilla keys.
 * Set them under Options, Controls, Key Binds, "Tape Measure".
 */
public final class Keybinds {
	private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(TapeMeasureClient.id("general"));

	private static KeyMapping point1;
	private static KeyMapping point2;
	private static KeyMapping undo;
	private static KeyMapping clear;
	private static KeyMapping nextMode;
	private static KeyMapping toggleHud;
	private static KeyMapping toggleVisible;
	private static KeyMapping count;
	private static KeyMapping settings;
	private static KeyMapping grow;
	private static KeyMapping shrink;

	private Keybinds() {
	}

	public static void register() {
		point1 = key("point1");
		point2 = key("point2");
		undo = key("undo");
		clear = key("clear");
		nextMode = key("mode");
		toggleHud = key("hud");
		toggleVisible = key("visible");
		count = key("count");
		settings = key("settings");
		grow = key("grow");
		shrink = key("shrink");
	}

	private static KeyMapping key(String name) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key." + TapeMeasureClient.MOD_ID + "." + name,
			InputConstants.UNKNOWN.getValue(),
			CATEGORY));
	}

	public static void tick(Minecraft mc) {
		boolean inGame = mc.gui.screen() == null;
		while (point1.consumeClick()) {
			if (inGame) {
				Actions.primary(mc);
			}
		}
		while (point2.consumeClick()) {
			if (inGame) {
				Actions.secondary(mc);
			}
		}
		while (undo.consumeClick()) {
			if (inGame) {
				Actions.undo(mc);
			}
		}
		while (clear.consumeClick()) {
			if (inGame) {
				Actions.clear(mc);
			}
		}
		while (nextMode.consumeClick()) {
			if (inGame) {
				Actions.cycleMode(mc, 1);
			}
		}
		while (toggleHud.consumeClick()) {
			if (inGame) {
				Actions.toggleHud(mc);
			}
		}
		while (toggleVisible.consumeClick()) {
			if (inGame) {
				Actions.toggleVisible(mc);
			}
		}
		while (count.consumeClick()) {
			if (inGame) {
				Actions.count(mc);
			}
		}
		while (grow.consumeClick()) {
			if (inGame) {
				Actions.resize(mc, 1);
			}
		}
		while (shrink.consumeClick()) {
			if (inGame) {
				Actions.resize(mc, -1);
			}
		}
		while (settings.consumeClick()) {
			if (inGame) {
				mc.gui.setScreen(new TapeSettingsScreen(null));
			}
		}
	}
}
