package org.academy.internal.common.structure.upgrade;

import org.academy.api.common.structure.upgrade.StructureDamage;
import org.academy.api.common.structure.upgrade.StructureFracture;
import org.academy.api.common.structure.upgrade.StructureImpactFeed;
import org.academy.api.common.structure.upgrade.StructureOperation;

import java.util.Objects;
import java.util.UUID;

/** Discoverable, replaceable ports; intentionally never entered by current gameplay. */
public final class ScaffoldStructureCombat implements StructureDamage, StructureImpactFeed, StructureFracture {
    @Override
    public StructureOperation.Result<StructureDamage.Batch> query(StructureDamage.Authority authority, StructureDamage.Request request) {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(request, "request");
        // [TODO: Agent B][UPG-DAMAGE-01] 合并宿主格与结构内容选择体查询，按真实形状和预算生成服务端计划。
        return StructureOperation.unsupported("damage_query_scaffold");
    }

    @Override
    public StructureOperation.Result<StructureDamage.Receipt> apply(StructureDamage.Authority authority, StructureDamage.Batch batch) {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(batch, "batch");
        // [TODO: Agent B][UPG-DAMAGE-02] 复核授权/版本，以稳定格键去重，复用全局掉落作用域并合并昂贵重建。
        return StructureOperation.unsupported("damage_apply_scaffold");
    }

    @Override
    public StructureOperation.Result<StructureDamage.Progress> status(StructureDamage.Authority authority, UUID operationId) {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(operationId, "operationId");
        // [TODO: Agent B][UPG-DAMAGE-03] 验证宿主与任务拥有者后查询分批状态；只返回本任务已准备的计划和真实进度。
        return StructureOperation.unsupported("damage_status_scaffold");
    }

    @Override
    public StructureOperation.Result<StructureDamage.Progress> cancel(StructureDamage.Authority authority, UUID operationId) {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(operationId, "operationId");
        // [TODO: Agent B][UPG-DAMAGE-04] 授权后停止未处理格；已执行方块回调/掉落不重发也不伪造回滚。
        return StructureOperation.unsupported("damage_cancel_scaffold");
    }

    @Override
    public StructureOperation.Result<StructureImpactFeed.Batch> drain(StructureImpactFeed.LaunchAccess access, StructureImpactFeed.Cursor cursor) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(cursor, "cursor");
        // [TODO: Agent B][UPG-IMPACT-01] 批量消费真实子步接触；溢出令首撞 UNKNOWN，禁止猜测或伪造空成功。
        return StructureOperation.unsupported("impact_feed_scaffold");
    }

    @Override
    public StructureOperation.Result<StructureFracture.Plan> plan(StructureImpactFeed.LaunchAccess access, StructureFracture.Request request) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(request, "request");
        // [TODO: Agent B][UPG-FRACTURE-01] 按局部能量/材质/连通性分配每格唯一所有权，保留不支持内容。
        return StructureOperation.unsupported("fracture_plan_scaffold");
    }

    @Override
    public StructureOperation.Result<StructureFracture.Reservation> reserve(StructureImpactFeed.LaunchAccess access, StructureFracture.Plan plan) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(plan, "plan");
        // [TODO: Agent B][UPG-FRACTURE-02] 整组预留存储/刚体/地形/同步额度，失败保留源且不输出战利品。
        return StructureOperation.unsupported("fracture_reserve_scaffold");
    }

    @Override
    public StructureOperation.Result<StructureFracture.Receipt> commit(StructureImpactFeed.LaunchAccess access, StructureFracture.Reservation reservation) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(reservation, "reservation");
        // [TODO: Agent B][UPG-FRACTURE-03] 持久所有权提交后发布子体、继承来源但不继承首撞增益，再清理源。
        return StructureOperation.unsupported("fracture_commit_scaffold");
    }

    @Override
    public StructureOperation.Result<StructureFracture.Progress> status(StructureImpactFeed.LaunchAuthority authority, UUID operationId) {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(operationId, "operationId");
        // [TODO: Agent B][UPG-FRACTURE-04] 验证持久发射身份和服务端策略后查询计划/预留/清理进度，不依赖已删除父体。
        return StructureOperation.unsupported("fracture_status_scaffold");
    }

    @Override
    public StructureOperation.Result<StructureFracture.Progress> abort(StructureImpactFeed.LaunchAuthority authority, UUID operationId) {
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(operationId, "operationId");
        // [TODO: Agent B][UPG-FRACTURE-05] 授权后回滚提交前写入并释放整组预留；提交后只继续源清理，不复活父体。
        return StructureOperation.unsupported("fracture_abort_scaffold");
    }
}
