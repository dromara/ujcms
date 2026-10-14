package com.ujcms.cms.core.security;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * HTML 清理策略层级。按内容作者的可信程度选择不同宽严的过滤策略。
 *
 * <ul>
 *     <li>{@link #RICH}：后台可信编辑者（文章正文、富文本自定义字段）。最宽松。</li>
 *     <li>{@link #BASIC}：半可信投稿。允许基本格式、链接、图片，禁用内联样式 / iframe。</li>
 *     <li>{@link #STRICT}：不可信的前台公开提交（如留言）。仅基本格式与 nofollow 链接。</li>
 *     <li>{@link #TEXT}：移除全部标签，仅保留文本（如问卷主观题答案）。</li>
 * </ul>
 *
 * @author PONY
 */
public enum PolicyTier {
    RICH, BASIC, STRICT, TEXT;

    /**
     * 解析字段配置的策略代码。空白或无法识别时回退到 {@link #RICH}（保持与旧行为一致的宽松默认）。
     *
     * @param code 策略代码（不区分大小写），通常来自 {@code Model.Field.policy}
     * @return 对应的策略层级，未匹配时为 {@link #RICH}
     */
    public static PolicyTier fromCode(@Nullable String code) {
        if (code == null || code.isBlank()) {
            return RICH;
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RICH;
        }
    }
}
