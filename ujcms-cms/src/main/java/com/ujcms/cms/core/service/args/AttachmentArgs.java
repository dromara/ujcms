package com.ujcms.cms.core.service.args;

import com.ujcms.common.query.BaseQueryArgs;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 组织查询参数
 *
 * @author PONY
 */
public class AttachmentArgs extends BaseQueryArgs {
    public AttachmentArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    /**
     * 按文件名关键字模糊过滤（与共享/我的范围的 keyword 参数语义一致）
     */
    public AttachmentArgs keyword(@Nullable String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            queryMap.put("Contains_name", keyword.trim());
        }
        return this;
    }

    public AttachmentArgs used(@Nullable Boolean used) {
        if (used != null) {
            queryMap.put("EQ_used_Boolean", used);
        }
        return this;
    }

    /**
     * 按文件扩展名过滤。生成形如 (name LIKE '%.jpg' OR name LIKE '%.png' OR ...) 的 OR 条件。
     * 扩展名清单由调用方根据 {@code Config.Upload} 配置解析后传入。
     */
    public AttachmentArgs extensions(@Nullable List<String> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            return this;
        }
        int i = 0;
        for (String ext : extensions) {
            if (ext == null) {
                continue;
            }
            String cleaned = ext.trim().toLowerCase();
            if (cleaned.isEmpty()) {
                continue;
            }
            queryMap.put("EndsWith_1-" + i + "_name", "." + cleaned);
            i++;
        }
        return this;
    }

    public static AttachmentArgs of() {
        return of(new HashMap<>(16));
    }

    public static AttachmentArgs of(Map<String, Object> queryMap) {
        return new AttachmentArgs(queryMap);
    }

    private AttachmentArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }
}
