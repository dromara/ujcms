package com.ujcms.cms.core.domain.global;

import org.springframework.lang.Nullable;

/**
 * Global的抽象类
 *
 * @author PONY
 */
public interface GlobalData {
    /**
     * 获取名称
     *
     * @return 名称
     */
    String getName();

    /**
     * 获取值
     *
     * @return 值
     */
    String getValue();
}
