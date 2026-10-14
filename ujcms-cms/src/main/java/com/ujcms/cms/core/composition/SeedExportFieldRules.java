package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.Model;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 导出字段规则登记表（theme-seed-export §5.1）：反向资源遍历是导出的首要正确性风险——
 * 漏掉一个可携带文件 URL 的字段类型即产出跨站安装断链的包。因此<b>每个</b>字段类型都必须在此
 * 显式声明其 URL 承载形态；遍历器遇到未声明的类型立即抛错（fail-loud），
 * {@code SeedExportFieldRulesTest} 以 CommercialFeatureManifestTest 风格钉死两套类型词汇的全覆盖：
 * <ul>
 *   <li><b>Shape 设置字段</b>（{@code composition.fields} 注册表，含商业模块贡献的类型）——
 *       {@link ThemeSeedExporter} 构造时对容器内全部注册类型断言已声明，新增字段类型未声明则启动即失败；</li>
 *   <li><b>模型自定义字段</b>（{@code Model.TYPE_*} 词汇，console 模型设计器）——遍历 customs 时逐字段查表。</li>
 * </ul>
 *
 * @author PONY
 */
public final class SeedExportFieldRules {

    private SeedExportFieldRules() {
    }

    /**
     * Shape 设置字段的 URL 承载形态
     */
    public enum ShapeRule {
        /** 不可能承载文件 URL（纯标量/颜色/日期等） */
        NONE,
        /** 富文本：URL 以内嵌形式出现（src/href），做子串级识别改写 */
        HTML,
        /** 媒体对象：{@code url} 键为整串 URL */
        MEDIA,
        /** 链接对象：{@code url}/{@code mobileUrl} 键为整串 URL */
        LINK,
        /** 栏目引用：反向令牌化为 {@code {{channel:<alias>}}}（§6） */
        CHANNEL_REF,
        /** 文章引用：反向令牌化为 {@code {{article:<originId>}}}（§6） */
        ARTICLE_REF,
        /** 区块引用：反向令牌化为 {@code {{block:<alias>}}}（E4a / D24——D11 的「不可种子化」废止） */
        BLOCK_REF,
        /** 投票引用：投票不可种子化，值原样保留 + 计划诊断（跨站不可解析） */
        VOTE_REF,
        /** 列表：递归元素定义（{@code of}） */
        LIST,
        /** 对象：递归子字段定义（{@code fields}） */
        OBJECT
    }

    /**
     * 模型自定义字段的 URL 承载形态
     */
    public enum ModelRule {
        /** 不可能承载文件 URL */
        NONE,
        /** 整串 URL 值（图片/视频/文件上传） */
        URL,
        /** 富文本/Markdown：URL 内嵌，子串级识别改写 */
        HTML
    }

    /**
     * Shape 设置字段类型 → URL 承载形态。核心 18 类 + blockRef（component-settings §5）。
     */
    private static final Map<String, ShapeRule> SHAPE_RULES = Map.ofEntries(
            Map.entry("text", ShapeRule.NONE),
            Map.entry("longText", ShapeRule.NONE),
            Map.entry("html", ShapeRule.HTML),
            Map.entry("number", ShapeRule.NONE),
            Map.entry("boolean", ShapeRule.NONE),
            Map.entry("date", ShapeRule.NONE),
            Map.entry("datetime", ShapeRule.NONE),
            Map.entry("enum", ShapeRule.NONE),
            Map.entry("media", ShapeRule.MEDIA),
            Map.entry("link", ShapeRule.LINK),
            Map.entry("badge", ShapeRule.NONE),
            Map.entry("themeColor", ShapeRule.NONE),
            Map.entry("freeColor", ShapeRule.NONE),
            Map.entry("articleRef", ShapeRule.ARTICLE_REF),
            Map.entry("channelRef", ShapeRule.CHANNEL_REF),
            Map.entry("blockRef", ShapeRule.BLOCK_REF),
            Map.entry("voteRef", ShapeRule.VOTE_REF),
            Map.entry("list", ShapeRule.LIST),
            Map.entry("object", ShapeRule.OBJECT));

