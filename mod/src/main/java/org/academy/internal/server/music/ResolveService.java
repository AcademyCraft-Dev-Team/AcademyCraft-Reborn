package org.academy.internal.server.music;

import net.minecraft.server.level.ServerPlayer;
import org.academy.AcademyCraft;
import org.academy.internal.common.music.SlidingWindowRateLimiter;
import org.academy.internal.common.music.provider.MusicProviderApi;
import org.academy.internal.common.music.provider.MusicProviders;
import org.academy.internal.common.music.provider.ProviderCredential;
import org.academy.internal.common.network.MusicAccountPackets;
import org.academy.internal.server.config.MusicConfig;
import org.jspecify.annotations.Nullable;
import org.misaka.MisakaNetworkServer;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 共享账号直链解析代理：TTL 缓存 + 单飞去重 + 滑动窗口限流，全部在后台线程池执行，
 * 绝不阻塞服务器线程喵。
 */
public final class ResolveService {
    public static final String ERROR_DISABLED = "disabled";
    public static final String ERROR_NO_ACCOUNT = "no_account";
    public static final String ERROR_RATE_LIMITED = "rate_limited";

    public record ResolveResult(List<String> urls, String error) {
        public ResolveResult {
            urls = urls == null ? List.of() : List.copyOf(urls);
            error = error == null ? "" : error;
        }

        public boolean success() {
            return error.isEmpty();
        }

        public static ResolveResult failure(String error) {
            return new ResolveResult(List.of(), error);
        }
    }

    private record CacheEntry(ResolveResult result, long cachedAtMillis) {
    }

    private static final Map<String, CacheEntry> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<ResolveResult>> IN_FLIGHT = new ConcurrentHashMap<>();
    private static volatile SlidingWindowRateLimiter limiter =
            new SlidingWindowRateLimiter(30, 60_000L);
    private static volatile int ttlSeconds = 600;

    private ResolveService() {
    }

    public static void init(MusicConfig config) {
        limiter = new SlidingWindowRateLimiter(
                Math.max(1, config.sharedAccount.resolveRateLimitPerMinute), 60_000L);
        ttlSeconds = Math.max(1, config.sharedAccount.resolveCacheTtlSeconds);
        CACHE.clear();
        IN_FLIGHT.clear();
    }

    public static CompletableFuture<ResolveResult> resolve(String provider, String trackId) {
        var normalized = normalize(provider);
        if (!SharedAccountService.isEnabled()) {
            return CompletableFuture.completedFuture(ResolveResult.failure(ERROR_DISABLED));
        }
        var credential = SharedAccountService.credentialFor(normalized);
        var key = normalized + ":" + trackId;
        var cached = lookupCache(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        if (!limiter.tryAcquire(System.currentTimeMillis())) {
            return CompletableFuture.completedFuture(ResolveResult.failure(ERROR_RATE_LIMITED));
        }
        var providerApi = MusicProviders.byName(normalized).orElse(null);
        if (providerApi == null) {
            return CompletableFuture.completedFuture(ResolveResult.failure(ERROR_NO_ACCOUNT));
        }
        var existing = IN_FLIGHT.get(key);
        if (existing != null) return existing;

        var future = CompletableFuture.supplyAsync(() -> doResolve(providerApi, trackId, credential),
                        AcademyCraft.executorService)
                .whenComplete((_, _) -> IN_FLIGHT.remove(key));
        IN_FLIGHT.put(key, future);
        return future;
    }

    private static ResolveResult doResolve(
            MusicProviderApi providerApi,
            String trackId,
            ProviderCredential credential
    ) {
        try {
            var candidate = providerApi.resolveTrack(trackId, credential);
            return new ResolveResult(candidate.streamUrls(), "");
        } catch (Exception exception) {
            var message = exception.getMessage();
            return ResolveResult.failure(message == null || message.isBlank()
                    ? "resolve_failed" : message);
        }
    }

    private static @Nullable ResolveResult lookupCache(String key) {
        var entry = CACHE.get(key);
        if (entry == null) return null;
        if ((System.currentTimeMillis() - entry.cachedAtMillis()) / 1000L >= ttlSeconds) {
            CACHE.remove(key);
            return null;
        }
        return entry.result();
    }

    public static void handleRequest(
            MusicAccountPackets.ResolveRequestPacket packet,
            ServerPlayer player
    ) {
        var server = player.level().getServer();
        resolve(packet.provider(), packet.trackId())
                .thenAccept(result -> server.execute(() -> {
                    if (player.hasDisconnected()) return;
                    MisakaNetworkServer.send(player,
                            new MusicAccountPackets.ResolveResponsePacket(
                                    packet.provider(), packet.trackId(), result.error(), result.urls()));
                }));
    }

    private static String normalize(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }
}
