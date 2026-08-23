package org.academy.api.client.render.vfxgraph.nodes;

import org.academy.api.client.render.vfxgraph.model.VfxBlock;
import org.academy.api.client.render.vfxgraph.sim.SimNode;

/**
 * VFX 容器块工厂（M24）：由块实例（读取其属性/端口）构建 [SimNode]。
 * 与粒子 {@code VfxNodeFactory} 平行，但输入是容器模型的 {@link VfxBlock}。
 *
 * <p>M25 数据流：块输入端口可被算子经 {@code VfxDataEdge} 驱动，工厂经
 * {@link PortValueSource} 读取端口值（有则优先于属性，无则用属性默认）。</p>
 */
public interface VfxBlockFactory {
    /** 无数据流绑定的缺省创建（端口读取恒返回属性默认值）。 */
    default SimNode create(VfxBlock block) {
        return create(block, PortValueSource.none());
    }

    /** 带端口值源创建：块内读取端口时先查数据流绑定，无绑定则用属性默认。 */
    SimNode create(VfxBlock block, PortValueSource ports);
}
