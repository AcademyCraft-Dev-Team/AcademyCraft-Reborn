package org.academy.api.client.render.graph.serialize;

/**
 * 图资产 schema 版本（契约）。破坏性变更须递增并写迁移规则。
 */
public final class GraphSchemaVersion {
    /**
     * 当前 schema 版本。
     */
    public static final int CURRENT = 1;

    /**
     * JSON 顶层字段名。
     */
    public static final String VERSION_FIELD = "version";

    private GraphSchemaVersion() {
    }
}
