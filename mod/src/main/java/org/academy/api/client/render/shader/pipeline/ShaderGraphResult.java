package org.academy.api.client.render.shader.pipeline;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;

/**
 * 编译结果：构建好的管线 + 参数布局。
 */
public record ShaderGraphResult(RenderPipeline pipeline, UniformLayout layout) {
}
