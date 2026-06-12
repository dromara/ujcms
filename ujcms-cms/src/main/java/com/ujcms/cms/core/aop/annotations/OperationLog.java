package com.ujcms.cms.core.aop.annotations;

import com.ujcms.cms.core.aop.enums.OperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 *
 * @author PONY
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    /**
     * 模块
     */
    String module();

    /**
     * 操作
     */
    String operation();

    /**
     * 类型
     */
    OperationType type() default OperationType.OTHER;
}
