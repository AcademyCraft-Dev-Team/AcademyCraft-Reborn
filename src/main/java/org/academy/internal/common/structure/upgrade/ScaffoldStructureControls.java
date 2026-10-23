package org.academy.internal.common.structure.upgrade;

import org.academy.api.common.structure.upgrade.StructureExtraction;
import org.academy.api.common.structure.upgrade.StructureOperation;
import org.academy.api.common.structure.upgrade.StructureOrientation;

import java.util.Objects;

/** Disabled replacement boundary. No method resolves, loads, controls or changes a live structure. */
public final class ScaffoldStructureControls implements StructureExtraction, StructureOrientation {
    @Override
    public StructureOperation.Result<StructureExtraction.CapturePlan> planCapture(
            StructureOperation.ActorContext context, StructureExtraction.CaptureRequest request) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(request, "request");
        // [TODO: Agent B][UPG-CTRL-10] 捕获前核查主体、来源、真实抬升路径及预算；在服务端保存源内容摘要、地形版本和到期时间，不捕获或授予租约。
        return StructureOperation.unsupported("extraction_capture_plan_not_implemented");
    }

    @Override
    public StructureOperation.Result<StructureExtraction.Plan> bindCapture(
            StructureOperation.Context context, StructureExtraction.BindCaptureRequest request) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(request, "request");
        // [TODO: Agent B][UPG-CTRL-11] 从服务端计划与捕获日志解析完成结果，复核同源摘要、主体、宿主、激活、租约、地形及到期；不得信任调用方预览或伪造捕获回执。
        return StructureOperation.unsupported("extraction_capture_bind_not_implemented");
    }

    @Override
    public StructureOperation.Result<StructureExtraction.Plan> plan(
            StructureOperation.Context context, StructureExtraction.PlanRequest request) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(request, "request");
        // [TODO: Agent B][UPG-CTRL-01] 校验独占租约与源版本，新捕获须已有可信绑定；按真实形状规划净空、软障碍和有界退让，不移动或捕获内容。
        return StructureOperation.unsupported("extraction_plan_not_implemented");
    }

    @Override
    public StructureOperation.Result<StructureExtraction.Snapshot> begin(
            StructureOperation.Context context, StructureExtraction.BeginRequest request) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(request, "request");
        // [TODO: Agent B][UPG-CTRL-02] 复核计划版本及租约；新捕获首步前才可初始化源姿态，随后子步限力提取，稳定到位后独立累计悬浮时间。
        return StructureOperation.unsupported("extraction_begin_not_implemented");
    }

    @Override
    public StructureOperation.Result<StructureExtraction.Snapshot> status(
            StructureOperation.Context context, StructureExtraction.StatusRequest request) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(request, "request");
        // [TODO: Agent B][UPG-CTRL-03] 只查询同一主体、运行期与控制令牌的提取阶段；受阻、超时及缺地形不得回报完成。
        return StructureOperation.unsupported("extraction_status_not_implemented");
    }

    @Override
    public StructureOperation.Result<StructureExtraction.Snapshot> cancel(
            StructureOperation.Context context, StructureExtraction.CancelRequest request) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(request, "request");
        // [TODO: Agent B][UPG-CTRL-04] 幂等撤销精确操作的平移与姿态约束，保留内容及合法惯性；不得误停后继控制或隐式发射。
        return StructureOperation.unsupported("extraction_cancel_not_implemented");
    }

    @Override
    public StructureOperation.Result<StructureOrientation.Snapshot> rotateTo(
            StructureOperation.Context context, StructureOrientation.RotateRequest request) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(request, "request");
        // [UPG-CTRL-05] Disabled DRAFT placeholder; LIVE delegates to LiveStructureOrientation.rotateTo.
        return StructureOperation.unsupported("orientation_rotate_not_implemented");
    }

    @Override
    public StructureOperation.Result<StructureOrientation.Snapshot> reset(
            StructureOperation.Context context, StructureOrientation.ResetRequest request) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(request, "request");
        // [UPG-CTRL-06] Disabled DRAFT placeholder; LIVE delegates to LiveStructureOrientation.reset.
        return StructureOperation.unsupported("orientation_reset_not_implemented");
    }

    @Override
    public StructureOperation.Result<StructureOrientation.Snapshot> spin(
            StructureOperation.Context context, StructureOrientation.SpinRequest request) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(request, "request");
        // [UPG-CTRL-07] Disabled DRAFT placeholder; LIVE delegates to LiveStructureOrientation.spin.
        return StructureOperation.unsupported("orientation_spin_not_implemented");
    }

    @Override
    public StructureOperation.Result<StructureOrientation.Snapshot> stop(
            StructureOperation.Context context, StructureOrientation.StopRequest request) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(request, "request");
        // [UPG-CTRL-08] Disabled DRAFT placeholder; LIVE delegates to LiveStructureOrientation.stop.
        return StructureOperation.unsupported("orientation_stop_not_implemented");
    }

    @Override
    public StructureOperation.Result<StructureOrientation.Snapshot> status(
            StructureOperation.Context context, StructureOrientation.StatusRequest request) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(request, "request");
        // [UPG-CTRL-09] Disabled DRAFT placeholder; LIVE delegates to LiveStructureOrientation.status.
        return StructureOperation.unsupported("orientation_status_not_implemented");
    }
}
