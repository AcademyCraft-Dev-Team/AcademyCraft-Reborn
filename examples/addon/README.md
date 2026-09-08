# 公开 API 示例附属

此工程只导入 `org.academy.api`，无 Academy 内部导入或方法 Mixin。它包含一个新分类、两个使用同一 Java 类的技能、普通、直接和真实生命三种伤害 profile 与 JSON、标准程序入口和专注动作节点。第二个技能通过原生 DeferredRegister 注册，验证其与 AcademyRegistrations 门面经过同一关联流程。

示例分类未声明初始开发 P.R.O.P.S，因此不参与初始随机开发；可用管理员命令选择分类并学习。节点用于演示 20 tick 发光效果及 Codec 计数状态，不绑定客户端按键。完整契约见 [开发指南](../../docs/addon_api_guide.md)。

## 仓库内验证

在仓库根目录使用 JBR 25：

```powershell
.\gradlew.bat verifyApiExample apiExampleJar -DisDev=true
.\gradlew.bat runGameTestServer -DisDev=true -PacademyApiExample=true -PacademyGameTests=academy_api_example:registration_execution
.\gradlew.bat runClientDev -DisDev=true -PacademyApiExample=true
```

`verifyApiExample` 编译并检查示例没有内部导入和 Mixin 引用，随主项目 check 执行。示例仅在显式传入 `academyApiExample=true` 时加载，不会打入 Academy 主 JAR；`apiExampleJar` 输出独立附属 JAR。

GameTest 使用正式管理员命令准备模拟玩家，验证冻结、同类多技能、key 依赖、未学拒绝、状态隔离、程序保存/执行、CP 合计不足时零副作用、取消/到期清理和真实伤害路由。模拟连接用于服务端验收，不代表两台客户端联机测试。

游戏内准备示例：

```text
/academy set_category academy_api_example:cryokinesis
/academy level 5
/academy learn_all
```

之后通过已有精密操作入口选择示例分类的手动入口和专注节点。

## 独立构建

本目录也可作为独立 Gradle 项目，只编译依赖主 MOD 制品，不读取其源码。先在仓库根目录生成开发 JAR，再运行：

```powershell
.\gradlew.bat jar -DisDev=true
.\gradlew.bat -p examples/addon build
```

默认引用 `../../build/libs/academy-26.2.0-0.0.4-alpha-dev.jar`。复制到其他位置时，使用 Java 25 和 Gradle 9.7，传入 `-PacademyJar=绝对路径`。正式运行还需安装匹配版本的 Academy、NeoForge 及主 MOD 的运行依赖；独立构建不把主 MOD 或依赖嵌套进示例 JAR。

测试阶段仅尽可能保持文档化公开 API 兼容；禁止破坏性注入，非必要禁止对 Academy 方法进行 Mixin。
