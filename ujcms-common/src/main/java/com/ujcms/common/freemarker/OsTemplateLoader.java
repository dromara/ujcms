package com.ujcms.common.freemarker;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.Strings;
import org.springframework.core.io.AbstractFileResolvingResource;
import org.jspecify.annotations.Nullable;
import org.springframework.ui.freemarker.SpringTemplateLoader;

import com.ujcms.common.file.FilesEx;

import freemarker.cache.URLTemplateLoader;

/**
 * 对象存储模板加载器（{@code ujcms.template-loader-url} 配置远程模板存储时启用；本地存储部署不装配）。
 * <p>
 * spring.freemarker.template-loader-path 也支持 URL格式，但自定义的方式有更好的控制，如处理因 Locale 导致多次加载模板等问题。
 * <p>
 * {@link #getURL} 只做存在性探测（HEAD）——模板内容由 {@code URLTemplateSource} 另行开连接读取、
 * 经 {@code closeTemplateSource} 关闭，探测连接的生命周期完全在本方法内，须在返回前释放
 * （此前 HTTP_OK 与 404 路径均未释放）。404 结果按 TTL 负缓存：FreeMarker 的 Locale 回退
 * （{@code name_zh_CN → name_zh → name}）与 {@code ignoreMissing} 包含使缺失名被反复探测，
 * 每次都是一趟远程往返；TTL 不高于模板缓存的更新延迟（{@code template_update_delay} 默认 5 秒），
 * 不拖慢热编辑回路——新建模板最迟一个 TTL 后可见，与既有模板的修改可见延迟一致。
 * 仅 404 负缓存；其它错误码（存储端 5xx 等）视为暂时故障，不缓存。
 *
 * @author PONY
 * @see SpringTemplateLoader
 */
public class OsTemplateLoader extends URLTemplateLoader {
    /**
     * 负缓存默认 TTL（毫秒）：与 FreeMarker {@code template_update_delay} 默认值（5 秒）对齐。
     */
    public static final long DEFAULT_NEGATIVE_TTL_MILLIS = 5000L;
    /**
     * 负缓存条目上限（防病态名爆炸；达到上限整体清空——负缓存只是省探测，清空无正确性影响）
     */
    private static final int NEGATIVE_CACHE_MAX_SIZE = 10_000;

    private final String prefix;
    private final long negativeTtlMillis;
    /**
     * 负缓存：模板名 → 失效时刻（epoch 毫秒）
     */
    private final Map<String, Long> negativeCache = new ConcurrentHashMap<>();

    public OsTemplateLoader(String prefix) {
        this(prefix, DEFAULT_NEGATIVE_TTL_MILLIS);
    }

    /**
     * @param prefix            模板 URL 前缀
     * @param negativeTtlMillis 404 负缓存 TTL（毫秒；{@code <= 0} 关闭负缓存）
     */
    public OsTemplateLoader(String prefix, long negativeTtlMillis) {
        this.prefix = Strings.CS.endsWith(prefix, FilesEx.SLASH) ? prefix : prefix + FilesEx.SLASH;
        this.negativeTtlMillis = negativeTtlMillis;
    }

    /**
     * @see AbstractFileResolvingResource#exists()
     */
    @Nullable
    @Override
    protected URL getURL(String name) {
        Long deadline = negativeCache.get(name);
        if (deadline != null) {
            if (System.currentTimeMillis() < deadline) {
                return null;
            }
            negativeCache.remove(name);
        }
        try {
            URL url = new URL(prefix + name);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try {
                // 存在性探测无需响应体（对象存储均支持 HEAD）；内容读取由 URLTemplateSource 自开连接
                conn.setRequestMethod("HEAD");
                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_OK) {
                    return url;
                }
                if (code == HttpURLConnection.HTTP_NOT_FOUND && negativeTtlMillis > 0) {
                    if (negativeCache.size() >= NEGATIVE_CACHE_MAX_SIZE) {
                        negativeCache.clear();
                    }
                    negativeCache.put(name, System.currentTimeMillis() + negativeTtlMillis);
                }
                return null;
            } finally {
                closeQuietly(conn.getErrorStream());
                conn.disconnect();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void closeQuietly(@Nullable InputStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException ignored) {
            // 释放探测连接的收尾动作，失败无碍
        }
    }
}
