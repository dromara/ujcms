package com.ujcms.cms.core.composition.fields.types;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldType;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 文章引用字段。值：文章ID（正整数）。
 * 读取时由 {@link com.ujcms.cms.core.composition.RefResolver} 解析为文章摘要对象
 * {@code {id,title,url,image}}，文章已删除时返回占位 {@code {id,deleted:true}}（§7.3）。
 *
 * @author PONY
 */
@Component
public class ArticleRefFieldType implements FieldType {
    @Override
    public String name() {
        return "articleRef";
    }

    @Override
    public List<String> validate(Object value, FieldDef def, FieldValueValidator ctx) {
        if (FieldValues.asRefId(value) == null) {
            return List.of("必须为文章ID(正整数)");
        }
        return Collections.emptyList();
    }

    /**
     * 归一化为 Long 存储（雪花ID超出 JS 安全整数范围，控制台以字符串提交，§10.1）
     */
    @Override
    public Object processOnSave(Object value, FieldDef def, FieldValueValidator ctx) {
        Long id = FieldValues.asRefId(value);
        return id != null ? id : value;
    }
}
