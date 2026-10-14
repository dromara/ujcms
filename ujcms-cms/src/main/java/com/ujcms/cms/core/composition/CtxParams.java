package com.ujcms.cms.core.composition;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 渲染上下文参数（{@code $ctx.*}）白名单（§14.2 / §16.1 / §19.4）。
 *
 * <p>查询来源的 {@code where} 过滤值可引用渲染上下文——{@code $ctx.channel}（当前频道别名）、
 * {@code $ctx.channelId}（当前频道ID）、{@code $ctx.siteId}（当前站点ID）。这使得单条 {@code channel}
 * 页面投放即可产出按频道而异的内容（新闻频道侧栏列新闻、体育频道列体育），无需逐实例投放。
 *
 * <p>允许的 {@code $ctx} 键是固定白名单：渲染期由服务端可信对象填充（模板轨道经
 * {@link RegionTemplateSupport} 从当前频道写入；{@code /api/region} 与预览端点只接受白名单内的值）。
 * 客户端提交的上下文值一律经 {@link #sanitize} 过滤。
 *
 * @author PONY
 */
public final class CtxParams {
    /**
     * {@code $ctx.channel} — 当前频道别名
     */
    public static final String CHANNEL = "channel";
    /**
     * {@code $ctx.channelId} — 当前频道ID
     */
    public static final String CHANNEL_ID = "channelId";
    /**
     * {@code $ctx.siteId} — 当前站点ID
     */
    public static final String SITE_ID = "siteId";

    /**
     * 全部允许的 {@code $ctx} 键（固定白名单，§14.2）
     */
    public static final Set<String> ALLOWED_KEYS = Set.of(CHANNEL, CHANNEL_ID, SITE_ID);

    /**
     * 查询 {@code where} 值中引用上下文参数的前缀（{@code "$ctx.channel"}）
     */
    public static final String PREFIX = "$ctx.";

    private CtxParams() {
    }

    /**
     * 是否为允许的 {@code $ctx} 键
     */
    public static boolean isAllowed(String key) {
        return ALLOWED_KEYS.contains(key);
    }

    /**
     * 过滤客户端提交的上下文值：仅保留白名单键、丢弃空值（{@code /api/slot}、预览端点用）。
     *
     * @param raw 原始上下文值（可为 {@code null}）
     * @return 仅含白名单键的不可变副本
     */
    public static Map<String, Object> sanitize(@Nullable Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>(raw.size());
        raw.forEach((key, value) -> {
            if (key == null || !ALLOWED_KEYS.contains(key) || value == null) {
                return;
            }
            if (value instanceof String str && StringUtils.isBlank(str)) {
                return;
            }
            result.put(key, value);
        });
        return result;
    }
}
