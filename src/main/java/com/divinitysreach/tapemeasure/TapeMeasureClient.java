package com.divinitysreach.tapemeasure;

import com.divinitysreach.tapemeasure.command.MeasureCommands;
import com.divinitysreach.tapemeasure.config.TapeConfig;
import com.divinitysreach.tapemeasure.count.BlockCounter;
import com.divinitysreach.tapemeasure.hud.HudOverlay;
import com.divinitysreach.tapemeasure.input.Keybinds;
import com.divinitysreach.tapemeasure.input.Targeting;
import com.divinitysreach.tapemeasure.input.ToolInput;
import com.divinitysreach.tapemeasure.math.Pt;
import com.divinitysreach.tapemeasure.math.Readout;
import com.divinitysreach.tapemeasure.render.MeasureRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

import java.util.Objects;

/**
 * Tape Measure: a client side measuring tool drawn the way WorldEditCUI draws selections.
 *
 * <p>Nothing here talks to the server. Clicks made with the tape item are cancelled on the client before any
 * packet is sent, so the mod works on any server, plugin or not.</p>
 */
public final class TapeMeasureClient implements ClientModInitializer {
	public static final String MOD_ID = "tapemeasure";

	private static TapeMeasureClient instance;

	private TapeConfig config;
	private final Selection selection = new Selection();
	private final BlockCounter counter = new BlockCounter();
	private boolean visible = true;
	private Pt preview;

	private String readoutKey;
	private Readout readout;
	/** Screen to open on the next tick (commands run while chat is still open, and chat closes right after). */
	private Screen pendingScreen;

	public static TapeMeasureClient get() {
		return instance;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		instance = this;
		this.config = TapeConfig.load();

		Keybinds.register();
		ToolInput.register();

		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
			this.selection.clear();
			this.counter.cancel();
			this.preview = null;
		});
		ClientCommandRegistrationCallback.EVENT.register(MeasureCommands::register);
		LevelRenderEvents.COLLECT_SUBMITS.register(MeasureRenderer::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("readout"), HudOverlay::extract);
	}

	private void onTick(Minecraft mc) {
		if (this.pendingScreen != null) {
			Screen screen = this.pendingScreen;
			this.pendingScreen = null;
			mc.gui.setScreen(screen);
		}
		if (mc.player == null || mc.level == null) {
			this.preview = null;
			return;
		}
		Keybinds.tick(mc);
		ToolInput.tick(mc);

		if (this.config.livePreview && this.visible && ToolInput.holdingTool(mc) && mc.gui.screen() == null) {
			this.preview = Targeting.target(mc);
		} else {
			this.preview = null;
		}

		this.counter.tick(mc);
	}

	public TapeConfig config() {
		return this.config;
	}

	public void reloadConfig() {
		this.config = TapeConfig.load();
		this.readout = null;
	}

	/** Replaces the active settings (from the settings screen) and saves them. */
	public void applyConfig(TapeConfig newConfig) {
		newConfig.sanitize();
		this.config = newConfig;
		this.config.save();
		this.readout = null;
	}

	/** Opens a screen on the next client tick. */
	public void openScreenNextTick(Screen screen) {
		this.pendingScreen = screen;
	}

	public Selection selection() {
		return this.selection;
	}

	public BlockCounter counter() {
		return this.counter;
	}

	public boolean visible() {
		return this.visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public Pt preview() {
		return this.preview;
	}

	/** Current dimension id, or null outside a world. */
	public static String dimensionOf(Minecraft mc) {
		return mc.level == null ? null : mc.level.dimension().identifier().toString();
	}

	public static Readout.Dim dimKind(Minecraft mc) {
		if (mc.level == null) {
			return Readout.Dim.OTHER;
		}
		if (Level.OVERWORLD.equals(mc.level.dimension())) {
			return Readout.Dim.OVERWORLD;
		}
		if (Level.NETHER.equals(mc.level.dimension())) {
			return Readout.Dim.NETHER;
		}
		return Readout.Dim.OTHER;
	}

	/** The view of the selection as it should be drawn and measured right now. */
	public Selection.View currentView(Minecraft mc) {
		return this.selection.view(dimensionOf(mc), this.preview);
	}

	/** Readout for the current view, cached until the points change. */
	public Readout currentReadout(Minecraft mc) {
		Selection.View view = currentView(mc);
		Readout.Dim dim = dimKind(mc);
		String key = this.selection.mode() + "|" + view.points() + "|" + view.preview() + "|" + dim;
		if (this.readout == null || !Objects.equals(key, this.readoutKey)) {
			this.readout = Readout.build(this.selection.mode(), view.points(), view.preview(), dim);
			this.readoutKey = key;
		}
		return this.readout;
	}

	/** Readout of only the points actually picked (no preview), used for chat output. */
	public Readout pickedReadout(Minecraft mc) {
		Selection.View view = this.selection.view(dimensionOf(mc), null);
		return Readout.build(this.selection.mode(), view.points(), false, dimKind(mc));
	}

	/** Called after any change to the selection; a running block count of the old shape is stopped. */
	public void onSelectionChanged() {
		this.counter.cancelIfStale(this.selection.revision());
	}
}
