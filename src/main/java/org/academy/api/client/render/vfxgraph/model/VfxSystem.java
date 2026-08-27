package org.academy.api.client.render.vfxgraph.model;

import org.academy.api.client.render.graph.model.GraphParameter;

import java.util.ArrayList;
import java.util.List;

/**
 * VFX 容器图（M23）：顶层资产单元，对标 Unity VFX Graph 的 System 资产。
 *
 * <p>结构：{@code contexts}（SPAWN/INITIALIZE/UPDATE/OUTPUT 容器，内含 {@link VfxBlock}）+
 * {@code operators}（自由算子，经 {@link VfxDataEdge} 驱动块属性）+ {@code flowEdges}
 * （context 间批次 flow）+ {@code blockFlows}（块级批次 flow，spawn→init 精确配对，M28b）+
 * {@code dataEdges}（算子→块数据流）+ 黑板 {@code parameters} + {@code outputs}（输出块 id）。
 * 与核心 {@code Graph} 并行，不破坏其契约冻结。</p>
 *
 * @param id         资产 id
 * @param contexts   context 容器列表
 * @param operators  自由算子列表
 * @param flowEdges  context 间 flow 边
 * @param blockFlows 块级 flow 边（spawn→init 批次配对）
 * @param dataEdges  算子→块 数据边
 * @param parameters 黑板参数
 * @param outputs    输出块 id 列表
 */
public record VfxSystem(
        String id,
        List<VfxContext> contexts,
        List<VfxOperatorNode> operators,
        List<VfxFlowEdge> flowEdges,
        List<VfxBlockFlowEdge> blockFlows,
        List<VfxDataEdge> dataEdges,
        List<GraphParameter> parameters,
        List<String> outputs
) {
    public VfxSystem {
        contexts = List.copyOf(contexts);
        operators = List.copyOf(operators);
        flowEdges = List.copyOf(flowEdges);
        blockFlows = List.copyOf(blockFlows);
        dataEdges = List.copyOf(dataEdges);
        parameters = List.copyOf(parameters);
        outputs = List.copyOf(outputs);
    }

    /**
     * 兼容重载（M28b 前调用点）：无块级 flow。
     */
    public VfxSystem(
            String id,
            List<VfxContext> contexts,
            List<VfxOperatorNode> operators,
            List<VfxFlowEdge> flowEdges,
            List<VfxDataEdge> dataEdges,
            List<GraphParameter> parameters,
            List<String> outputs
    ) {
        this(id, contexts, operators, flowEdges, List.of(), dataEdges, parameters, outputs);
    }

    /**
     * 全部块与算子的扁平视图（数据边引用、校验、执行器构建用）。
     */
    public List<VfxNode> nodes() {
        var out = new ArrayList<VfxNode>();
        for (var context : contexts) {
            out.addAll(context.blocks());
        }
        out.addAll(operators);
        return List.copyOf(out);
    }

    /**
     * 按 id 查块或算子；无则返回 null。
     */
    public VfxNode findNode(String nodeId) {
        for (var node : nodes()) {
            if (node.id().equals(nodeId)) return node;
        }
        return null;
    }

    /**
     * 按 id 查 context；无则返回 null。
     */
    public VfxContext findContext(String contextId) {
        for (var context : contexts) {
            if (context.id().equals(contextId)) return context;
        }
        return null;
    }
}
