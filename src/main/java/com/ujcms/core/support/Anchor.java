package com.ujcms.core.support;

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
    String getName();

    /**
     * 获取链接 URL
     *
     * @return URL
     */
    String getUrl();

    /**
     * 获取是否新窗口打开
     *
     * @return 是否新窗口打开
     */
    default Boolean getTargetBlank() {
        return false;
    }
}