package com.divinitysreach.tapemeasure.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.Arrays;

/**
 * Collects line segments (already relative to the camera) and submits them in one go. Every segment is drawn
 * twice: bright where it is in front of terrain and faint where it is hidden, like WorldEditCUI.
 */
final class LineBatch {
	/** x1, y1, z1, x2, y2, z2, nx, ny, nz, r, g, b, a, width */
	private static final int STRIDE = 14;

	private float[] data = new float[STRIDE * 256];
	private int size;

	void clear() {
		this.size = 0;
	}

	boolean isEmpty() {
		return this.size == 0;
	}

	void line(double x1, double y1, double z1, double x2, double y2, double z2, int argb, float width) {
		double dx = x2 - x1;
		double dy = y2 - y1;
		double dz = z2 - z1;
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (len < 1.0E-6) {
			return;
		}
		if (this.size + STRIDE > this.data.length) {
			this.data = Arrays.copyOf(this.data, this.data.length * 2);
		}
		float[] d = this.data;
		int i = this.size;
		d[i] = (float) x1;
		d[i + 1] = (float) y1;
		d[i + 2] = (float) z1;
		d[i + 3] = (float) x2;
		d[i + 4] = (float) y2;
		d[i + 5] = (float) z2;
		d[i + 6] = (float) (dx / len);
		d[i + 7] = (float) (dy / len);
		d[i + 8] = (float) (dz / len);
		d[i + 9] = ((argb >>> 16) & 0xFF) / 255f;
		d[i + 10] = ((argb >>> 8) & 0xFF) / 255f;
		d[i + 11] = (argb & 0xFF) / 255f;
		d[i + 12] = ((argb >>> 24) & 0xFF) / 255f;
		d[i + 13] = width;
		this.size += STRIDE;
	}

	/**
	 * Queues the collected lines with the collector.
	 *
	 * @param hidden true for the faint pass behind terrain
	 */
	void submit(SubmitNodeCollector collector, PoseStack poseStack, boolean hidden) {
		if (this.size == 0) {
			return;
		}
		// The collector draws later, so hand it a snapshot.
		final float[] snapshot = Arrays.copyOf(this.data, this.size);
		final RenderType type = hidden ? LineTypes.HIDDEN : LineTypes.VISIBLE;
		final float rgbScale = hidden ? 0.75f : 1.0f;
		final float alphaScale = hidden ? 0.25f : 1.0f;
		collector.submitCustomGeometry(poseStack, type, (pose, consumer) -> {
			for (int i = 0; i < snapshot.length; i += STRIDE) {
				float r = snapshot[i + 9] * rgbScale;
				float g = snapshot[i + 10] * rgbScale;
				float b = snapshot[i + 11] * rgbScale;
				float a = snapshot[i + 12] * alphaScale;
				float w = snapshot[i + 13];
				float nx = snapshot[i + 6];
				float ny = snapshot[i + 7];
				float nz = snapshot[i + 8];
				consumer.addVertex(pose, snapshot[i], snapshot[i + 1], snapshot[i + 2])
					.setColor(r, g, b, a)
					.setLineWidth(w);
				consumer.setNormal(pose, nx, ny, nz);
				consumer.addVertex(pose, snapshot[i + 3], snapshot[i + 4], snapshot[i + 5])
					.setColor(r, g, b, a)
					.setLineWidth(w);
				consumer.setNormal(pose, nx, ny, nz);
			}
		});
	}
}
