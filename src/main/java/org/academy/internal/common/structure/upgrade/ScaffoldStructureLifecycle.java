package org.academy.internal.common.structure.upgrade;

import net.minecraft.server.level.ServerLevel;
import org.academy.api.common.structure.upgrade.StructureGridRestoration;
import org.academy.api.common.structure.upgrade.StructureOperation;
import org.academy.api.common.structure.upgrade.StructureProvenance;

import java.util.Objects;
import java.util.UUID;

/** Inert replacement point: no ledger writes, world reads/writes, timers, tickets, or fabricated receipts. */
public final class ScaffoldStructureLifecycle implements StructureProvenance, StructureGridRestoration {
    @Override
    public StructureOperation.Result<Metadata> query(StructureOperation.Context context) {
        Objects.requireNonNull(context, "context");
        // [TODO: Agent B][UPG-LIFE-QUERY] 读取版本化来源；未知后端/格式保留内容，不能把不可用误报为无标记。
        return StructureOperation.unsupported("provenance_query_unimplemented");
    }

    @Override
    public StructureOperation.Result<Preparation> prepare(StructureOperation.Context context, Metadata metadata) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(metadata, "metadata");
        // [TODO: Agent B][UPG-LIFE-PREPARE] 校验创建事务、父子来源和策略版本；仅准备镜像，由所属事务原子提交。
        return StructureOperation.unsupported("provenance_prepare_unimplemented");
    }

    @Override
    public StructureOperation.Result<Preview> preview(Access access, Options options) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(options, "options");
        // [TODO: Agent B][UPG-LIFE-PREVIEW] 只读规划四向回正，检查已加载空目标、真实形状、权限及内容依赖并绑定版本。
        return StructureOperation.unsupported("restoration_preview_unimplemented");
    }

    @Override
    public StructureOperation.Result<Progress> request(Access access, Request request) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(request, "request");
        // [TODO: Agent B][UPG-LIFE-REQUEST] 复核服务端预览与静止/租约资格，强日志保存目标后转移所有权，再清理源。
        return StructureOperation.unsupported("restoration_request_unimplemented");
    }

    @Override
    public StructureOperation.Result<Progress> status(ServerLevel host, RestorationActor actor, UUID operationId) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(operationId, "operationId");
        // [TODO: Agent B][UPG-LIFE-STATUS] 核验持久拥有者和服务端策略后按操作 ID 查阶段；UUID 不是授权，离线未知权限拒绝。
        return StructureOperation.unsupported("restoration_status_unimplemented");
    }

    @Override
    public StructureOperation.Result<Progress> cancel(Access access, UUID operationId) {
        Objects.requireNonNull(access, "access");
        Objects.requireNonNull(operationId, "operationId");
        // [TODO: Agent B][UPG-LIFE-CANCEL] 仅取消提交前操作；部分写入先精确回滚，已提交事务继续幂等清理源。
        return StructureOperation.unsupported("restoration_cancel_unimplemented");
    }

    @Override
    public StructureOperation.Result<Hold> hold(StructureOperation.Context context, HoldRequest request) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(request, "request");
        // [TODO: Agent B][UPG-LIFE-HOLD] 验证真实活动控制者并授予有期限延缓；只读引用不得阻止归块。
        return StructureOperation.unsupported("restoration_hold_unimplemented");
    }

    @Override
    public StructureOperation.Result<Hold> renewHold(StructureOperation.Context context, UUID holdId,
                                                    HoldRequest request) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(holdId, "holdId");
        Objects.requireNonNull(request, "request");
        // [TODO: Agent B][UPG-LIFE-HOLD-RENEW] 仅同一有效控制者续租并重验权限；过期、换维或旧代引用拒绝。
        return StructureOperation.unsupported("restoration_hold_renew_unimplemented");
    }

    @Override
    public StructureOperation.Result<Void> releaseHold(StructureOperation.Context context, UUID holdId) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(holdId, "holdId");
        // [TODO: Agent B][UPG-LIFE-HOLD-RELEASE] 幂等释放本控制者的租约并重新累计静止时间，不直接执行还原。
        return StructureOperation.unsupported("restoration_hold_release_unimplemented");
    }
}
