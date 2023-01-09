package com.ujcms.util.web.exception;

import org.springframework.lang.Nullable;

/**
 * SC_GONE 已经不存在 异常。请求的文档已经不存在并且没有更新的地址。
 * <p>
 * 410状态不同于404，410是在指导文档已被移走的情况下使用，而404则用于未知原因的无法访问
 *
 * @author PONY
 */
public class Http410Exception extends AbstractMessagedException {
    private static final long serialVersionUID = 7374820636835696053L;

    public Http410Exception(String code, @Nullable String... args) {
        super(code, args);
    }
}