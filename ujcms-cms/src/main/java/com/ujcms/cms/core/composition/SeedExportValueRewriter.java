package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.domain.Model;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 导出值改写器（theme-seed-export §5.1 / §6）：按字段定义驱动遍历导出值——
 * <ul>
 *   <li><b>Shape 设置</b>（面板 settings）按 {@link SeedExportFieldRules.ShapeRule} 分派：
 *       channelRef/articleRef/blockRef 反向令牌化为 {@code {{channel:<alias>}}}/
 *       {@code {{article:<originId>}}}/{@code {{block:<alias>}}}（§6 / E4a；导入侧
 *       {@code FieldValues.asRefId} 接受字符串编码ID）——栏目/区块仅对导出集内令牌化，
 *       集外按原始ID导出 + 诊断（D19/D27）；值本身已是令牌字面量（D9 前导入遗留）则 error 级诊断并
 *       原样保留（D18，修复走构成健康页）；media/link 的 url 键与 html
 *       走资源分类改写；list/object 递归；voteRef 反向令牌化为 {@code {{vote:<originId>}}}
 *       （E4b / D30——来源绑定，文章同法；D11 的「不可种子化」自此全部废止）。</li>
 *   <li><b>模型 customs</b> 按 {@link SeedExportFieldRules.ModelRule} 分派（图片/视频/文件上传为
 *       整串 URL，富文本/Markdown 为内嵌改写）。</li>
 *   <li>其余一切字符串做<b>主题前缀反向</b>（{@code <templateStorageUrl>/<siteId>/<theme>} →
 *       {@code {{theme}}}）——导入侧对全部字符串无条件展开 {@code {{theme}}}，反向须对称，
 *       否则跨站点安装携带本站前缀。</li>
 * </ul>
 * 字段类型未在 {@link SeedExportFieldRules} 声明即抛错（fail-loud，§5.1）。
 *
 * @author PONY
 */
final class SeedExportValueRewriter {

    private static final Pattern REF_ID = Pattern.compile("^[1-9][0-9]{0,18}$");

    private final SeedAssetExtractor.Session assets;
    /**
     * 站点全部栏目 ID → 别名（{{channel:}} 令牌以别名迟绑——种子/linked/操作员栏目均可解析）
     */
    private final Map<Long, String> channelAliasById;
    /**
     * 导出集内文章 ID → originId（{{article:}} 令牌须指向种子内文章）
     */
    private final Map<Long, String> articleOriginById;
    /**
     * 站点可见区块 ID → 别名（{{block:}} 令牌以别名迟绑，E4a / D24）
     */
    private final Map<Long, String> blockAliasById;
    /**
     * 导出集内投票 ID → originId（{{vote:}} 令牌须指向种子内投票——来源绑定，文章同法，E4b / D30）
     */
    private final Map<Long, String> voteOriginById;
    /**
     * 导出的栏目ID集合（D19：channelRef 仅对导出集内的栏目令牌化，集外按原始ID导出——空白站安装
     * 退化为悬挂软引用而非硬失败；{@code null} = 仅构成导出，无内容集可依，全部可解析别名照常令牌化）
     */
    @Nullable
    private final Set<Long> exportedChannelIds;
    /**
     * 导出的区块ID集合（D27：blockRef 同 D19 口径收窄令牌化；{@code null} = 仅构成导出）
     */
    @Nullable
    private final Set<Long> exportedBlockIds;
    private final List<ExportPlan.RefIssue> issues;
    private final Pattern themePrefixPattern;

