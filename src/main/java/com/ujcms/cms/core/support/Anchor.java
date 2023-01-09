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
     * 获取是否新窗口打开
     *
     * @return 是否新窗口打开
     */
    @Schema(description = "是否新窗口打开")
    default Boolean getTargetBlank() {
        return false;
    }
}