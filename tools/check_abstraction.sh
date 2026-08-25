#!/usr/bin/env bash
set -euo pipefail

# 检查图系统代码是否脱离图形 API 无关抽象层（PROGRAM.md R1）。
# 扫描 src/ 下 graph/shader/vfxgraph/grapheditor 相关源码，命中黑名单类/包即报错。
#
# 黑名单：
#   - 后端专有类: GlStateManager GlDevice VulkanDevice GpuDeviceBackend
#                 GlRenderPipeline VulkanRenderPipeline GlShaderModule
#                 IntermediaryShaderModule GlslCompiler
#   - 后端包: com.mojang.blaze3d.opengl.* / com.mojang.blaze3d.vulkan.*
#   - 原生调用: org.lwjgl.opengl.* / org.lwjgl.vulkan.*

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VIOLATIONS=0

declare -a PATTERNS=(
  'GlStateManager'
  'GlDevice\b'
  'VulkanDevice\b'
  'GpuDeviceBackend\b'
  'GlRenderPipeline\b'
  'VulkanRenderPipeline\b'
  'GlShaderModule\b'
  'IntermediaryShaderModule\b'
  'GlslCompiler\b'
  'com\.mojang\.blaze3d\.opengl'
  'com\.mojang\.blaze3d\.vulkan'
  'org\.lwjgl\.opengl'
  'org\.lwjgl\.vulkan'
)

# 仅扫描图系统相关目录（graph/shader/vfxgraph/grapheditor），避免误伤项目既有代码。
TARGET_DIRS=(
  "$ROOT/src/main/java/org/academy/api/client/render/graph"
  "$ROOT/src/main/java/org/academy/api/client/render/shader"
  "$ROOT/src/main/java/org/academy/api/client/render/vfxgraph"
  "$ROOT/src/editor/kotlin/org/academy/desktop/grapheditor"
)

EXISTING=0
for dir in "${TARGET_DIRS[@]}"; do
  [ -d "$dir" ] || continue
  EXISTING=1
  for pat in "${PATTERNS[@]}"; do
    while IFS= read -r line; do
      file="${line%%:*}"
      lineno="${line#*:}"; lineno="${lineno%%:*}"
      echo "VIOLATION: $file:$lineno -> $pat"
      VIOLATIONS=$((VIOLATIONS + 1))
    done < <(grep -rnE "$pat" "$dir" 2>/dev/null || true)
  done
done

echo "----------------------------------------"
if [ "$EXISTING" -eq 0 ]; then
  echo "no graph-source directories yet (nothing to scan)"
elif [ "$VIOLATIONS" -eq 0 ]; then
  echo "abstraction check: OK"
else
  echo "abstraction check: $VIOLATIONS violation(s)"
fi

[ "$VIOLATIONS" -eq 0 ]
