package com.ujcms.cms.core.composition;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ujcms.cms.core.support.Constants.MAPPER;

/**
 * 已发布的 JSON Schema（§8.4）。校验的唯一事实来源：classpath {@code composition/schema/}
 * （{@code ujcms-cms/src/main/resources/composition/schema/}，注册表与目录的完整性由
 * {@code CompositionSchemasRegistryTest} 保证），通过 {@code GET /api/composition/schemas} 对外发布；
 * 加载期校验与（未来的）Node CLI 都经由这组 Schema，不存在第二套校验规则。
 * 主题作者参考文档由 {@code scripts/generate-composition-docs.mjs} 从本组 Schema 生成。
 *
 * @author PONY
 */
@Component
public class CompositionSchemas {
    /**
     * Shape 定义 Schema
     */
    public static final String SHAPE = "shape";
    /**
     * 页面清单 Schema
     */
    public static final String PAGE_MANIFEST = "page-manifest";
    /**
     * 主题清单 Schema
     */
    public static final String THEME_MANIFEST = "theme-manifest";
    /**
     * 种子区域 Schema（page-regions §10）
     */
    public static final String SEED_REGIONS = "seed-regions";
    /**
     * 内容种子栏目 Schema（theme-marketplace §5，Phase A2）
     */
    public static final String SEED_CHANNELS = "seed-channels";
    /**
     * 内容种子文章 Schema（theme-marketplace §5，Phase A2）
     */
    public static final String SEED_ARTICLES = "seed-articles";
    /**
     * 内容种子专题 Schema（theme-marketplace §5，Phase A2）
     */
    public static final String SEED_TOPICS = "seed-topics";
    /**
     * 内容种子区块条目 Schema（theme-marketplace A6；block→card 迁移前的权宜格式）
     */
    public static final String SEED_BLOCKS = "seed-blocks";
    /**
     * 内容种子投票定义 Schema（theme-seed-export E4b / D29）
     */
    public static final String SEED_VOTES = "seed-votes";
    /**
     * 内容种子问卷定义 Schema（theme-seed-export E4d / D35）
     */
    public static final String SEED_SURVEYS = "seed-surveys";
    /**
     * 内容种子模型定义 Schema（model-seeding D1–D4）
     */
    public static final String SEED_MODELS = "seed-models";
    /**
     * 内容种子字典类型定义 Schema（model-seeding D8）
     */
    public static final String SEED_DICTS = "seed-dicts";
    /**
     * 内容种子表单类型定义 Schema（theme-seed-export E4e / D38）
     */
    public static final String SEED_FORMS = "seed-forms";
    /**
     * 内容种子友情链接定义 Schema（theme-seed-export E4f / D42）
     */
    public static final String SEED_LINK_GROUPS = "seed-link-groups";
    /**
     * 布局变体目录 Schema（§21 Q4）
     */
    public static final String LAYOUT_VARIANTS = "layout-variants";
    /**
     * 页面区域结构树 Schema（page-regions §5）
     */
    public static final String PAGE_REGION_LAYOUT = "page-region-layout";

    private static final List<String> NAMES = List.of(
            SHAPE, PAGE_MANIFEST, THEME_MANIFEST, SEED_REGIONS,
            SEED_CHANNELS, SEED_ARTICLES, SEED_TOPICS, SEED_BLOCKS, SEED_VOTES, SEED_SURVEYS,
            SEED_MODELS, SEED_DICTS, SEED_FORMS, SEED_LINK_GROUPS,
            LAYOUT_VARIANTS, PAGE_REGION_LAYOUT);
    private static final String CLASSPATH_DIR = "composition/schema/";

    private final Map<String, JsonSchema> schemas;
    private final Map<String, JsonNode> rawSchemas;

    public CompositionSchemas() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        Map<String, JsonSchema> schemaMap = new LinkedHashMap<>(NAMES.size());
        Map<String, JsonNode> rawMap = new LinkedHashMap<>(NAMES.size());
        for (String name : NAMES) {
            String path = CLASSPATH_DIR + name + ".schema.json";
            try (InputStream in = new ClassPathResource(path).getInputStream()) {
                JsonNode node = MAPPER.readTree(in);
                rawMap.put(name, node);
                schemaMap.put(name, factory.getSchema(node));
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot load composition schema: " + path, e);
            }
        }
        this.schemas = Collections.unmodifiableMap(schemaMap);
        this.rawSchemas = Collections.unmodifiableMap(rawMap);
    }

    /**
     * 按 Schema 校验 JSON 节点。
     *
     * @param schemaName Schema 名（本类常量之一）
     * @param node       待校验节点
     * @return 错误信息列表，空列表表示通过
     */
    public List<String> validate(String schemaName, JsonNode node) {
        JsonSchema schema = schemas.get(schemaName);
        if (schema == null) {
            throw new IllegalArgumentException("Unknown composition schema: " + schemaName);
        }
        return schema.validate(node).stream().map(ValidationMessage::getMessage).toList();
    }

    /**
     * 全部 Schema 原文（发布接口使用）
     */
    public Map<String, JsonNode> getRawSchemas() {
        return rawSchemas;
    }
}
