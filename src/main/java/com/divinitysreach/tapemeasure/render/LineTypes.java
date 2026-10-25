/*
 * Adapted from WorldEditCUI's VanillaPipelineProvider.
 *
 * Copyright (c) 2011-2024 WorldEditCUI team and contributors
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package com.divinitysreach.tapemeasure.render;

import com.divinitysreach.tapemeasure.TapeMeasureClient;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.function.Consumer;

/**
 * Line render types with custom depth tests, the same trick WorldEditCUI uses: one pass draws the parts of a
 * line that are in front of terrain at full strength, a second pass draws the hidden parts faintly, so an
 * outline stays readable through walls.
 *
 * <p>Minecraft 26.x uses a reversed depth buffer (cleared to 0, nearer is larger), which is why "visible" is
 * GREATER_THAN_OR_EQUAL.</p>
 */
public final class LineTypes {
	private static final RenderPipeline.Snippet LINES_SNIPPET = createSnippet();

	/** Parts of lines that are not hidden by terrain. */
	public static final RenderType VISIBLE = create("visible", CompareOp.GREATER_THAN_OR_EQUAL);
	/** Parts of lines that are behind terrain. */
	public static final RenderType HIDDEN = create("hidden", CompareOp.LESS_THAN);

	private LineTypes() {
	}

	private static RenderPipeline.Snippet createSnippet() {
		RenderPipeline base = RenderPipelines.LINES;
		RenderPipeline.Builder builder = RenderPipeline.builder()
			.withVertexShader(base.getShaders().get(ShaderType.VERTEX))
			.withFragmentShader(base.getShaders().get(ShaderType.FRAGMENT))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.withCull(false)
			.withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
			.withPrimitiveTopology(PrimitiveTopology.LINES)
			.withDepthStencilState(DepthStencilState.DEFAULT);
		base.getBindGroupLayouts().forEach(builder::withBindGroupLayout);
		return builder.buildSnippet();
	}

	private static RenderType create(String suffix, CompareOp depthTest) {
		RenderPipeline pipeline = RenderPipeline.builder(LINES_SNIPPET)
			.withLocation(TapeMeasureClient.id("pipeline/lines_" + suffix))
			.withDepthStencilState(new DepthStencilState(depthTest, false))
			.build();
		Consumer<RenderPipeline.Builder> applyDepthTest =
			builder -> builder.withDepthStencilState(new DepthStencilState(depthTest, false));
		OitPipelineSet oitPipelines = OitPipelineSet.builder("tapemeasure_lines_" + suffix, RenderPipeline.builder(RenderPipelines.OIT_LINES_SNIPPET))
			.withDepthBoundsModifier(applyDepthTest)
			.withTransmittanceModifier(applyDepthTest)
			.withAccumulateModifier(applyDepthTest)
			.build();
		RenderSetup setup = RenderSetup.builder(pipeline)
			.setOitPipelines(oitPipelines)
			.createRenderSetup();
		return RenderType.create("tapemeasure_lines_" + suffix, setup);
	}
}
