# Academy VFXGraph Editor MCP

这是 AcademyCraft Reborn 的项目内 VFXGraph MCP。它让 Codex 直接读取、修改、校验并热重载
`src/main/resources/assets/academy/vfxgraph` 与 `run/academy/graphs` 中的特效，不再依赖逐项点击编辑器。

## 结构

```text
Codex
  │ stdio / JSON-RPC
  ▼
Node MCP server ── 原子写入 + 校验 + SHA 乐观锁 + 备份
  │                                      │
  │ run/academy/vfxgraph-mcp/bridge      └─ VFXGraph JSON
  ▼
Kotlin GraphEditor bridge ── 打开 / 重载 / 播放 / 暂停 / 重置 / 单步
```

MCP 服务器不监听网络端口。编辑器连接使用 `run/academy/vfxgraph-mcp/bridge` 下的本地文件队列，
并由编辑器渲染线程消费命令，因此不会从后台线程修改 ImGui、文档模型或 GPU 预览状态。

## 能力

| 工具 | 用途 |
| --- | --- |
| `list_graphs` | 列出特效、路径、SHA-256 与结构计数 |
| `get_graph` | 按摘要、节点或完整 JSON 读取特效 |
| `list_node_catalog` | 从 Java 注册表与现有资产汇总节点类型、属性和示例值 |
| `validate_graph` | 校验 schema、ID、flow、block flow、data edge、output 与节点类型 |
| `update_node` | 按节点 ID 更新属性；传 `null` 删除属性 |
| `update_parameter` | 更新或创建参数，支持曲线和渐变默认值 |
| `apply_json_patch` | 用 JSON Pointer 执行 `test/add/replace/remove` 结构修改 |
| `create_variant` | 从现有效果创建新的 assets/runtime 变体 |
| `editor_command` | 查询状态，或控制打开、重载、保存和预览播放 |

每次写入会执行以下流程：

1. 可选检查调用方读取时得到的 `expected_sha256`，防止覆盖并发修改。
2. 在内存中修改并运行快速结构校验；默认拒绝无效图。
3. 把旧文件备份到 `run/academy/vfxgraph-mcp/backups/<timestamp>`。
4. 在目标目录内原子替换 JSON。
5. 编辑器在线时立即要求其重载；离线时写入仍完成，响应会标记 `connected: false`。

## 本机接入

插件清单位于 `.codex-plugin/plugin.json`，MCP 配置位于 `.mcp.json`。当前配置绑定本仓库绝对路径；
仓库移动后需要同步更新 `.mcp.json` 的 `--project-root` 参数。

也可以直接注册 stdio MCP：

```powershell
codex mcp add academy-vfxgraph-editor -- node D:\mcmodtest\AcademyCraft-Reborn-26.2\tools\vfxgraph-editor\scripts\server.mjs --project-root=D:\mcmodtest\AcademyCraft-Reborn-26.2
```

注册后新建一个 Codex 任务以加载工具。该命令会修改用户级 Codex MCP 配置，本仓库不会自动执行它。

启动可连接的编辑器：

```powershell
.\gradlew.bat runGraphEditor
```

编辑器未启动时，所有文件读取、更新、备份和校验工具仍可使用；只有 `editor_command` 和即时预览状态不可用。

## 推荐调用顺序

1. `get_graph(graph, view="nodes")`，保存返回的 SHA-256。
2. 小范围参数调整优先用 `update_node` 或 `update_parameter`，并传 `expected_sha256`。
3. 新增/删除节点或连线时使用带 `test` 前置操作的 `apply_json_patch`。
4. 查看写入响应中的 `validation` 和 `editor.previewError`；必要时用 `editor_command(action="status")` 观察粒子数。

例如可直接对 Codex 说：

> 读取 `plasma_cannon_charge`，把充能粒子的生成速率提高 20%，保持寿命不变，校验后在 VFXGraph 编辑器里重载。

## 测试

```powershell
cd tools\vfxgraph-editor
npm test
cd ..\..
.\gradlew.bat editorTest -DisDev=true
```

MCP 不依赖 npm 第三方包，要求 Node.js 20 或更新版本。
