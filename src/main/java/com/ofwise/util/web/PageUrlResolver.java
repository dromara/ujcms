package com.ofwise.util.web;

/**
 * 分页url获取接口
 *
 * @author liufang
 */
public interface PageUrlResolver {
    /**
     * 获取 URL 地址
     *
     * @param page 页码
     * @return URL地址
     */
    String getUrl(int page);

    /**
     * 获取 动态URL 地址
     *
     * @param page 页码
     * @return 动态URL
     */
    String getDynamicUrl(int page);
}
