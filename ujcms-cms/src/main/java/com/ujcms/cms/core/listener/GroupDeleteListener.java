package com.ujcms.cms.core.listener;

/**
 * 用户组删除监听器
 *
 * @author PONY
 */
public interface GroupDeleteListener {
    /**
     * 用户组删除前回调
     *
     * @param groupId 用户组ID
     */
    void preGroupDelete(Long groupId);
}
