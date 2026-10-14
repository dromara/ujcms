package com.ujcms.cms.core.composition.fields.types;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldType;
import com.ujcms.cms.core.composition.fields.FieldValueValidator;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 链接字段。值：{@code {url, mobileUrl, targetBlank, label}}。
 * 参数：{@code mobileOverride}、{@code allowTargetBlank}（默认 true）、{@code allowExternal}（默认 true）、
 * {@code withLabel}（默认 false）。
 *
 * <p>{@code label}（链接文字）只在字段声明 {@code withLabel: true} 时才有意义：编辑器才显示该输入，保存才保留该键。
 * 模板几乎总是用周边内容（栏目标题、条目标题）作链接文字，所以默认不开——模板不读的输入是纯成本
 * （2026-09-10，默认主题的「更多链接」曾带着无效的链接文字输入）。
 *
 * <p>URL 白名单口径：{@code http(s)://} 绝对地址、{@code /} 开头的站内地址（含 {@code //} 协议相对）、
 * {@code #} 页内锚点、{@code mailto:} / {@code tel:} 联系方式。白名单之外（含 {@code javascript:}、
 * {@code data:} 等脚本类协议与裸域名/相对路径）一律拒绝——值原样进入模板 {@code href}，不能放行无法
 * 识别的协议。首尾空白在校验与保存时均剥离（粘贴常带空格）。
 *
 * @author PONY
 */
@Component
public class LinkFieldType implements FieldType {
    /**
     * 链接地址白名单（大小写不敏感的协议前缀）。比 {@link FieldValues#URL_PATTERN}（媒体地址、
     * {@code BlockItem.linkUrl} 口径）多出锚点与 {@code mailto:} / {@code tel:}——只对点击跳转有意义。
     */
    static final Pattern LINK_URL_PATTERN = Pattern.compile("^(https?://|/|#|mailto:|tel:).*$",
            Pattern.CASE_INSENSITIVE);
    static final String URL_ERROR = " 必须以 http://、https://、/、# 或 mailto:、tel: 开头";

    @Override
    public String name() {
        return "link";
    }

    @Override
    public List<String> validate(Object value, FieldDef def, FieldValueValidator ctx) {
        Map<String, Object> map = FieldValues.asMap(value);
        if (map == null) {
            return List.of("必须为链接对象");
        }
        List<String> errors = new ArrayList<>();
        boolean allowExternal = def.getBoolean("allowExternal", true);
        String url = trimmed(map, "url");
        if (url == null || url.isEmpty()) {
            errors.add("url 不能为空");
        } else {
            validateUrl(url, allowExternal, "url", errors);
        }
        String mobileUrl = trimmed(map, "mobileUrl");
        if (mobileUrl != null && !mobileUrl.isEmpty()) {
            validateUrl(mobileUrl, allowExternal, "mobileUrl", errors);
        }
        if (!def.getBoolean("allowTargetBlank", true) && Boolean.TRUE.equals(map.get("targetBlank"))) {
            errors.add("不允许新窗口打开(targetBlank)");
        }
        return errors;
    }

    /**
     * 保存时剥离 {@code url} / {@code mobileUrl} 首尾空白（校验已按剥离后的值放行，落库保持一致）；
     * 未声明 {@code withLabel} 的字段丢弃 {@code label}（编辑器不显示的值不留存）。
     */
    @Override
    public Object processOnSave(Object value, FieldDef def, FieldValueValidator ctx) {
        Map<String, Object> map = FieldValues.asMap(value);
        if (map == null) {
            return value;
        }
        Map<String, Object> result = new LinkedHashMap<>(map);
        for (String key : List.of("url", "mobileUrl")) {
            String str = trimmed(map, key);
            if (str != null) {
                result.put(key, str);
            }
        }
        if (!def.getBoolean("withLabel", false)) {
            result.remove("label");
        }
        return result;
    }

    @Nullable
    private static String trimmed(Map<String, Object> map, String key) {
        String str = FieldValues.getString(map, key);
        return str == null ? null : str.strip();
    }

    private void validateUrl(String url, boolean allowExternal, String key, List<String> errors) {
        if (!LINK_URL_PATTERN.matcher(url).matches()) {
            errors.add(key + URL_ERROR);
        } else if (!allowExternal && !isInternal(url)) {
            errors.add(key + " 不允许外部链接");
        }
    }

    /**
     * 站内链接：{@code /} 开头（协议相对 {@code //host} 除外）或页内锚点。
     */
    private static boolean isInternal(String url) {
        return (url.startsWith("/") && !url.startsWith("//")) || url.startsWith("#");
    }
}
