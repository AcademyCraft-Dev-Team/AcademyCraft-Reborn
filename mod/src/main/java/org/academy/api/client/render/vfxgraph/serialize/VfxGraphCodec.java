package org.academy.api.client.render.vfxgraph.serialize;

import com.google.gson.JsonObject;
import org.academy.api.client.render.vfxgraph.model.VfxSystem;

/**
 * VFX 容器图编解码器（契约，M23）。基于 Gson，JSON 顶层含 {@link VfxGraphSchemaVersion#VERSION_FIELD}
 * 与 {@code kind: "vfx"}。**无旧格式兼容**（旧扁平节点列表 schema 已废弃）。
 */
public interface VfxGraphCodec {
    JsonObject encode(VfxSystem system);

    VfxSystem decode(JsonObject json);
}
