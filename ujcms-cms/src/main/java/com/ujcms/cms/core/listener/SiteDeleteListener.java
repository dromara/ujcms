package com.ujcms.cms.core.listener;

/**
 * 站点删除监听器
 *
 * @author PONY
 */
public interface SiteDeleteListener extends DeleteListenerOrder {
    /**
     * 站点删除前回调
     *
     * @param siteId 站点ID
     */
    void preSiteDelete(Long siteId);
}
