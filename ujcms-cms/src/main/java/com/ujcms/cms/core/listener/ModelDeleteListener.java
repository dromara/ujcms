package com.ujcms.cms.core.listener;

/**
 * 模型删除监听器
 *
 * @author PONY
 */
public interface ModelDeleteListener {
    /**
     * 模型删除前回调
     *
     * @param modelId 模型ID
     */
    void preModelDelete(Long modelId);
}
