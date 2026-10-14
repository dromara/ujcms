package com.ujcms.cms.core.composition;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.ujcms.cms.core.composition.PageManifest.SlotDef;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ujcms.cms.core.support.Constants.MAPPER;

/**
 * 页面清单加载器。解析 {@code *.manifest.json}、按已发布 Schema 校验、执行文件内规则
 * （区域必须显式 {@code kind: region} 且声明非空 {@code allowedShapes}；经典插槽已随
 * 插槽子系统于 component-settings Phase D 退役，缺失/其它 kind 一律拒绝）。
 *
 * @author PONY
 */
@Component
public class ManifestLoader {
    private final CompositionSchemas schemas;

    public ManifestLoader(CompositionSchemas schemas) {
        this.schemas = schemas;
    }

    /**
     * 加载一个页面清单。
     *
     * @param json       清单 JSON 文本
     * @param sourceDesc 来源描述（文件路径/资源名，用于错误信息）
     * @return 构建的页面清单
     */
    public PageManifest load(String json, String sourceDesc) {
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw new CompositionLoadException(sourceDesc, e);
        }
        List<String> schemaErrors = schemas.validate(CompositionSchemas.PAGE_MANIFEST, node);
        if (!schemaErrors.isEmpty()) {
            throw new CompositionLoadException(sourceDesc, schemaErrors);
        }
        Map<String, Object> raw = MAPPER.convertValue(node, new TypeReference<>() {
        });
        raw.remove("$schema");

        String name = (String) raw.get("name");
        List<String> templates = toTemplates(raw.get("template"));
        List<String> errors = new ArrayList<>();
        Map<String, SlotDef> slots = new LinkedHashMap<>();
        if (raw.get("slots") instanceof Map<?, ?> slotMap) {
            slotMap.forEach((k, v) -> {
                if (k instanceof String slotName && v instanceof Map<?, ?> defMap) {
                    slots.put(slotName, toSlotDef(slotName, defMap, errors));
                }
            });
        }
        if (!errors.isEmpty()) {
            throw new CompositionLoadException(sourceDesc, errors);
        }
        return new PageManifest(name, ShapeLoader.toStringMap(raw.get("label")), templates,
                raw.get("route") instanceof String route ? route : null,
                raw.get("order") instanceof Number order ? order.intValue() : null, slots,
                ThemeSettingsSchema.parse(raw.get("settings")), raw);
    }

    private static List<String> toTemplates(Object value) {
        if (value instanceof String str) {
            return List.of(str);
        }
        return ShapeLoader.toStringList(value);
    }

    private static SlotDef toSlotDef(String slotName, Map<?, ?> defMap, List<String> errors) {
        Map<String, Object> def = new LinkedHashMap<>(defMap.size());
        defMap.forEach((k, v) -> {
            if (k instanceof String key) {
                def.put(key, v);
            }
        });
        if (!(def.get("kind") instanceof String kind) || !PageManifest.KIND_REGION.equals(kind)) {
            // 经典插槽（缺失 kind / 其它 kind）已于 component-settings Phase D 退役
            errors.add(slotName + ": 须声明 kind: region（经典插槽已退役）");
            return new SlotDef(false, List.of(), RowLayoutCatalog.keys(), null, null);
        }
        if (def.get("shared") instanceof String owner) {
            // 共享区域引用（page-regions §15）：定义在宿主页面清单，注册表快照构建时按同主题解引用；
            // Schema 保证引用形态只含 kind + shared
            return SlotDef.reference(owner);
        }
        Integer maxRows = null;
        Integer maxPanelsPerColumn = null;
        if (def.get("constraints") instanceof Map<?, ?> constraints) {
            if (constraints.get("maxRows") instanceof Number n) {
                maxRows = n.intValue();
            }
            if (constraints.get("maxPanelsPerColumn") instanceof Number n) {
                maxPanelsPerColumn = n.intValue();
            }
        }
        List<String> allowedShapes = ShapeLoader.toStringList(def.get("allowedShapes"));
        if (allowedShapes.isEmpty()) {
            errors.add(slotName + ": region 须声明非空 allowedShapes");
        }
        List<String> allowedRowLayouts = ShapeLoader.toStringList(def.get("allowedRowLayouts"));
        if (allowedRowLayouts.isEmpty()) {
            allowedRowLayouts = RowLayoutCatalog.keys();
        }
        return new SlotDef(Boolean.TRUE.equals(def.get("required")),
                allowedShapes, allowedRowLayouts, maxRows, maxPanelsPerColumn,
                ShapeLoader.toStringMap(def.get("label")),
                def.get("description") instanceof String description && !description.isBlank() ? description : null,
                null);
    }
}
