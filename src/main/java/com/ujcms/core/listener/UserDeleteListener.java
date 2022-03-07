package com.ujcms.core.listener;

/**
 * 用户删除监听器
 *
 * @author PONY
 */
public interface UserDeleteListener {
    /**
     * 用户删除前回调
     *
     * @param userId 用户ID
     */
    void preUserDelete(Integer userId);
}
