package org.academy.api.client.render.shader.pipeline;

/**
 * 多样本纹理绑定（A1，ADR-021）：一个采样槽位的 uniform 名与纹理标识。
 *
 * <p>{@code uniformName} 形如 {@code Sampler0}..{@code SamplerN-1}（与生成 GLSL 的
 * sampler 声明及管线 bind group 槽位一一对应）；{@code identifier} 为纹理资产路径
 * （如 {@code minecraft:textures/block/stone.png}），空串表示未指定（绑定兜底纹理）。</p>
 */
public record SamplerBinding(String uniformName, String identifier) {
    /**
     * 槽位索引 → uniform 名（{@code Sampler0} 起）。
     */
    public static String uniformName(int index) {
        return "Sampler" + index;
    }
}
