package org.academy.api.client.render.graph.model;

/**
 * 边（契约）。从某节点的输出端口到另一节点的输入端口。
 */
public record Edge(PortRef from, PortRef to) {
    /** 端口引用：节点 id + 端口 id。 */
    public record PortRef(String nodeId, String portId) {
    }
}
