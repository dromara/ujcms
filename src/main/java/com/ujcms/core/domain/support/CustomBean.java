package com.ujcms.core.domain.support;

import org.springframework.lang.Nullable;

/**
 * Custom 类的抽象接口。方便统一处理Custom，如 ArticleCustom、ChannelCustom
 *
 * @author PONY
 */
public interface CustomBean {
    /**
     * 获取名称
     *
     * @return 名称
     */
    String getName();

    /**
     * 设置名称
     *
     * @param name 名称
     */
    void setName(String name);

    /**
     * 获取值
     *
     * @return 值
     */
    @Nullable
    String getValue();

    /**
     * 设置值
     *
     * @param value 值
     */
    void setValue(@Nullable String value);
}
