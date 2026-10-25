package com.divinitysreach.tapemeasure.input;

import com.divinitysreach.tapemeasure.TapeMeasureClient;
import com.divinitysreach.tapemeasure.config.TapeConfig;
import com.divinitysreach.tapemeasure.math.Pt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Finds the block under the crosshair, much further out than normal reach. */
public final class Targeting {
	private Targeting() {
	}

	/** The targeted block, or null when nothing is in range. */
	public static Pt target(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) {
			return null;
		}
		TapeConfig config = TapeMeasureClient.get().config();
		float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		HitResult hit = player.pick(config.reach, partialTick, false);
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = blockHit.getBlockPos();
			if (config.targetFace) {
				pos = pos.relative(blockHit.getDirection());
			}
			return new Pt(pos.getX(), pos.getY(), pos.getZ());
		}
		return null;
	}

	/** The block the player is standing in (feet level). */
	public static Pt feet(Minecraft mc) {
		if (mc.player == null) {
			return null;
		}
		BlockPos pos = mc.player.blockPosition();
		return new Pt(pos.getX(), pos.getY(), pos.getZ());
	}
}
