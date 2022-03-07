package com.ujcms.core.listener;

/**
 * 栏目删除监听器
 *
 * @author PONY
 */
public interface ChannelDeleteListener {
    /**
     * 栏目删除前回调
     *
     * @param channelId 栏目ID
     */
    void preChannelDelete(Integer channelId);
}
