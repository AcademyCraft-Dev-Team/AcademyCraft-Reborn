package org.academy.api.common.structure;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 待实现的服务端权威结构交互协议；所有入口限定所属服务端线程，不注册网络包或事件。
 * 请求只提供观察与意图，工具、模式、权限、进度及物品数量均由服务端读取。
 * StructureHit.localFace/localPoint 使用结构局部坐标，worldPoint 使用可见宿主坐标；
 * 它们都不是隐藏存储坐标，也不构成授权。实现须重新解析引用、运行期身份和实际射线。
 */
public interface StructureInteractionController {
    /** START 建立会话；CONTINUE 仅续约；ABORT 即使目标已不可用也必须能清理旧会话。 */
    MiningReceipt mine(ServerPlayer actor, MiningRequest request);

    /** 方块放置回执；不允许客户端物品副本决定扣除、退款或成功。 */
    ActionReceipt place(ServerPlayer actor, ActionRequest request);

    /** 方块使用/容器开启回执；PASS 交还调用者继续处理副手等原版顺序。 */
    ActionReceipt use(ServerPlayer actor, ActionRequest request);

    /** 服务端每 tick 调用一次；唯一推进挖掘工作量的时钟，并发布异步进度与终态。 */
    void tick(MinecraftServer server, Feedback feedback);

    /** 离线、死亡、换维、工具/模式改变或显式中断；不要求目标仍可解析。 */
    void cancelActor(ServerPlayer actor, CancelReason reason, Feedback feedback);

    /** 只撤销精确 reference + runtimeId 的会话，不能清掉同引用重新加载后的新会话。 */
    void cancelStructure(MinecraftServer server, StructureRef reference, UUID runtimeId,
                         CancelReason reason, Feedback feedback);

    /** 停服时清理会话、裂纹及菜单；不可分配新运行时或重新加载结构。 */
    void close(MinecraftServer server, Feedback feedback);

    enum MiningAction { START, CONTINUE, ABORT }

    enum MiningStatus { STARTED, PROGRESS, COMPLETED, ABORTED, REJECTED }

    enum ActionStatus { APPLIED, PASS, REJECTED }

    enum CancelReason { CLIENT_ABORT, TARGET_CHANGED, ACTOR_UNAVAILABLE, STRUCTURE_UNAVAILABLE, SERVER_STOPPED }

    /**
     * requestId 是连接内单调递增的操作号；同次挖掘续约/中断沿用 START 的号。
     * START/CONTINUE 必须提供 observation；ABORT 可为空，不重新授权破坏。
     * 不接受客户端进度、硬度、工具或累计时长。
     */
    record MiningRequest(long requestId, MiningAction action, @Nullable StructureHit observation) {}

    /** observation.localFace 是点击的局部面，不得把世界最近轴方向当作放置邻格方向。 */
    record ActionRequest(long requestId, StructureHit observation, InteractionHand hand) {}

    /** 固定局部格地址及授权时的运行期身份；geometryRevision 不是持久结构 generation。 */
    record Target(SpaceBlockRef block, UUID runtimeId, long geometryRevision) {}

    /**
     * updateSequence 是该连接服务端回执的单调序号，用于丢弃乱序旧进度。
     * progress 范围 [0,1]；crackStage 范围 0..9，-1 表示清除；所有终态必须清除裂纹。
     * target 仅在无法识别会话/目标的拒绝回执中可为空；detail 为稳定原因码，不是本地化文案。
     */
    record MiningReceipt(long requestId, long updateSequence, @Nullable Target target,
                         MiningStatus status, float progress, int crackStage, String detail) {}

    /**
     * changedBlocks 是不可变的局部地址列表；拒绝/PASS 不代表允许扣物品。
     * 回执之后仍需同步真实格状态、几何及背包；resyncInventory 标记预测必须纠正。
     */
    record ActionReceipt(long requestId, @Nullable Target target, ActionStatus status,
                         List<SpaceBlockRef> changedBlocks, boolean swingHand,
                         boolean resyncInventory, String detail) {}

    /**
     * 不依赖 wire 包的输出端；实现选择宿主内实际观察者，并向旧观察者补发终态清除。
     * actorId 区分多人裂纹；recipient 为收件者，不得将其当作挖掘授权主体。
     * 同步 mine/place/use 的返回值由调用者确认；tick/cancel/close 的异步回执由此发出。
     */
    interface Feedback {
        void mining(ServerPlayer recipient, UUID actorId, MiningReceipt receipt);
    }
}