    SeedExportValueRewriter(SeedAssetExtractor.Session assets, String themePrefix,
                            Map<Long, String> channelAliasById, Map<Long, String> articleOriginById,
                            Map<Long, String> blockAliasById, Map<Long, String> voteOriginById,
                            @Nullable Set<Long> exportedChannelIds, @Nullable Set<Long> exportedBlockIds,
                            List<ExportPlan.RefIssue> issues) {
        this.assets = assets;
        this.channelAliasById = channelAliasById;
        this.articleOriginById = articleOriginById;
        this.blockAliasById = blockAliasById;
        this.voteOriginById = voteOriginById;
        this.exportedChannelIds = exportedChannelIds;
        this.exportedBlockIds = exportedBlockIds;
        this.issues = issues;
        // 前缀反向须带边界（后随 / 或定界符或串尾），防止误伤同名前缀主题（mytheme vs mytheme2）
        this.themePrefixPattern = Pattern.compile(
                Pattern.quote(themePrefix) + "(?=$|/|[\\s\"'<>()\\[\\]{}\\\\])");
    }

    // ----- Shape 设置（面板 settings，§6）-----

    /**
     * 按 Shape 设置字段定义改写整块 settings；未声明的键做主题前缀反向后原样保留。
     */
    Map<String, Object> rewriteSettings(Map<String, Object> settings, Map<String, FieldDef> fieldDefs,
                                        String where) {
        Map<String, Object> out = new LinkedHashMap<>(settings.size());
        settings.forEach((key, value) -> {
            FieldDef def = fieldDefs.get(key);
            out.put(key, def != null ? rewriteSettingValue(value, def, where + "." + key)
                    : themeOnly(value));
        });
        return out;
    }

    @Nullable
    private Object rewriteSettingValue(@Nullable Object value, FieldDef def, String where) {
        if (value == null) {
            return null;
        }
        return switch (SeedExportFieldRules.shapeRule(def.getType())) {
            case NONE -> themeOnly(value);
            case HTML -> value instanceof String str ? assets.rewriteInline(str, where) : themeOnly(value);
            case MEDIA -> rewriteUrlKeys(value, where, "url");
            case LINK -> rewriteUrlKeys(value, where, "url", "mobileUrl");
            case CHANNEL_REF -> rewriteChannelRef(value, where);
            case ARTICLE_REF -> rewriteArticleRef(value, where);
            case BLOCK_REF -> rewriteBlockRef(value, where);
            case VOTE_REF -> rewriteVoteRef(value, where);
            case LIST -> rewriteList(value, def, where);
            case OBJECT -> rewriteObject(value, def, where);
        };
    }

