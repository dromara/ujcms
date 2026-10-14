package com.ujcms.cms.core.composition.fields.types;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldType;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.cms.core.security.PolicyTier;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 富文本字段。保存时通过既有的分级 {@link HtmlSanitizeService} 清理。
 * 参数：{@code sanitize: 'strict'|'lenient'}（默认 strict）。
 * strict 对应 {@link PolicyTier#BASIC}（格式/图片/链接），lenient 对应
 * {@link PolicyTier#RICH}（可信编辑者宽松白名单，含受限 iframe）。
 *
 * @author PONY
 */
@Component
public class HtmlFieldType implements FieldType {
    /**
     * 宽松清理策略参数值
     */
    public static final String SANITIZE_LENIENT = "lenient";

    private final HtmlSanitizeService sanitizeService;

    public HtmlFieldType(HtmlSanitizeService sanitizeService) {
        this.sanitizeService = sanitizeService;
    }

    @Override
    public String name() {
        return "html";
    }

    @Override
    public List<String> validate(Object value, FieldDef def, FieldValueValidator ctx) {
        if (!(value instanceof String)) {
            return List.of("必须为字符串");
        }
        return Collections.emptyList();
    }

    @Override
    public Object processOnSave(Object value, FieldDef def, FieldValueValidator ctx) {
        if (!(value instanceof String html)) {
            return value;
        }
        PolicyTier tier = SANITIZE_LENIENT.equals(def.getString("sanitize")) ? PolicyTier.RICH : PolicyTier.BASIC;
        return Optional.ofNullable(sanitizeService.sanitize(html, tier)).orElse("");
    }
}
