package com.ujcms.cms.core.composition;

import com.ujcms.cms.core.domain.Model;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 导出字段规则完备性测试（theme-seed-export §5.1，CommercialFeatureManifestTest 风格）：
 * 反向资源遍历漏掉一个可携带文件 URL 的字段类型即产出跨站断链的包——两套字段类型词汇
 * （Shape 注册表 + 模型 {@code Model.TYPE_*}）的每个成员都必须在 {@link SeedExportFieldRules}
 * 显式声明。新增字段类型而未声明时本测试失败（运行期同样 fail-loud：
 * {@code ThemeSeedExporter} 构造断言 + 遍历器逐字段查表抛错）。
 */
class SeedExportFieldRulesTest {

    @Test
    void everyRegisteredShapeFieldTypeIsDeclared() {
        // 与 FieldTypeMatrixTest 同源的核心注册表 + blockRef（component-settings §5）
        List<String> registered = new ArrayList<>(FieldTypeMatrixTest.registry().names());
        registered.add("blockRef");
        assertDoesNotThrow(() -> SeedExportFieldRules.assertShapeTypesDeclared(registered));
        // 声明表不含幽灵类型（防止拼写漂移：声明了但注册表里不存在的名字）
        assertEquals(registered.size(), SeedExportFieldRules.declaredShapeTypes().size(),
                "声明表与注册表必须一一对应：" + SeedExportFieldRules.declaredShapeTypes());
    }

    @Test
    void everyModelFieldTypeConstantIsDeclared() throws IllegalAccessException {
        // 反射枚举 Model.TYPE_* 常量（新增模型字段类型未声明 → 此处失败）
        List<String> constants = new ArrayList<>();
        for (Field field : Model.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getName().startsWith("TYPE_")
                    && field.getType() == String.class) {
                constants.add((String) field.get(null));
            }
        }
        assertTrue(constants.size() >= 17, "Model.TYPE_* 常量枚举异常: " + constants);
        for (String type : constants) {
            assertDoesNotThrow(() -> SeedExportFieldRules.modelRule(type),
                    "模型字段类型未声明 URL 承载形态: " + type);
        }
        assertEquals(constants.size(), SeedExportFieldRules.declaredModelTypes().size(),
                "声明表与 Model.TYPE_* 常量必须一一对应");
    }

    @Test
    void undeclaredTypesFailLoud() {
        assertThrows(IllegalStateException.class, () -> SeedExportFieldRules.shapeRule("hologram"));
        assertThrows(IllegalStateException.class, () -> SeedExportFieldRules.modelRule("hologram"));
        assertThrows(IllegalStateException.class,
                () -> SeedExportFieldRules.assertShapeTypesDeclared(List.of("text", "hologram")));
    }

    @Test
    void urlCarryingTypesAreClassifiedAsSuch() {
        // 抽查关键分派（改动这些分派会直接改变导出行为，钉死）
        assertEquals(SeedExportFieldRules.ShapeRule.MEDIA, SeedExportFieldRules.shapeRule("media"));
        assertEquals(SeedExportFieldRules.ShapeRule.HTML, SeedExportFieldRules.shapeRule("html"));
        assertEquals(SeedExportFieldRules.ShapeRule.CHANNEL_REF, SeedExportFieldRules.shapeRule("channelRef"));
        assertEquals(SeedExportFieldRules.ShapeRule.ARTICLE_REF, SeedExportFieldRules.shapeRule("articleRef"));
        assertEquals(SeedExportFieldRules.ModelRule.URL, SeedExportFieldRules.modelRule(Model.TYPE_IMAGE_UPLOAD));
        assertEquals(SeedExportFieldRules.ModelRule.URL, SeedExportFieldRules.modelRule(Model.TYPE_VIDEO_UPLOAD));
        assertEquals(SeedExportFieldRules.ModelRule.URL, SeedExportFieldRules.modelRule(Model.TYPE_FILE_UPLOAD));
        assertEquals(SeedExportFieldRules.ModelRule.HTML, SeedExportFieldRules.modelRule(Model.TYPE_RICH_EDITOR));
        assertEquals(SeedExportFieldRules.ModelRule.HTML,
                SeedExportFieldRules.modelRule(Model.TYPE_MARKDOWN_EDITOR));
    }
}
