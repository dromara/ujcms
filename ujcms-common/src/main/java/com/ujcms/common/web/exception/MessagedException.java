package com.ujcms.common.web.exception;

import org.jspecify.annotations.Nullable;

/**
 * @author PONY
 */
public interface MessagedException {
    /**
     * 获取消息代码
     *
     * @return 消息代码
     */
    @Nullable
    String getCode();

    /**
     * 获取消息参数
     *
     * @return 消息参数
     */
    @Nullable
    String[] getArgs();
}
