package com.ujcms.commons.web.exception;

import org.springframework.lang.Nullable;

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
