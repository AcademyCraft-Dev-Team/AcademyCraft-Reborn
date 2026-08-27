package org.academy.api.client.render.vfxgraph.model;

/**
 * VFX 容器类型（M23，对标 Unity VFX Graph 的四个 context）。
 *
 * <p>流水线阶段顺序：{@link #SPAWN} → {@link #INITIALIZE} → {@link #UPDATE} → {@link #OUTPUT}。
 * 同一阶段可存在多个 context 实例（如多个独立发射器），各自含若干 {@link VfxBlock}。</p>
 */
public enum VfxContextType {
    /**
     * 生成粒子批次（spawn 块输出「本帧新粒子索引批次」）。
     */
    SPAWN,
    /**
     * 初始化新粒子（只处理经 flow 边传入的批次）。
     */
    INITIALIZE,
    /**
     * 逐帧更新全部存活粒子。
     */
    UPDATE,
    /**
     * 输出/渲染（决定 RenderSpec）。
     */
    OUTPUT
}