    /**
     * 模型自定义字段类型 → URL 承载形态（{@code Model.TYPE_*} 全词汇；与
     * {@code Model.getUrlsFromMap} 的口径一致并补上 markdownEditor）。
     */
    private static final Map<String, ModelRule> MODEL_RULES = Map.ofEntries(
            Map.entry(Model.TYPE_TEXT, ModelRule.NONE),
            Map.entry(Model.TYPE_TEXTAREA, ModelRule.NONE),
            Map.entry(Model.TYPE_NUMBER, ModelRule.NONE),
            Map.entry(Model.TYPE_DATE, ModelRule.NONE),
            Map.entry(Model.TYPE_COLOR, ModelRule.NONE),
            Map.entry(Model.TYPE_SLIDER, ModelRule.NONE),
            Map.entry(Model.TYPE_RADIO, ModelRule.NONE),
            Map.entry(Model.TYPE_CHECKBOX, ModelRule.NONE),
            Map.entry(Model.TYPE_SELECT, ModelRule.NONE),
            Map.entry(Model.TYPE_MULTIPLE_SELECT, ModelRule.NONE),
            Map.entry(Model.TYPE_CASCADER, ModelRule.NONE),
            Map.entry(Model.TYPE_SWITCH, ModelRule.NONE),
            Map.entry(Model.TYPE_IMAGE_UPLOAD, ModelRule.URL),
            Map.entry(Model.TYPE_VIDEO_UPLOAD, ModelRule.URL),
            Map.entry(Model.TYPE_AUDIO_UPLOAD, ModelRule.URL),
            Map.entry(Model.TYPE_FILE_UPLOAD, ModelRule.URL),
            Map.entry(Model.TYPE_RICH_EDITOR, ModelRule.HTML),
            Map.entry(Model.TYPE_MARKDOWN_EDITOR, ModelRule.HTML));

    /**
     * Shape 字段类型的 URL 承载形态；未声明即抛错（fail-loud：漏声明 = 潜在断链）。
     */
    public static ShapeRule shapeRule(String type) {
        ShapeRule rule = SHAPE_RULES.get(type);
        if (rule == null) {
            throw new IllegalStateException("Shape field type not declared in SeedExportFieldRules: " + type
                    + " — declare whether it can carry a file URL before exporting");
        }
        return rule;
    }

    /**
     * Shape 字段类型的 URL 承载形态——宽容形（设置快照器用，§7.4）：清单作者手写的字段类型是
     * <b>数据</b>而非注册表条目（注册表完备性由 {@link #assertShapeTypesDeclared} 在启动期钉死），
     * 未知类型按 {@link ShapeRule#NONE} 处理、不阻断快照。
     */
    public static ShapeRule shapeRuleLenient(String type) {
        return SHAPE_RULES.getOrDefault(type, ShapeRule.NONE);
    }

    /**
     * 模型字段类型的 URL 承载形态；未声明即抛错（同上）。
     */
    public static ModelRule modelRule(String type) {
        ModelRule rule = MODEL_RULES.get(type);
        if (rule == null) {
            throw new IllegalStateException("Model field type not declared in SeedExportFieldRules: " + type
                    + " — declare whether it can carry a file URL before exporting");
        }
        return rule;
    }

    /**
     * 断言注册表内全部 Shape 字段类型均已声明（导出引擎构造时调用——商业模块新贡献字段类型
     * 而未在此声明时，应用启动即失败，绝不等到导出产出断链包）。
     *
     * @return 未声明的类型名（仅测试用；正常路径抛错）
     */
    public static Set<String> assertShapeTypesDeclared(Collection<String> registeredTypes) {
        Set<String> missing = new TreeSet<>();
        for (String type : registeredTypes) {
            if (!SHAPE_RULES.containsKey(type)) {
                missing.add(type);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Shape field types not declared in SeedExportFieldRules: " + missing);
        }
        return missing;
    }

    /**
     * 已声明的 Shape 字段类型名（测试对照用）
     */
    public static Set<String> declaredShapeTypes() {
        return SHAPE_RULES.keySet();
    }

    /**
     * 已声明的模型字段类型名（测试对照用）
     */
    public static Set<String> declaredModelTypes() {
        return MODEL_RULES.keySet();
    }
}
