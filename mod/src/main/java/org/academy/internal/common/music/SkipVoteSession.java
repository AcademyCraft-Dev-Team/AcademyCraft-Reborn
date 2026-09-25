package org.academy.internal.common.music;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * 切歌投票会话：每人一票，窗口期以服务器 tick 计。纯逻辑，便于单元测试喵。
 */
public final class SkipVoteSession {
    private final LinkedHashMap<UUID, String> votes = new LinkedHashMap<>();
    private final long startedAtGameTime;
    private final long windowTicks;

    public SkipVoteSession(long gameTime, long windowTicks, UUID firstVoter, String voterName) {
        this.startedAtGameTime = gameTime;
        this.windowTicks = windowTicks;
        votes.put(firstVoter, voterName == null ? "" : voterName);
    }

    /**
     * @return true 表示这是新的一票；false 表示已投过
     */
    public boolean addVote(UUID uuid, String voterName) {
        if (votes.containsKey(uuid)) return false;
        votes.put(uuid, voterName == null ? "" : voterName);
        return true;
    }

    public boolean isExpired(long gameTime) {
        return gameTime - startedAtGameTime >= windowTicks;
    }

    /**
     * 移除一票（玩家登出/取消订阅时），投票归零后由调用方决定是否废弃会话喵。
     */
    public boolean removeVote(UUID uuid) {
        return votes.remove(uuid) != null;
    }

    public int voteCount() {
        return votes.size();
    }

    public List<String> voterNames() {
        return new ArrayList<>(votes.values());
    }

    public long endsAtGameTime() {
        return startedAtGameTime + windowTicks;
    }
}
