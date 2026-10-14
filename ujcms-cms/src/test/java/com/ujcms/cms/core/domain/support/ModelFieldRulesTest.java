package com.ujcms.cms.core.domain.support;

import com.ujcms.cms.core.domain.Model;
import com.ujcms.common.web.exception.LogicException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字段定义的服务端规则（docs/spec/model-management/plan.md M3）：一次写坏会让该模型下的所有内容读取抛错，
 * 这里是最后一道闸。规则与控制台一致，历史数据允许数字开头的代码。
 */
class ModelFieldRulesTest {
    private static String code(LogicException e) {
        return e.getMessage();
    }

    @Test
    void parsesValidCustomsIntoFields() {
        List<Model.Field> fields = ModelFieldRules.validateCustoms(
                "[{\"code\":\"brand\",\"name\":\"品牌\",\"type\":\"select\",\"dictTypeId\":\"97960988613022721\",\"double\":true},"
                        + "{\"code\":\"1st\",\"name\":\"数字开头的历史代码\",\"type\":\"text\"}]");
        assertEquals(2, fields.size());
        assertEquals(97960988613022721L, fields.get(0).getDictTypeId());
        assertTrue(ModelFieldRules.validateCustoms(null).isEmpty());
        assertTrue(ModelFieldRules.validateCustoms("  ").isEmpty());
    }

    @Test
    void rejectsMalformedJsonAndNonArrays() {
        assertEquals("error.model.fieldsInvalid", code(assertThrows(LogicException.class,
                () -> ModelFieldRules.validateCustoms("not json"))));
        assertEquals("error.model.fieldsInvalid", code(assertThrows(LogicException.class,
                () -> ModelFieldRules.validateCustoms("{\"code\":\"a\"}"))));
        // 能解析为数组但字段属性类型不对（precision 为文本）：getFieldList 同样会抛，须在保存前拦下
        assertEquals("error.model.fieldsInvalid", code(assertThrows(LogicException.class,
                () -> ModelFieldRules.validateCustoms("[{\"code\":\"a\",\"name\":\"A\",\"type\":\"number\",\"precision\":\"two\"}]"))));
    }

    @Test
    void rejectsBadCodesNamesAndTypes() {
        assertEquals("error.model.fieldsInvalid", code(assertThrows(LogicException.class,
                () -> ModelFieldRules.validateCustoms("[{\"code\":\"a-b\",\"name\":\"A\",\"type\":\"text\"}]"))));
        assertEquals("error.model.fieldsInvalid", code(assertThrows(LogicException.class,
                () -> ModelFieldRules.validateCustoms("[{\"code\":\"a\",\"name\":\"\",\"type\":\"text\"}]"))));
        assertEquals("error.model.fieldsInvalid", code(assertThrows(LogicException.class,
                () -> ModelFieldRules.validateCustoms("[{\"code\":\"a\",\"name\":\"A\",\"type\":\"widget\"}]"))));
    }

    @Test
    void rejectsDuplicateAndKeySuffixCollidingCodes() {
        assertEquals("error.model.fieldCodeDuplicated", code(assertThrows(LogicException.class,
                () -> ModelFieldRules.validateCustoms("[{\"code\":\"a\",\"name\":\"A\",\"type\":\"text\"},"
                        + "{\"code\":\"a\",\"name\":\"B\",\"type\":\"textarea\"}]"))));
        // 字典类字段 x 以 xKey 存选项键：另一个字段不能叫 xKey
        assertEquals("error.model.fieldCodeDuplicated", code(assertThrows(LogicException.class,
                () -> ModelFieldRules.validateCustoms("[{\"code\":\"color\",\"name\":\"颜色\",\"type\":\"radio\"},"
                        + "{\"code\":\"colorKey\",\"name\":\"键\",\"type\":\"text\"}]"))));
        // 非字典类字段无 Key 伴生键，不冲突
        assertEquals(2, ModelFieldRules.validateCustoms("[{\"code\":\"note\",\"name\":\"备注\",\"type\":\"text\"},"
                + "{\"code\":\"noteKey\",\"name\":\"键\",\"type\":\"text\"}]").size());
    }

    @Test
    void fieldSetsMustBeArraysOfCodedObjects() {
        ModelFieldRules.validateFieldSet(null);
        ModelFieldRules.validateFieldSet("[]");
        ModelFieldRules.validateFieldSet("[{\"code\":\"title\",\"show\":true}]");
        assertEquals("error.model.fieldsInvalid", code(assertThrows(LogicException.class,
                () -> ModelFieldRules.validateFieldSet("{\"code\":\"title\"}"))));
        assertEquals("error.model.fieldsInvalid", code(assertThrows(LogicException.class,
                () -> ModelFieldRules.validateFieldSet("[{\"show\":true}]"))));
        assertEquals("error.model.fieldsInvalid", code(assertThrows(LogicException.class,
                () -> ModelFieldRules.validateFieldSet("[1]"))));
    }
}
