package com.ujcms.cms.core.domain;

import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_GLOBAL;
import static com.ujcms.cms.core.support.Constants.MAPPER;
import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import com.ujcms.cms.core.security.HtmlSanitizeService;
import com.ujcms.cms.core.security.PolicyTier;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ujcms.cms.core.domain.generated.GeneratedModel;
import com.ujcms.cms.core.support.Constants;
import io.swagger.v3.oas.annotations.media.Schema;
import com.ujcms.common.web.HtmlParserUtils;
import com.ujcms.common.web.Views;

/**
 * 模型实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Model extends GeneratedModel {

    /**
     * 是否全局共享
     */
    @JsonIgnore
    public boolean isGlobal() {
        return getScope() == SCOPE_GLOBAL;
    }

    private static void assembleNumber(Map<String, Object> assembled, @Nullable Integer precision,
                                       String name, Object value) {
        if (precision != null && precision > 0) {
            if (value instanceof BigDecimal) {
                assembled.put(name, value);
            } else if (value instanceof Number || value instanceof String) {
                assembled.put(name, new BigDecimal(value.toString()));
            }
        } else {
            if (value instanceof Long) {
                assembled.put(name, value);
            } else if (value instanceof Number || value instanceof String) {
                assembled.put(name, Long.valueOf(value.toString()));
            }
        }
    }

    private static void assembleList(Map<String, Object> assembled, String dataType,
                                     String name, Object value, String nameKey, Object valueKey) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            assembled.put(name, list.stream().filter(Objects::nonNull).map(Object::toString)
                    .toList());
        }
        if (valueKey instanceof List) {
            List<?> listKey = (List<?>) valueKey;
            assembled.put(nameKey, listKey.stream().filter(Objects::nonNull).map(val -> {
                if (DATA_TYPE_NUMBER.equals(dataType)) {
                    return Long.valueOf(val.toString());
                } else {
                    return val.toString();
                }
            }).toList());
        }
    }

    private static void assembleSelect(Map<String, Object> assembled, String dataType,
                                       String name, Object value, String nameKey, Object valueKey) {
        if (value instanceof String string) {
            assembled.put(name, string);
        }
        if (valueKey instanceof String || valueKey instanceof Number) {
            if (DATA_TYPE_NUMBER.equals(dataType)) {
                assembled.put(nameKey, Long.valueOf(valueKey.toString()));
            } else {
                assembled.put(nameKey, valueKey.toString());
            }
        }
    }

    private static void assembleDate(Map<String, Object> assembled, String name, Object value) {
        if (value instanceof OffsetDateTime) {
            assembled.put(name, value);
        } else if (value instanceof String string) {
            assembled.put(name, ISO_OFFSET_DATE_TIME.parse(string, OffsetDateTime::from));
        } else if (value instanceof Number number) {
            assembled.put(name, OffsetDateTime.ofInstant(
                    Instant.ofEpochSecond(number.longValue()), ZoneId.systemDefault()));
        }
    }

    private static void assembleBoolean(Map<String, Object> assembled, String name, Object value) {
        if (value instanceof Boolean bool) {
            assembled.put(name, bool);
        } else if (value instanceof String string) {
            assembled.put(name, Boolean.valueOf(string));
        } else if (value instanceof Number number) {
            assembled.put(name, Boolean.valueOf(number.longValue() != 0));
        }
    }

    public Map<String, Object> assembleMap(Map<String, Object> map) {
        Map<String, Model.Field> fieldMap = getFieldMap();
        Map<String, Object> assembled = new HashMap<>(16);
        for (Map.Entry<String, Model.Field> entry : fieldMap.entrySet()) {
            String name = entry.getKey();
            String nameKey = name + KEY_SUFFIX;
            Model.Field field = entry.getValue();
            String type = field.getType();
            String dataType = field.getDataType();
            Object value = map.get(name);
            Object valueKey = map.get(nameKey);
            switch (type) {
                case Model.TYPE_NUMBER,Model.TYPE_SLIDER:
                    assembleNumber(assembled, field.getPrecision(), name, value);
                    break;
                case Model.TYPE_DATE:
                    assembleDate(assembled, name, value);
                    break;
                case Model.TYPE_SWITCH:
                    assembleBoolean(assembled, name, value);
                    break;
                case Model.TYPE_CHECKBOX,Model.TYPE_MULTIPLE_SELECT:
                    assembleList(assembled, dataType, name, value, nameKey, valueKey);
                    break;
                case Model.TYPE_RADIO,Model.TYPE_SELECT:
                    assembleSelect(assembled, dataType, name, value, nameKey, valueKey);
                    break;
                default:
                    // 剩余数据都是必须是字符串
                    if (value != null) {
                        assembled.put(name, value.toString());
                    }
            }
        }
        return assembled;
    }

    public Map<String, Object> assembleMap(@Nullable String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyMap();
        }
        try {
            return assembleMap(MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            }));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public Map<String, Object> sanitizeMap(Map<String, Object> map, HtmlSanitizeService sanitizeService) {
        Map<String, Object> assembledMap = assembleMap(map);
        for (Field field : getFieldList()) {
            if (TYPE_RICH_EDITOR.equals(field.getType())) {
                String code = field.getCode();
                Object obj = assembledMap.get(code);
                if (obj instanceof String string) {
                    // 按字段配置的策略层级清理；未配置时回退到 RICH（保持旧的宽松默认）。
                    assembledMap.put(code, sanitizeService.sanitize(string, PolicyTier.fromCode(field.getPolicy())));
                }
            }
        }
        return assembledMap;
    }

    public List<String> getUrlsFromMap(Map<String, Object> map) {
        List<String> list = new ArrayList<>();
        for (Field field : getFieldList()) {
            Object obj = map.get(field.getCode());
            if (obj instanceof String string) {
                switch (field.getType()) {
                    case Model.TYPE_RICH_EDITOR,Model.TYPE_MARKDOWN_EDITOR:
                        list.addAll(HtmlParserUtils.getUrls(string));
                        break;
                    case Model.TYPE_IMAGE_UPLOAD,Model.TYPE_VIDEO_UPLOAD,Model.TYPE_AUDIO_UPLOAD,Model.TYPE_FILE_UPLOAD:
                        list.add(string);
                        break;
                    default:
                }
            }
        }
        return list;
    }

    /**
     * 把自定义字段值拆回两个 JSON 列。桶为空时也写 {@code null}：清掉最后一个值（或把字段改为大字段）后，
     * 旧列值不能残留——否则下次读取时它又回来了
     */
    public void disassembleMap(Map<String, Object> map, Consumer<@Nullable String> mainsConsumer,
                              Consumer<@Nullable String> clobsConsumer) {
        Map<String, Object> mainsMap = new HashMap<>(map.size());
        Map<String, Object> clobsMap = new HashMap<>(map.size());
        for (Field field : getFieldList()) {
            String code = field.getCode();
            String codeKey = code + KEY_SUFFIX;
            Object value = map.get(code);
            Object valueKey = map.get(codeKey);
            if (value == null) {
                continue;
            }
            if (field.isClob()) {
                clobsMap.put(code, value);
                if (valueKey != null) {
                    clobsMap.put(codeKey, valueKey);
                }
            } else {
                mainsMap.put(code, value);
                if (valueKey != null) {
                    mainsMap.put(codeKey, valueKey);
                }
            }
        }
        try {
            mainsConsumer.accept(mainsMap.isEmpty() ? null : MAPPER.writeValueAsString(mainsMap));
            clobsConsumer.accept(clobsMap.isEmpty() ? null : MAPPER.writeValueAsString(clobsMap));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 只读：由 customs 解析而来，没有 setter。不标只读的话，请求体里带着它（栏目详情原样回传时，其栏目模型、文章模型下就有）
     * Jackson 会把 getter 当 setter，往这里返回的不可变列表里添加元素，整个请求以 JSON 解析错误告终
     */
    @Schema(description = "字段定义列表（从 customs JSON 解析）")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public List<Field> getFieldList() {
        String customs = getCustoms();
        if (StringUtils.isBlank(customs)) {
            return Collections.emptyList();
        }
        try {
            return Constants.MAPPER.readValue(customs, new TypeReference<List<Model.Field>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @JsonIgnore
    public Map<String, Field> getFieldMap() {
        return getFieldList().stream().collect(Collectors.toMap(Field::getCode, field -> field));
    }

    public interface CustomHandle {
        /**
         * 处理自定义字段
         *
         * @param name  字段名称
         * @param value 字段值
         * @param field 字段属性
         */
        void execute(String name, String value, Model.Field field);
    }

    public static final class Field {
        private String code = "";
        private String name = "";
        private String type = "";
        private String dataType = DATA_TYPE_STRING;
        private String luceneField = "";
        private boolean clob = false;
        private boolean showInList = false;
        @Nullable
        private Integer precision;
        @Nullable
        private Long dictTypeId;
        /** HTML 清理策略层级（见 {@link PolicyTier}，不区分大小写）。仅对富文本字段有效，为空时默认 RICH。 */
        @Nullable
        private String policy;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        @Nullable
        public Integer getPrecision() {
            return precision;
        }

        public void setPrecision(@Nullable Integer precision) {
            this.precision = precision;
        }

        @Nullable
        public Long getDictTypeId() {
            return dictTypeId;
        }

        public void setDictTypeId(@Nullable Long dictTypeId) {
            this.dictTypeId = dictTypeId;
        }

        public String getDataType() {
            return dataType;
        }

        public void setDataType(String dataType) {
            this.dataType = dataType;
        }

        /**
         * Lucene 索引字段名（内部检索实现细节）。仅在详情/后台视图暴露，匿名公开的
         * {@code /api/model} 列表与详情（{@code @JsonView(Views.List)}）不返回。
         */
        @JsonView(Views.Whole.class)
        public String getLuceneField() {
            return luceneField;
        }

        public void setLuceneField(String luceneField) {
            this.luceneField = luceneField;
        }

        public boolean isClob() {
            return clob;
        }

        public void setClob(boolean clob) {
            this.clob = clob;
        }

        public boolean isShowInList() {
            return showInList;
        }

        public void setShowInList(boolean showInList) {
            this.showInList = showInList;
        }

        @Nullable
        public String getPolicy() {
            return policy;
        }

        public void setPolicy(@Nullable String policy) {
            this.policy = policy;
        }
    }

    public static final String KEY_SUFFIX = "Key";

    public static final String TYPE_TEXT = "text";
    public static final String TYPE_TEXTAREA = "textarea";
    public static final String TYPE_NUMBER = "number";
    public static final String TYPE_DATE = "date";
    public static final String TYPE_COLOR = "color";
    public static final String TYPE_SLIDER = "slider";
    public static final String TYPE_RADIO = "radio";
    public static final String TYPE_CHECKBOX = "checkbox";
    public static final String TYPE_SELECT = "select";
    public static final String TYPE_MULTIPLE_SELECT = "multipleSelect";
    public static final String TYPE_CASCADER = "cascader";
    public static final String TYPE_SWITCH = "switch";
    public static final String TYPE_IMAGE_UPLOAD = "imageUpload";
    public static final String TYPE_VIDEO_UPLOAD = "videoUpload";
    public static final String TYPE_AUDIO_UPLOAD = "audioUpload";
    public static final String TYPE_FILE_UPLOAD = "fileUpload";
    // 语义命名，与具体编辑器实现解耦（不要用 "tinyEditor" 等实现名）。值必须与控制台自定义字段类型字符串
    // 一致，否则 sanitizeMap / getUrlsFromMap 匹配不到任何字段，富文本自定义字段将既不被过滤也不参与附件引用计数。
    public static final String TYPE_RICH_EDITOR = "richEditor";
    public static final String TYPE_MARKDOWN_EDITOR = "markdownEditor";

    public static final String DATA_TYPE_STRING = "string";
    public static final String DATA_TYPE_NUMBER = "number";
    public static final String DATA_TYPE_DATETIME = "datetime";
    public static final String DATA_TYPE_BOOLEAN = "boolean";

    /**
     * 自定义字段类型全集（控制台字段面板所提供的；{@code cascader}、{@code markdownEditor} 为历史/预留类型，
     * 已存数据仍须可读）。服务端据此校验保存的字段定义，与种子导出 {@code SeedExportFieldRules.MODEL_RULES} 同口径
     */
    public static final Set<String> FIELD_TYPES = Set.of(TYPE_TEXT, TYPE_TEXTAREA, TYPE_NUMBER, TYPE_DATE,
            TYPE_COLOR, TYPE_SLIDER, TYPE_RADIO, TYPE_CHECKBOX, TYPE_SELECT, TYPE_MULTIPLE_SELECT, TYPE_CASCADER,
            TYPE_SWITCH, TYPE_IMAGE_UPLOAD, TYPE_VIDEO_UPLOAD, TYPE_AUDIO_UPLOAD, TYPE_FILE_UPLOAD,
            TYPE_RICH_EDITOR, TYPE_MARKDOWN_EDITOR);

    /**
     * 全局设置模型（固定 ID 1）、用户模型（固定 ID 2）：模型类型与行一一对应，服务端按 ID 取用，
     * 控制台不提供新增，删除接口拒绝
     */
    public static final String MODEL_TYPE_GLOBAL = "global";
    public static final String MODEL_TYPE_SITE = "site";
    public static final Long CONFIG_MODEL_ID = 1L;
    public static final Long USER_MODEL_ID = 2L;
    /**
     * 表单模型类型（{@code FormType.MODEL_TYPE} 位于 ext 包；core 不反向依赖，此处以字面量对齐）
     */
    private static final String FORM_MODEL_TYPE = "form";
    /**
     * 模型类型全集（article/channel/topic/blockItem/form/user/site/global）
     */
    public static final Set<String> MODEL_TYPES = Set.of(Article.MODEL_TYPE, Channel.MODEL_TYPE, Topic.MODEL_TYPE,
            Block.MODEL_TYPE, FORM_MODEL_TYPE, User.MODEL_TYPE, MODEL_TYPE_SITE, MODEL_TYPE_GLOBAL);
    /**
     * 单例模型类型：每种只有一个系统行
     */
    public static final Set<String> SINGLETON_MODEL_TYPES = Set.of(MODEL_TYPE_GLOBAL, User.MODEL_TYPE);
    public static final Set<Long> SYSTEM_MODEL_IDS = Set.of(CONFIG_MODEL_ID, USER_MODEL_ID);

    public static final String NOT_FOUND = "Model not found. ID: ";
}