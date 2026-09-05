# 空间收纳单元

物品 ID：`academy:spatial_storage_unit`。图标采用用户提供的 PNG。

## 使用

- 默认关闭，手持右键切换；开启时有附魔光效和状态提示。
- 开启后，主手、副手、快捷栏、背包和任意有效 Curios 饰品槽均可触发收纳。多个单元按主手、副手、背包、饰品顺序选择首个开启的单元。
- 能力与精密操作的方块掉落进入单元，包括箱桶内部物品及连带破坏的附属方块。采矿光束原有时运、精准采集、自动冶炼规则不变。
- 传送使的方块传送节点不参与收纳；普通采掘和已有地面掉落不受影响。
- 手持单元，Shift + 右键箱子、木桶或提供 NeoForge 物品存储能力的设备，按点击面接受能力插入。未被设备接受的物品继续保留，关闭状态也可以卸货。
- 同时加载超越维度时，每秒尝试将携带单元中的内容转入玩家当前主维度网络。没有网络、网络容量不足或拒收时保留剩余内容；每个单元每秒最多处理 128 种物品并轮换重试。

## 合成

```text
板 珠 板
路 壳 路
板 珠 板
```

板：虚相位板 ×4；珠：末影珍珠 ×2；路：虚相位电路 ×2；壳：空白液体单元 ×1。

配方及解锁进度通过 `runClientData` 生成。

## 存储与扩展

物品组件仅记录 UUID 索引和开关。内容使用主世界 `SavedDataStorage` 中的 `academy:spatial_storage` 保存，以物品及完整组件为键合并，数量为 long；不会写入玩家能力数据或物品的内容组件。

`AbilityBlockDrops` 提供显式的服务端破坏者归属作用域。作用域内的物品实体在加入世界前直接收纳，覆盖容器内部掉落与相邻方块连带掉落。作用域通过 try-with-resources 恢复，不会泄漏到普通采掘。自行把掉落送入背包或工作缓存的技能通过 `SpatialStorageService.collect` 接入。

Curios 通过可选类隔离和 `curios:curio` 通用标签支持所有槽型。超越维度使用其 26.2 公开 API 的 `DimensionsNet.getNetFromPlayer`、`getUnifiedStorage` 和 `UnifiedStorage.insert`，不强制安装联动模组。接口不兼容时记录错误并禁用该桥接，收纳内容继续保留。

上游接口：[BeyondDimensions 26.2](https://github.com/Frostbite-time/BeyondDimensions/tree/26.2/src/main/java/com/wintercogs/beyonddimensions/api)。

## 验证

- `./gradlew runClientData -DisDev=true`
- `./gradlew test -DisDev=true`
- `./gradlew build -DisDev=true`
- `./gradlew build -DisDev=false`
- `./gradlew runGameTestServer -DisDev=true -PacademyGameTests=academy:spatial_storage`
- `./gradlew runClientDev -DisDev=true`：客户端启动及模型、贴图图集加载检查。

游戏测试覆盖：背包收纳、关闭恢复掉落、普通破坏隔离、箱桶内容收纳、满箱与部分卸货、不同 ID 隔离、数量及自定义组件序列化、异常后的作用域清理。联动测试使用 Curios 16.0.0+26.2 和 BeyondDimensions 0.7.30，检查仅饰品装备时的收纳，以及无网络、正常插入、容量不足三种路径。联动测试包仅放在测试运行目录，不加入发布包。
