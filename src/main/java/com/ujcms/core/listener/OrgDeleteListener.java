package com.ujcms.core.listener;

/**
 * 组织删除监听器
 *
 * @author PONY
 */
public interface OrgDeleteListener {
    /**
     * 组织删除前回调
     *
     * @param orgId 组织ID
     */
    void preOrgDelete(Integer orgId);
}
