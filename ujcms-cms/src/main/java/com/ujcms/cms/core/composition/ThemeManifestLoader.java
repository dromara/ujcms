package com.ujcms.cms.core.composition;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.ujcms.cms.core.support.Constants.MAPPER;

/**
 * 主题清单加载器（§8.2）。解析 {@code theme.manifest.json}、按已发布主题清单 Schema 校验，
 * 抽取 {@code settings}（主题设置结构）与 {@code version}。仅消费构成扩展字段——
 * 安装校验仍由既有 {@code ThemeInstallValidator} 负责（§8.2 末注）。
 *
 * <p>未知字段告警而不拒绝（与既有 {@code ThemeMetadata} 读取行为一致，Schema 不设
 * {@code additionalProperties:false}）。
 *
 * @author PONY
 */
@Component
public class ThemeManifestLoader {
    private final CompositionSchemas schemas;

    public ThemeManifestLoader(CompositionSchemas schemas) {
        this.schemas = schemas;
    }

    /**
     * 加载并校验主题清单。
     *
     * @param json       清单 JSON 文本
     * @param sourceDesc 来源描述（文件路径，用于错误信息）
     * @return 解析结果
     */
    public ParsedManifest load(String json, String sourceDesc) {
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw new CompositionLoadException(sourceDesc, e);
        }
        List<String> errors = schemas.validate(CompositionSchemas.THEME_MANIFEST, node);
        if (!errors.isEmpty()) {
            throw new CompositionLoadException(sourceDesc, errors);
        }
        JsonNode settingsNode = node.get("settings");
        ThemeSettingsSchema settings = settingsNode == null || settingsNode.isNull()
                ? ThemeSettingsSchema.EMPTY
                : ThemeSettingsSchema.parse(MAPPER.convertValue(settingsNode, java.util.Map.class));
        String settingsJson = settingsNode == null ? "" : canonicalize(settingsNode, sourceDesc);
        // layoutVariants 块（manifest 2.0，§4.1）：主题声明的布局变体目录（含变体模板绑定与变体作用域设置）
        JsonNode variantsNode = node.get("layoutVariants");
        LayoutVariantCatalog layoutVariants = variantsNode == null || variantsNode.isNull()
                ? LayoutVariantCatalog.EMPTY
                : LayoutVariantCatalog.parse(MAPPER.convertValue(variantsNode, java.util.Map.class));
        String layoutVariantsJson = variantsNode == null ? "" : canonicalize(variantsNode, sourceDesc);
        // linkSlots 块（link-groups D7）：主题声明的链接组展示位词汇表
        JsonNode linkSlotsNode = node.get("linkSlots");
        LinkSlotCatalog linkSlots = linkSlotsNode == null || linkSlotsNode.isNull()
                ? LinkSlotCatalog.EMPTY
                : LinkSlotCatalog.parse(MAPPER.convertValue(linkSlotsNode, java.util.List.class));
        String linkSlotsJson = linkSlotsNode == null ? "" : canonicalize(linkSlotsNode, sourceDesc);
        JsonNode versionNode = node.get("version");
        String version = versionNode != null && versionNode.isTextual() ? versionNode.asText() : null;
        return new ParsedManifest(settings, settingsJson, layoutVariants, layoutVariantsJson,
                linkSlots, linkSlotsJson, version);
    }

    private static String canonicalize(JsonNode node, String sourceDesc) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (JacksonException e) {
            throw new CompositionLoadException(sourceDesc, e);
        }
    }

    /**
     * 主题清单解析结果
     *
     * @param settings           设置结构（无 settings → {@link ThemeSettingsSchema#EMPTY}）
     * @param settingsJson       settings 节点原文（计入快照内容散列；无则空串）
     * @param layoutVariants     主题声明的布局变体目录（manifest 2.0 §4.1；无 → {@link LayoutVariantCatalog#EMPTY}）
     * @param layoutVariantsJson layoutVariants 节点原文（计入快照内容散列；无则空串）
     * @param linkSlots          链接组展示位词汇表（link-groups D7；无 → {@link LinkSlotCatalog#EMPTY}）
     * @param linkSlotsJson      linkSlots 节点原文（计入快照内容散列；无则空串）
     * @param version            主题版本（{@code version} 字段；可为 {@code null}）
     */
    public record ParsedManifest(ThemeSettingsSchema settings, String settingsJson,
                                 LayoutVariantCatalog layoutVariants, String layoutVariantsJson,
                                 LinkSlotCatalog linkSlots, String linkSlotsJson,
                                 @Nullable String version) {
    }
}
