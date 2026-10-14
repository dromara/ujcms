package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.composition.fields.FieldDef;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shape 变更兼容性分级器（§13.1）。对比同名 Shape 的旧版与新版，判定变更等级
 * （additive / rename / breaking-required / breaking-removal）。在快照替换路径（{@link ManifestService}）
 * 中调用：无迁移块描述的 {@link ShapeCompatibility#BREAKING_REMOVAL 破坏性移除} 会使新快照被拒绝、
 * 旧快照继续服务（§13.4 / §19.3）；{@link ShapeCompatibility#BREAKING_REQUIRED 破坏性必填} 不拒绝，
 * 受影响卡片由健康页标记（§11.5）。
 *
 * <p>「被迁移块覆盖」的判定取新 Shape 的 {@code migrations}：被改名（{@code rename} 的源字段）、
 * 被声明移除（{@code remove}）、或被变换（{@code transform}）的旧字段不计入未覆盖移除。
 *
 * @author PONY
 */
@Component
public class ShapeCompatibilityClassifier {
    /**
     * 对比旧 Shape 与新 Shape，给出兼容性分级结果。
     *
     * @param oldShape 当前已加载的同名 Shape
     * @param newShape 候选快照中的同名 Shape
     * @return 分级结果
     */
    public Result classify(Shape oldShape, Shape newShape) {
        Map<String, FieldDef> oldFields = oldShape.getFields();
        Map<String, FieldDef> newFields = newShape.getFields();
        List<MigrationBlock> blocks = MigrationBlock.parse(newShape.getMigrations());
        Semver oldVersion = Semver.parse(oldShape.getVersion());

        Set<String> renameSources = new LinkedHashSet<>();
        Set<String> removeDeclared = new LinkedHashSet<>();
        Set<String> transformDeclared = new LinkedHashSet<>();
        for (MigrationBlock block : blocks) {
            // 仅计入 from 范围含旧版本的迁移块（§21 Q12(k)）：投影按 from.contains(stored) 匹配
            // （ShapeMigrationService.project），不含旧版本的块对旧卡片根本不会套用，
            // 不能作为移除/改类型的覆盖证据——否则 from:"1.x" 的旧迁移会让 2.x→3.0 的移除误判为已覆盖
            if (!block.from().contains(oldVersion)) {
                continue;
            }
            renameSources.addAll(block.rename().keySet());
            removeDeclared.addAll(block.remove());
            transformDeclared.addAll(block.transform().keySet());
        }

        List<String> renamed = new ArrayList<>();
        List<String> uncoveredRemovals = new ArrayList<>();
        // 旧有而新无的字段：被改名→兼容改名；被声明移除→已覆盖；否则→未覆盖破坏性移除
        for (String name : oldFields.keySet()) {
            if (newFields.containsKey(name)) {
                continue;
            }
            if (renameSources.contains(name)) {
                renamed.add(name);
            } else if (!removeDeclared.contains(name)) {
                uncoveredRemovals.add(name);
            }
        }
        // 两版都有但类型变了：被 transform 覆盖则可读，否则按破坏性移除（旧数据类型不符）
        List<String> retyped = new ArrayList<>();
        for (Map.Entry<String, FieldDef> entry : newFields.entrySet()) {
            FieldDef oldDef = oldFields.get(entry.getKey());
            if (oldDef != null && !oldDef.getType().equals(entry.getValue().getType())) {
                retyped.add(entry.getKey());
                if (!transformDeclared.contains(entry.getKey())) {
                    uncoveredRemovals.add(entry.getKey());
                }
            }
        }
        // 新增必填（旧版无该字段或非必填）：可读但需在健康页标记
        List<String> newRequired = new ArrayList<>();
        for (Map.Entry<String, FieldDef> entry : newFields.entrySet()) {
            if (!entry.getValue().isRequired()) {
                continue;
            }
            FieldDef oldDef = oldFields.get(entry.getKey());
            if (oldDef == null || !oldDef.isRequired()) {
                newRequired.add(entry.getKey());
            }
        }

        ShapeCompatibility level;
        if (!uncoveredRemovals.isEmpty()) {
            level = ShapeCompatibility.BREAKING_REMOVAL;
        } else if (!newRequired.isEmpty()) {
            level = ShapeCompatibility.BREAKING_REQUIRED;
        } else if (!renamed.isEmpty() || !removeDeclared.isEmpty() || !retyped.isEmpty()) {
            level = ShapeCompatibility.COMPATIBLE_RENAME;
        } else {
            level = ShapeCompatibility.COMPATIBLE_ADDITIVE;
        }
        return new Result(level, renamed, uncoveredRemovals, newRequired);
    }

    /**
     * 分级结果。
     *
     * @param level             兼容性等级
     * @param renamedFields     带别名改名的旧字段
     * @param uncoveredRemovals 未被迁移块覆盖的移除/改类型字段（非空即拒绝重载）
     * @param newRequiredFields 新增的必填字段（受影响卡片在健康页标记）
     */
    public record Result(ShapeCompatibility level, List<String> renamedFields, List<String> uncoveredRemovals,
                         List<String> newRequiredFields) {
        public Result {
            renamedFields = List.copyOf(renamedFields);
            uncoveredRemovals = List.copyOf(uncoveredRemovals);
            newRequiredFields = List.copyOf(newRequiredFields);
        }
    }
}
