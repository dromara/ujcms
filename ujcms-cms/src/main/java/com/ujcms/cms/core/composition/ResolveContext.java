package com.ujcms.cms.core.composition;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 插槽解析上下文（§9.2）。携带预览标记、主题命名空间与 {@code $ctx.*} 查询参数。
 *
 * @param themeKey  主题命名空间（规范化主题键，如 {@code 1/default}）
 * @param preview   预览模式（跳过生命周期门槛，草稿可见；预览结果从不缓存）
 * @param ctxParams 渲染上下文参数（白名单内的 {@code $ctx.*} 值：channel / channelId / siteId，§14.2）。
 *                  供查询来源做 {@code $ctx.*} 替换；同时构成投放缓存键的 {@code ctxKey}（§19.1）
 * @author PONY
 */
public record ResolveContext(@Nullable String themeKey, boolean preview,
                                 Map<String, Object> ctxParams) {
    public ResolveContext {
        ctxParams = ctxParams == null ? Map.of() : Map.copyOf(ctxParams);
    }

    /**
     * 渲染上下文
     *
     * @param themeKey 规范化主题键
     */
    public static ResolveContext template(String themeKey) {
        return new ResolveContext(themeKey, false, Map.of());
    }

    /**
     * 渲染上下文（带渲染上下文参数）
     *
     * @param themeKey  规范化主题键
     * @param ctxParams 白名单内的 {@code $ctx.*} 值
     */
    public static ResolveContext template(String themeKey, Map<String, Object> ctxParams) {
        return new ResolveContext(themeKey, false, ctxParams);
    }

    /**
     * 预览上下文（草稿可见）
     *
     * @param themeKey 规范化主题键
     */
    public static ResolveContext templatePreview(String themeKey) {
        return new ResolveContext(themeKey, true, Map.of());
    }

    /**
     * 预览上下文（草稿可见，带渲染上下文参数）
     *
     * @param themeKey  规范化主题键
     * @param ctxParams 白名单内的 {@code $ctx.*} 值
     */
    public static ResolveContext templatePreview(String themeKey, Map<String, Object> ctxParams) {
        return new ResolveContext(themeKey, true, ctxParams);
    }

    /**
     * 读取某个 {@code $ctx.*} 参数（{@code null} 表示当前上下文不含该键）。
     */
    @Nullable
    public Object ctx(String key) {
        return ctxParams.get(key);
    }

    /**
     * 缓存键的 {@code ctxKey} 分量（§19.1）。$ctx 参数值的规范化串——按键排序，
     * 使按频道而异的解析结果不会在缓存中互相覆盖。空参数返回空串。
     */
    public String ctxKey() {
        if (ctxParams.isEmpty()) {
            return "";
        }
        return ctxParams.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }
}