    private Object rewriteList(Object value, FieldDef def, String where) {
        FieldDef of = def.getFieldDef("of");
        if (!(value instanceof List<?> list) || of == null) {
            return themeOnly(value);
        }
        List<Object> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            out.add(rewriteSettingValue(list.get(i), of, where + "[" + i + "]"));
        }
        return out;
    }

    private Object rewriteObject(Object value, FieldDef def, String where) {
        if (!(value instanceof Map<?, ?> map)) {
            return themeOnly(value);
        }
        Map<String, FieldDef> fields = def.getFieldDefs("fields");
        Map<String, Object> out = new LinkedHashMap<>(map.size());
        map.forEach((k, v) -> {
            String key = String.valueOf(k);
            FieldDef field = fields.get(key);
            out.put(key, field != null ? rewriteSettingValue(v, field, where + "." + key) : themeOnly(v));
        });
        return out;
    }

    private Object rewriteUrlKeys(Object value, String where, String... urlKeys) {
        if (!(value instanceof Map<?, ?> map)) {
            return themeOnly(value);
        }
        Map<String, Object> out = new LinkedHashMap<>(map.size());
        map.forEach((k, v) -> out.put(String.valueOf(k), themeOnly(v)));
        for (String urlKey : urlKeys) {
            if (out.get(urlKey) instanceof String url) {
                out.put(urlKey, assets.rewriteUrl(url, where + "." + urlKey));
            }
        }
        return out;
    }

    private Object rewriteChannelRef(Object value, String where) {
        // 两种遗留词形（D18/D20）：令牌字面量（D9 前导入；导出原样携带则导入展开可能硬失败 → error）
        // 与别名映射（令牌文法前的手写种子形；渲染迟绑可用、跨站可散装，但无法令牌化/闭包收集 → warning）
        if (isLegacyToken(value)) {
            issues.add(legacyTokenIssue(value, where));
            return value;
        }
        if (SeedTokenExpander.isAliasRefMap(value)) {
            issues.add(new ExportPlan.RefIssue("panel", String.valueOf(value),
                    "channelRef 为旧别名对象形（渲染迟绑可用，但导出无法令牌化与闭包收集）——建议先在"
                            + " 设计 → 页面构成 → 构成健康 执行「修复种子令牌」，原样保留：" + where));
            return value;
        }
        Long id = asRefId(value);
        String alias = id != null ? channelAliasById.get(id) : null;
        if (alias == null) {
            issues.add(new ExportPlan.RefIssue("panel", String.valueOf(value),
                    "channelRef 无法解析为站内栏目，原样保留：" + where));
            return value;
        }
        // D19：仅导出集内的栏目令牌化；集外（D7 linked/未选中）按原始ID导出——同站导入无损，
        // 空白/他站安装为悬挂软引用（构成健康可见），绝不因别名令牌不可解析硬失败整次安装
        if (exportedChannelIds != null && !exportedChannelIds.contains(id)) {
            issues.add(new ExportPlan.RefIssue("panel", alias,
                    "channelRef 指向导出集之外的栏目，按原始ID导出（同站导入无损；跨站安装为悬挂引用，"
                            + "放宽选择可随包）：" + where));
            return value;
        }
        return "{{channel:" + alias + "}}";
    }

    private Object rewriteArticleRef(Object value, String where) {
        if (isLegacyToken(value)) {
            issues.add(legacyTokenIssue(value, where));
            return value;
        }
        Long id = asRefId(value);
        String originId = id != null ? articleOriginById.get(id) : null;
        if (originId == null) {
            issues.add(new ExportPlan.RefIssue("panel", String.valueOf(value),
                    "articleRef 指向导出集之外的文章（放宽选择或调整卡片），原样保留：" + where));
            return value;
        }
        return "{{article:" + originId + "}}";
    }

    /**
     * blockRef 反向令牌化（E4a / D24/D27——D11 的「不可种子化」自此废止）：导出集内的区块 →
     * {@code {{block:<alias>}}}；集外按原始ID导出 + 诊断（同站导入无损，跨站为悬挂软引用）；
     * 别名映射遗留形（D20 修订）warning + 修复指引，原样保留。
     */
    private Object rewriteBlockRef(Object value, String where) {
        if (isLegacyToken(value)) {
            issues.add(legacyTokenIssue(value, where));
            return value;
        }
        if (SeedTokenExpander.isAliasRefMap(value)) {
            issues.add(new ExportPlan.RefIssue("panel", String.valueOf(value),
                    "blockRef 为旧别名对象形（渲染迟绑可用，但导出无法令牌化与闭包收集）——建议先在"
                            + " 设计 → 页面构成 → 构成健康 执行「修复种子令牌」，原样保留：" + where));
            return value;
        }
        Long id = asRefId(value);
        String alias = id != null ? blockAliasById.get(id) : null;
        if (alias == null) {
            issues.add(new ExportPlan.RefIssue("panel", String.valueOf(value),
                    "blockRef 无法解析为站点可见区块，原样保留：" + where));
            return value;
        }
        if (exportedBlockIds != null && !exportedBlockIds.contains(id)) {
            issues.add(new ExportPlan.RefIssue("panel", alias,
                    "blockRef 指向导出集之外的区块，按原始ID导出（同站导入无损；跨站安装为悬挂引用）："
                            + where));
            return value;
        }
        return "{{block:" + alias + "}}";
    }

    /**
     * voteRef 反向令牌化（E4b / D30——D11 的「不可种子化」余项自此废止）：导出集内的投票 →
     * {@code {{vote:<originId>}}}（来源绑定，文章同法）；集外或仅构成导出按原始ID导出 + 诊断
     * （同站导入无损，跨站为悬挂软引用）。投票无别名——别名映射遗留形无从存在（D20 恒不分派）。
     */
    private Object rewriteVoteRef(Object value, String where) {
        if (isLegacyToken(value)) {
            issues.add(legacyTokenIssue(value, where));
            return value;
        }
        Long id = asRefId(value);
        String originId = id != null ? voteOriginById.get(id) : null;
        if (originId == null) {
            issues.add(new ExportPlan.RefIssue("panel", String.valueOf(value),
                    "voteRef 指向导出集之外的投票，按原始ID导出（同站导入无损；跨站安装为悬挂引用）："
                            + where));
            return value;
        }
        return "{{vote:" + originId + "}}";
    }

    /**
     * 引用字段值本身是种子令牌字面量（D9 之前导入的遗留数据，D18；别名映射形另见
     * {@link SeedTokenExpander#isAliasRefMap}）：活数据的该引用已静默断链，
     * 导出无从闭包（无法按ID收集），原样携带则跨站不可解析。
     */
    private static boolean isLegacyToken(Object value) {
        return value instanceof String str && SeedTokenExpander.REF_TOKEN.matcher(str.trim()).matches();
    }

    private static ExportPlan.RefIssue legacyTokenIssue(Object value, String where) {
        return new ExportPlan.RefIssue("panel", String.valueOf(value),
                "引用字段携带未展开的种子令牌（早期导入的遗留数据；原样出包则安装展开时可能整体失败）"
                        + "——请先在 设计 → 页面构成 → 构成健康 执行「修复种子令牌」再导出，原样保留："
                        + where,
                ExportPlan.RefIssue.SEVERITY_ERROR);
    }

    // ----- 模型 customs（§5.1）-----

    /**
     * 按模型字段定义改写 customs（模型缺失时仅做主题前缀反向 + 诊断）。
     */
    Map<String, Object> rewriteCustoms(Map<String, Object> customs, @Nullable Model model, String where) {
        Map<String, Object> out = new LinkedHashMap<>(customs.size());
        customs.forEach((k, v) -> out.put(k, themeOnly(v)));
        if (model == null) {
            if (!out.isEmpty()) {
                issues.add(new ExportPlan.RefIssue("customs", where, "模型缺失，customs 未做资源改写"));
            }
            return out;
        }
        for (Model.Field field : model.getFieldList()) {
            SeedExportFieldRules.ModelRule rule = SeedExportFieldRules.modelRule(field.getType());
            String code = field.getCode();
            if (rule == SeedExportFieldRules.ModelRule.URL && out.get(code) instanceof String url) {
                out.put(code, assets.rewriteUrl(url, where + "." + code));
            } else if (rule == SeedExportFieldRules.ModelRule.HTML && out.get(code) instanceof String html) {
                out.put(code, assets.rewriteInline(html, where + "." + code));
            }
        }
        return out;
    }

    // ----- 通用 -----

    /**
     * 主题前缀反向（递归）：所有字符串中的 {@code <templateStorageUrl>/<siteId>/<theme>} → {@code {{theme}}}。
     */
    @Nullable
    @SuppressWarnings("unchecked")
    <T> T themeOnly(@Nullable T value) {
        if (value instanceof String str) {
            return (T) themePrefixPattern.matcher(str).replaceAll("{{theme}}");
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>(map.size());
            map.forEach((k, v) -> out.put(String.valueOf(k), themeOnly(v)));
            return (T) out;
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            list.forEach(item -> out.add(themeOnly(item)));
            return (T) out;
        }
        return value;
    }

    /**
     * 引用ID解析（channelRef/articleRef 存储形：Long 或字符串编码ID，§10.1）。
     */
    @Nullable
    static Long asRefId(@Nullable Object value) {
        if (value instanceof Number number && number.longValue() > 0
                && number.doubleValue() == Math.rint(number.doubleValue())) {
            return number.longValue();
        }
        if (value instanceof String str && REF_ID.matcher(str).matches()) {
            return Long.parseLong(str);
        }
        return null;
    }
}
