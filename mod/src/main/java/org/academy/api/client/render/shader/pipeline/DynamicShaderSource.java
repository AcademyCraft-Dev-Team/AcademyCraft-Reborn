package org.academy.api.client.render.shader.pipeline;

import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态着色器源（契约实现）。把图生成的 GLSL 源码以内容哈希路径注册，
 * 供 {@code GpuDevice.compilePipeline(pipeline, this, executor)} 编译。
 */
public final class DynamicShaderSource implements ShaderSource {
    private final Map<String, String> sources = new ConcurrentHashMap<>();

    /**
     * 注册源码，返回其唯一 Identifier（内容哈希，相同源码去重）。
     */
    public Identifier register(String source) {
        var path = "graph/" + sha256(source);
        sources.put(path, source);
        return Identifier.fromNamespaceAndPath("academy", path);
    }

    @Override
    @Nullable
    public String getShader(Identifier id, ShaderType type) {
        return sources.get(id.getPath());
    }

    @Override
    public ShaderSource.@Nullable CachedIncludeSource getInclude(Identifier id) {
        return null;
    }

    @Override
    public void close() {
    }

    private static String sha256(String source) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder(bytes.length * 2);
            for (var b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
