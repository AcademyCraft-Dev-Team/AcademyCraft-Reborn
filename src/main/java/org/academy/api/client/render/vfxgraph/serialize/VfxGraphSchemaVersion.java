package org.academy.api.client.render.vfxgraph.serialize;

/**
 * VFX 容器图 schema 版本（M23）。破坏性变更须递增并写迁移规则。
 */
public final class VfxGraphSchemaVersion {
    /** 当前 schema 版本。 */
    public static final int CURRENT = 1;

    /** JSON 顶层字段名。 */
    public static final String VERSION_FIELD = "version";

    /** 顶层 kind 标识：区分 VFX 容器图与核心 Graph（{@code "kind": "vfx"}）。 */
    public static final String KIND_FIELD = "kind";

    private VfxGraphSchemaVersion() {
    }
}
