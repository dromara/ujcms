package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.PageManifest.SlotDef;
import com.ujcms.cms.core.composition.fields.FieldDef;
import com.ujcms.cms.core.composition.fields.FieldTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 快照校验器。原子替换前对候选快照做整体校验（validate-before-swap，§9.4）：
 * Shape 引用可解析、字段类型已注册、约束自洽、每主题内模板名最多被一个清单绑定。
 * 任何错误都使替换被拒绝、旧快照继续服务。
 *
 * @author PONY
 */
@Component
public class SnapshotValidator {
    private static final Logger logger = LoggerFactory.getLogger(SnapshotValidator.class);

    /**
     * 单个 list 字段元素允许的结构类型
     */
    private static final String TYPE_LIST = "list";
    private static final String TYPE_OBJECT = "object";

    private final FieldTypeRegistry fieldTypeRegistry;

    public SnapshotValidator(FieldTypeRegistry fieldTypeRegistry) {
        this.fieldTypeRegistry = fieldTypeRegistry;
    }

    /**
     * 校验候选快照。
     *
     * @param snapshot 候选快照
     * @return 错误列表，空列表表示通过
     */
    public List<String> validate(RegistrySnapshot snapshot) {
        List<String> errors = new ArrayList<>();
        snapshot.getThemeShapes().forEach((theme, shapes) ->
                shapes.values().forEach(shape -> validateShape("theme:" + theme, shape, errors)));

        snapshot.getThemeManifests().forEach((theme, manifests) -> {
            Map<String, String> templateBindings = new HashMap<>(16);
            manifests.values().forEach(manifest -> {
                validateManifest("theme:" + theme, theme, manifest, snapshot, errors);
                for (String template : manifest.getTemplates()) {
                    String existed = templateBindings.putIfAbsent(template, manifest.getName());
                    if (existed != null) {
                        errors.add(String.format("主题 %s: 模板 %s 被多个页面清单绑定(%s, %s)",
                                theme, template, existed, manifest.getName()));
                    }
                }
            });
        });

        // 主题设置结构字段类型须已注册（§9.3：设置 schema well-formed）
        snapshot.getThemeSettings().forEach((theme, schema) ->
                schema.getGroups().forEach(group -> group.getFields().forEach((fieldName, def) ->
                        validateFieldDef(String.format("[theme:%s] 设置 %s.%s", theme, group.getName(), fieldName),
                                def, errors))));
        // 页面作用域设置结构（manifest 2.0 §4.2）：页面清单的 settings 块字段类型须已注册
        snapshot.getThemeManifests().forEach((theme, manifests) -> manifests.values().forEach(manifest ->
                validateSettingsSchema(String.format("[theme:%s] 页面 %s", theme, manifest.getName()),
                        manifest.getSettings(), errors)));
        // 变体作用域设置结构（§4.1/§4.2）：主题声明的布局变体的 settings 块字段类型须已注册。
        // 同 surface 多个启用变体绑定同一模板 → 反查有歧义，仅告警不阻断（先声明者胜出，
        // template-track-variants D2）
        snapshot.getThemeLayoutVariantsMap().forEach((theme, catalog) ->
                catalog.getBySurface().forEach((surface, items) -> {
                    Map<String, String> variantByTemplate = new HashMap<>(8);
                    items.forEach(item -> {
                        validateSettingsSchema(String.format("[theme:%s] 变体 %s:%s", theme, surface, item.getValue()),
                                item.getSettings(), errors);
                        String template = item.getTemplate();
                        if (item.isEnabled() && template != null) {
                            String existed = variantByTemplate.putIfAbsent(template, item.getValue());
                            if (existed != null) {
                                logger.warn("[theme:{}] 变体 {}:{} 与 {}:{} 绑定同一模板 {}——反查取先声明者",
                                        theme, surface, existed, surface, item.getValue(), template);
                            }
                        }
                    });
                }));
        return errors;
    }

