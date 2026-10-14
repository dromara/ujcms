package com.ujcms.cms.core.domain.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.ujcms.cms.core.domain.Model;
import com.ujcms.common.web.exception.LogicException;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.ujcms.cms.core.support.Constants.MAPPER;

/**
 * 模型字段定义的服务端规则（docs/spec/model-management/plan.md M3）。
 *
 * <p>字段定义以 JSON 存于模型行；一次写坏（重复代码、非法 JSON）会让该模型下的所有内容读取抛错，
 * 甚至让模型列表接口本身失败（{@code Model.getFieldList} 是序列化属性）。控制台校验只覆盖当前选中的字段，
 * 这里是最后一道闸。规则与控制台一致：代码 {@code ^[a-zA-Z0-9]+$}（历史数据允许数字开头）、不超过 50 字符。
 *
 * @author PONY
 */
public final class ModelFieldRules {
    public static final Pattern CODE_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
    public static final int CODE_MAX_LENGTH = 50;
    public static final int NAME_MAX_LENGTH = 50;

    /**
     * 校验自定义字段定义（{@code customs}）：合法 JSON 数组、每个字段能按 {@link Model.Field} 解析、
     * 代码合法且唯一、名称非空、类型在 {@link Model#FIELD_TYPES} 之内，
     * 且不存在 {@code x} 与 {@code xKey} 同时为字段代码的情况（字典类字段以 {@code xKey} 存选项键）。
     *
     * @return 解析后的字段列表（空串、null 视为无字段）
     * @throws LogicException 不合规则
     */
    public static List<Model.Field> validateCustoms(@Nullable String customs) {
        if (StringUtils.isBlank(customs)) {
            return Collections.emptyList();
        }
        List<Model.Field> fields;
        try {
            JsonNode root = MAPPER.readTree(customs);
            if (!root.isArray()) {
                throw new LogicException("error.model.fieldsInvalid", "not an array");
            }
            fields = MAPPER.readValue(customs, new TypeReference<List<Model.Field>>() {
            });
        } catch (JsonProcessingException e) {
            throw new LogicException("error.model.fieldsInvalid", e.getOriginalMessage());
        }
        Set<String> codes = new HashSet<>(fields.size() * 2);
        for (Model.Field field : fields) {
            String code = field.getCode();
            if (!CODE_PATTERN.matcher(code).matches() || code.length() > CODE_MAX_LENGTH) {
                throw new LogicException("error.model.fieldsInvalid", code);
            }
            if (StringUtils.isBlank(field.getName()) || field.getName().length() > NAME_MAX_LENGTH) {
                throw new LogicException("error.model.fieldsInvalid", code);
            }
            if (!Model.FIELD_TYPES.contains(field.getType())) {
                throw new LogicException("error.model.fieldsInvalid", code + ": " + field.getType());
            }
            if (!codes.add(code)) {
                throw new LogicException("error.model.fieldCodeDuplicated", code);
            }
        }
        for (Model.Field field : fields) {
            String keyCode = field.getCode() + Model.KEY_SUFFIX;
            if (isDictBacked(field) && codes.contains(keyCode)) {
                throw new LogicException("error.model.fieldCodeDuplicated", keyCode);
            }
        }
        return fields;
    }

    /**
     * 校验系统字段集（{@code mains} / {@code asides}）：合法 JSON 数组，每项为带 {@code code} 的对象。
     * 字段集的具体内容由控制台按模型类型的目录合并，服务端不解释
     *
     * @throws LogicException 不合规则
     */
    public static void validateFieldSet(@Nullable String json) {
        if (StringUtils.isBlank(json)) {
            return;
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new LogicException("error.model.fieldsInvalid", e.getOriginalMessage());
        }
        if (!root.isArray()) {
            throw new LogicException("error.model.fieldsInvalid", "not an array");
        }
        for (JsonNode item : root) {
            if (!item.isObject() || !item.path("code").isTextual() || item.path("code").asText().isEmpty()) {
                throw new LogicException("error.model.fieldsInvalid", item.toString());
            }
        }
    }

    /**
     * 字典类字段（选项来自字典类型，值与键双写）
     */
    public static boolean isDictBacked(Model.Field field) {
        return switch (field.getType()) {
            case Model.TYPE_RADIO, Model.TYPE_CHECKBOX, Model.TYPE_SELECT, Model.TYPE_MULTIPLE_SELECT -> true;
            default -> false;
        };
    }

    private ModelFieldRules() {
    }
}
