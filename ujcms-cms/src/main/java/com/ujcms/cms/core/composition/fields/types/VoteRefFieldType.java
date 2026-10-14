package com.ujcms.cms.core.composition.fields.types;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldType;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 投票引用字段（vote-micro-region §2）。值：投票ID（正整数）。仅用于组件设置（面板即组件实例）：
 * 读取时由 {@link com.ujcms.cms.core.composition.SettingsResolver} 解析为投票摘要对象
 * {@code {id,title}}；实体本身由模板片段经 {@code [@Vote id=…]} 自取（RefResolver 不解析它，
 * 引擎值为经存在性与站点归属校验的裸ID透传）。不是条目字段类型。
 *
 * @author PONY
 */
@Component
public class VoteRefFieldType implements FieldType {
    @Override
    public String name() {
        return "voteRef";
    }

    @Override
    public List<String> validate(Object value, FieldDef def, FieldValueValidator ctx) {
        if (FieldValues.asRefId(value) == null) {
            return List.of("必须为投票ID(正整数)");
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
