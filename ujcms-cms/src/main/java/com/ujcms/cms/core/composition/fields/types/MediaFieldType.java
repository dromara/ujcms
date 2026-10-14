package com.ujcms.cms.core.composition.fields.types;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldType;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 媒体（图片/视频）引用字段。值：{@code {url, alt, width, height, mime, byteLength}}。
 * 参数：{@code aspectRatio}、{@code constraints: {minWidth, maxBytes, formats}}。
 *
 * @author PONY
 */
@Component
public class MediaFieldType implements FieldType {
    @Override
    public String name() {
        return "media";
    }

    @Override
    public List<String> validate(Object value, FieldDef def, FieldValueValidator ctx) {
        Map<String, Object> map = FieldValues.asMap(value);
        if (map == null) {
            return List.of("必须为媒体对象");
        }
        List<String> errors = new ArrayList<>();
        String url = FieldValues.getString(map, "url");
        if (url == null || url.isBlank()) {
            errors.add("url 不能为空");
        } else if (!FieldValues.URL_PATTERN.matcher(url).matches()) {
            errors.add("url 必须以 http 或 / 开头");
        }
        FieldDef constraints = def.getFieldDef("constraints");
        if (constraints != null) {
            Integer minWidth = constraints.getInteger("minWidth");
            Number width = FieldValues.getNumber(map, "width");
            if (minWidth != null && width != null && width.intValue() < minWidth) {
                errors.add("宽度不能小于 " + minWidth);
            }
            Long maxBytes = constraints.getLong("maxBytes");
            Number byteLength = FieldValues.getNumber(map, "byteLength");
            if (maxBytes != null && byteLength != null && byteLength.longValue() > maxBytes) {
                errors.add("文件大小不能超过 " + maxBytes + " 字节");
            }
            List<String> formats = constraints.getStringList("formats");
            String mime = FieldValues.getString(map, "mime");
            if (!formats.isEmpty() && mime != null && formats.stream().noneMatch(mime::endsWith)) {
                errors.add("格式必须为 " + formats + " 之一");
            }
        }
        return errors;
    }
}
