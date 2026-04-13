package com.ujcms.cms.core.support;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * html a 接口
 *
 * @author liufang
 */
public interface Anchor {
    /**
     * 获取链接名称
     *
     * @return 名称
     */
    @Schema(description = "名称")
    String getName();

    /**
     * 获取链接 URL
     *
     * @return URL
     */
    @Schema(description = "URL地址")
    String getUrl();

    /**
     * 获取完整链接 URL（始终包含协议、域名、端口），用于 Sitemap、RSS 等需要绝对地址的场景
     *
     * @return 完整 URL
     */
    @Schema(description = "完整URL地址")
    default String getFullUrl() {
        return getUrl();
    }

    /**
     * 获取是否新窗口打开
     *
     * @return 是否新窗口打开
     */
    @Schema(description = "是否新窗口打开")
    default Boolean getTargetBlank() {
        return false;
    }
}