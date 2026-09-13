# 外部时间控制免疫

## 行为约定

没有 GUI/HUD 上下文的外部输入取消、技能绑定禁用和强制运动/朝向写入，默认按
`TemporalPauseSource.EXTERNAL_COMPATIBILITY` 处理。保护由服务端授予的免疫贡献决定；
单独拥有 `VANILLA_FREEZE` 免疫不会获得这一保护。

这项策略不再以 `tickCount` 停滞为前提。玩家可以正常 tick，同时遭受外部操作封锁；
这种情况下也必须维护操作能力。矢量反射继续使用既有
`TemporalService.acquireTimeStopImmunity(Entity)`，其他实体也可以通过相同 API 获得保护。

## 实现边界

- 在本体/原版入口处理外部 cancellable Mixin 回调：服务端实体与乘客 tick、
  客户端实体 tick、键盘、鼠标按钮/滚轮/转向，以及本体技能输入分发。
  根据合并后的 Mixin 来源元数据识别外部回调，不匹配附属类名、方法名或技能名称。
  外部回调本身仍会执行；免疫时交给它独立的取消令牌，避免污染随后本体回调的取消状态。
- 本体输入事件在取消发生时检查来源，GUI/HUD 消费仍可取消事件。
- 服务端位置、速度、视角/头部/身体朝向写入检查外部控制来源。
  连接层的强制传送在创建传送确认状态和发送位置包之前拦截，避免仅拦住位置写入却留下网络回拉。
  原版玩家自身行为、本体控制、显式自身运动来源和内部位置校正保留。
  来源判定会读取 `MixinMerged` 元数据，避免把合并进原版类的外部处理器误判为原版行为。
- 旧式 `setKeyBindingEnabled` 外部禁用请求与绑定的有效状态分开保存。
  免疫中恢复原先启用的绑定，免疫关闭后重新应用仍未解除的请求；
  后续解锁恢复原状态，断线清理请求。原先禁用的绑定不会被强制启用，
  本体/GUI/HUD 后续显式写入优先于旧请求。

标准入口保护覆盖当前调用链中的 `CallbackInfo` 取消和自有事件、运动 API。
它不是对任意字节码替换、任意字段写入或其他模组内部状态的全局恢复器。
不清空外部模组的时停状态，也不修改其 JAR。

## GUI/HUD 接入

原版 Screen/AbstractWidget 调用栈和本体控制保持有效。`addExclusiveKeyBinding`
注册的 HUD 操作自动进入 UI 上下文。其他自定义 HUD 可以显式声明同步控制范围：

```java
UiInputContext.run(() -> {
    event.setCanceled(true);
    InputSystem.setKeyBindingEnabled(bindingName, false);
});
```

作用域可嵌套，并在异常退出时恢复。异步工作必须在实际进行 UI 操作的线程上重新进入作用域。
不要用 UI 作用域声明技能控制、时停或非交互式的外部束缚。

## 回归

普通构建不编译 `validation/temporal/java`。仅下面的独立客户端回归启用该源码目录；
不要给发布构建传入验证 init 脚本。

```powershell
.\gradlew.bat test -DisDev=true
.\gradlew.bat build -DisDev=true
.\gradlew.bat build -DisDev=false
.\gradlew.bat runGameTestServer -DisDev=true '-PacademyGameTests=academy:time*'
.\gradlew.bat runClientDev -DisDev=true -I validation/temporal/run.init.gradle
```

客户端测试目录为 `run/temporal-client-regression`，需要一份名为
`verification` 的专用测试存档及放在该目录 `mods` 子目录的测试附属。
运行会自动启用矢量反射、构造 BOSS 施法实例、调用 BOSS 使用的时停技能、
测试后清理并退出。只使用复制的测试世界，不使用正式存档。

样本：用户提供的 `shadow-26.2.0-1.1.4.jar`；
SHA-256：`2d5e1522795fb4678f44d9d5226f5dca829b673eac9e21e008fd78da7da356a5`。
样本类名只出现在可选验证驱动中。

断言覆盖：

1. 实际矢量反射产生时间免疫贡献并同步到客户端。
2. 免疫实体继续 tick，普通实体仍被冻结。
3. 免疫实体/玩家不被回写位置、视角和头部朝向；普通实体仍被锁位。
4. 技能键盘/鼠标输入、原版移动键与鼠标转向在免疫时生效，释放键正常。
5. 免疫失效、时停期间重新获得免疫、外部时停结束后的状态切换。
6. 独立生成且无本体 CodeSource 的未知事件取消者受到相同保护；
   GUI/HUD 作用域仍允许取消。
7. 原本禁用的绑定、重复禁用、原有 UI 写入、嵌套作用域异常退出、
   本体 Mixin 取消以及与目标入口无关的取消不受破坏。

成功日志包含 `TEMPORAL_ADDON_REGRESSION_PASSED`。
