package com.divinitysreach.tapemeasure.input;

import com.divinitysreach.tapemeasure.TapeMeasureClient;
import com.divinitysreach.tapemeasure.config.TapeConfig;
import net.fabricmc.fabric.api.event.client.player.ClientHotbarScrollEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Clicks made while holding the tape item.
 *
 * <ul>
 *     <li>Left click: first point (sneak: clear)</li>
 *     <li>Right click: next point (sneak: undo last point)</li>
 *     <li>Sneak and scroll: change mode</li>
 *     <li>Ctrl and scroll: grow or shrink the side you look at</li>
 * </ul>
 *
 * <p>Every click is cancelled on the client, so no attack, block break, use or place packet reaches the
 * server.</p>
 */
public final class ToolInput {
	/** Holding right click repeats the use action every few ticks; only the first one of a press counts. */
	private static boolean useArmed = true;

	private ToolInput() {
	}

	public static void register() {
		ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
			if (client.gui.screen() != null || !holdingTool(client)) {
				return false;
			}
			if (clickCount > 0) {
				if (player.isShiftKeyDown()) {
					Actions.clear(client);
				} else {
					Actions.primary(client);
				}
			}
			// Cancel the attack (and any block breaking) entirely, also while the button is held.
			return true;
		});

		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> onUse(player, level, hand));
		UseItemCallback.EVENT.register(ToolInput::onUse);

		ClientHotbarScrollEvents.ALLOW.register((inventory, currentSlot, newSlot, xOffset, yOffset) -> {
			Minecraft mc = Minecraft.getInstance();
			TapeConfig config = TapeMeasureClient.get().config();
			if (mc.player == null || !holdingTool(mc)) {
				return true;
			}
			double offset = yOffset != 0 ? yOffset : xOffset;
			if (config.ctrlScrollResizes && mc.hasControlDown()) {
				// Scroll up grows the side you look at, scroll down pulls it in.
				if (offset != 0) {
					Actions.resize(mc, offset > 0 ? 1 : -1);
				}
				return false;
			}
			if (config.sneakScrollChangesMode && mc.player.isShiftKeyDown()) {
				if (offset != 0) {
					Actions.cycleMode(mc, offset > 0 ? -1 : 1);
				}
				return false;
			}
			return true;
		});
	}

	private static InteractionResult onUse(Player player, Level level, InteractionHand hand) {
		if (!level.isClientSide()) {
			return InteractionResult.PASS;
		}
		Minecraft mc = Minecraft.getInstance();
		if (player != mc.player || !holdingTool(mc)) {
			return InteractionResult.PASS;
		}
		if (hand == InteractionHand.MAIN_HAND && useArmed) {
			useArmed = false;
			if (player.isShiftKeyDown()) {
				Actions.undo(mc);
			} else {
				Actions.secondary(mc);
			}
		}
		// FAIL stops vanilla here: nothing is used or placed and no packet is sent, for either hand.
		return InteractionResult.FAIL;
	}

	/** Called every client tick. */
	public static void tick(Minecraft mc) {
		if (!mc.options.keyUse.isDown()) {
			useArmed = true;
		}
	}

	public static boolean holdingTool(Minecraft mc) {
		TapeConfig config = TapeMeasureClient.get().config();
		if (!config.toolEnabled || mc.player == null) {
			return false;
		}
		ItemStack stack = mc.player.getMainHandItem();
		if (stack.isEmpty()) {
			return false;
		}
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(config.toolItem);
	}
}
