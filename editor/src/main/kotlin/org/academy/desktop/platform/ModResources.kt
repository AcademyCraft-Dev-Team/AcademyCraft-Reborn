package org.academy.desktop.platform

import java.nio.file.Path

/**
 * 仓库根下的模组资源目录。拆分后模组代码位于 `mod/` 子项目，
 * 编辑器以仓库根作为 `--project-root`，因此资源统一从这里解析。
 */
fun modResources(repoRoot: Path): Path =
    repoRoot.resolve("mod").resolve("src").resolve("main").resolve("resources")
