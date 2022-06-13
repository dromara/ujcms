package com.ujcms.cms.core.domain.support;

import com.ujcms.cms.core.domain.Model;
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
     * 获取类型
     *
     * @return 类型
     */
    String getType();

    /**
     * 设置类型
     *
     * @param type 类型
     */
    void setType(String type);

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

    /**
     * 是否是富文本编辑器
     *
     * @return 是或否
     */
    default boolean isRichEditor() {
        return Model.TYPE_RICH_EDITOR.equals(getType());
    }
}
