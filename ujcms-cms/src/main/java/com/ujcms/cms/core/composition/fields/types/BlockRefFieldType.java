package com.ujcms.cms.core.composition.fields.types;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldType;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 区块引用字段（component-settings §5）。值：区块ID（正整数）。仅用于组件设置（面板即组件实例）：
 * 读取时由 {@link com.ujcms.cms.core.composition.SettingsResolver} 解析为区块摘要对象
 * {@code {id,alias,name,title}}，别名形态（种子缺省值）亦在读取期解析；数据模板经
 * {@code where: {"blockId": "$settings.…"}} 取区块项。不是条目字段类型（RefResolver 不解析它）。
 *
 * @author PONY
 */
@Component
public class BlockRefFieldType implements FieldType {
    @Override
    public String name() {
        return "blockRef";
    }

    @Override
    public List<String> validate(Object value, FieldDef def, FieldValueValidator ctx) {
        if (FieldValues.asRefId(value) == null) {
            return List.of("必须为区块ID(正整数)");
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