    /**
     * 设置结构（settings 块）字段类型校验：页面 / 变体作用域与全局共用字段类型注册表（§4.2 同语法）。
     */
    private void validateSettingsSchema(String prefix, ThemeSettingsSchema schema, List<String> errors) {
        schema.getGroups().forEach(group -> group.getFields().forEach((fieldName, def) ->
                validateFieldDef(prefix + " 设置 " + group.getName() + "." + fieldName, def, errors)));
    }

    private void validateShape(String namespace, Shape shape, List<String> errors) {
        String prefix = String.format("[%s] Shape %s", namespace, shape.getName());
        shape.getFields().forEach((name, def) -> validateFieldDef(prefix + " 字段 " + name, def, errors));
        validateMigrations(prefix, shape, errors);
    }

    /**
     * 迁移块校验（§13.2）：{@code from} 范围重叠且对同一字段声明冲突动词的，是加载期校验错误，
     * 而非运行期意外——保证读取期投影的多块套用次序无歧义。
     */
    private void validateMigrations(String prefix, Shape shape, List<String> errors) {
        List<MigrationBlock> blocks = MigrationBlock.parse(shape.getMigrations());
        for (int i = 0; i < blocks.size(); i++) {
            for (int j = i + 1; j < blocks.size(); j++) {
                MigrationBlock a = blocks.get(i);
                MigrationBlock b = blocks.get(j);
                if (!a.from().overlaps(b.from())) {
                    continue;
                }
                List<String> shared = a.affectedFields().stream()
                        .filter(b.affectedFields()::contains).toList();
                if (!shared.isEmpty()) {
                    errors.add(String.format("%s: 迁移块 from=%s 与 from=%s 范围重叠且对字段 %s 声明冲突动词",
                            prefix, a.from().raw(), b.from().raw(), shared));
                }
            }
        }
    }

    private void validateManifest(String namespace, String themeName, PageManifest manifest,
                                  RegistrySnapshot snapshot, List<String> errors) {
        String prefix = String.format("[%s] 页面 %s", namespace, manifest.getName());
        manifest.getSlots().forEach((slotName, slot) ->
                validateRegionSlot(prefix + " 区域 " + slotName, themeName, slot, snapshot, errors));
    }

    /**
     * 区域校验（page-regions §4）：每个 allowedShape 须能在对应命名空间解析；
     * allowedRowLayouts 须为行布局目录的子集。共享引用（§15）须已在快照构建时解引用——
     * 未解引用即报错；已解引用的定义由宿主页面校验，不重复。
     */
    private void validateRegionSlot(String prefix, String themeName, SlotDef slot,
                                    RegistrySnapshot snapshot, List<String> errors) {
        if (slot.isUnresolvedReference()) {
            errors.add(prefix + ": shared 引用无效: " + slot.getSharedFrom()
                    + "（宿主页面不存在、同名区域未在宿主页完整声明，或宿主自身为引用/自引用）");
            return;
        }
        if (slot.isShared()) {
            return;
        }
        if (slot.getAllowedShapes().isEmpty()) {
            errors.add(prefix + ": region 须声明非空 allowedShapes");
        }
        for (String shapeName : slot.getAllowedShapes()) {
            if (snapshot.resolveShape(themeName, shapeName) == null) {
                errors.add(prefix + ": allowedShapes 中的 Shape 无法解析: " + shapeName);
            }
        }
        for (String layout : slot.getAllowedRowLayouts()) {
            if (!RowLayoutCatalog.contains(layout)) {
                errors.add(prefix + ": 未知行布局: " + layout);
            }
        }
    }

    private void validateFieldDef(String prefix, FieldDef def, List<String> errors) {
        String type = def.getType();
        if (!fieldTypeRegistry.contains(type)) {
            errors.add(prefix + ": 未知字段类型: " + type);
            return;
        }
        if (TYPE_LIST.equals(type)) {
            FieldDef of = def.getFieldDef("of");
            if (of != null) {
                validateFieldDef(prefix + ".of", of, errors);
            }
        } else if (TYPE_OBJECT.equals(type)) {
            def.getFieldDefs("fields").forEach((name, sub) ->
                    validateFieldDef(prefix + "." + name, sub, errors));
        }
    }
}
