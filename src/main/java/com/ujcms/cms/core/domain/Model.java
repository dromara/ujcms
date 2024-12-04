package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ujcms.cms.core.domain.base.ModelBase;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.commons.web.HtmlParserUtils;
import org.apache.commons.lang3.StringUtils;
import org.owasp.html.PolicyFactory;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_GLOBAL;
import static com.ujcms.cms.core.support.Constants.MAPPER;
import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;

/**
 * 模型实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Model extends ModelBase implements Serializable {
    private static final long serialVersionUID = 1L;

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
                    .collect(Collectors.toList()));
        }
        if (valueKey instanceof List) {
            List<?> listKey = (List<?>) valueKey;
            assembled.put(nameKey, listKey.stream().filter(Objects::nonNull).map(val -> {
                if (DATA_TYPE_NUMBER.equals(dataType)) {
                    return Long.valueOf(val.toString());
                } else {
                    return val.toString();
                }
            }).collect(Collectors.toList()));
        }
    }

    private static void assembleSelect(Map<String, Object> assembled, String dataType,
                                       String name, Object value, String nameKey, Object valueKey) {
        if (value instanceof String) {
            assembled.put(name, value);
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
        } else if (value instanceof String) {
            assembled.put(name, ISO_OFFSET_DATE_TIME.parse((String) value, OffsetDateTime::from));
        } else if (value instanceof Number) {
            assembled.put(name, OffsetDateTime.ofInstant(
                    Instant.ofEpochSecond(((Number) value).longValue()), ZoneId.systemDefault()));
        }
    }

    private static void assembleBoolean(Map<String, Object> assembled, String name, Object value) {
        if (value instanceof Boolean) {
            assembled.put(name, value);
        } else if (value instanceof String) {
            assembled.put(name, Boolean.valueOf((String) value));
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
                case Model.TYPE_NUMBER:
                case Model.TYPE_SLIDER:
                    assembleNumber(assembled, field.getPrecision(), name, value);
                    break;
                case Model.TYPE_DATE:
                    assembleDate(assembled, name, value);
                    break;
                case Model.TYPE_SWITCH:
                    assembleBoolean(assembled, name, value);
                    break;
                case Model.TYPE_CHECKBOX:
                case Model.TYPE_MULTIPLE_SELECT:
                    assembleList(assembled, dataType, name, value, nameKey, valueKey);
                    break;
                case Model.TYPE_RADIO:
                case Model.TYPE_SELECT:
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

    public Map<String, Object> sanitizeMap(Map<String, Object> map, PolicyFactory policyFactory) {
        Map<String, Object> assembledMap = assembleMap(map);
        for (Field field : getFieldList()) {
            if (TYPE_RICH_EDITOR.equals(field.getType())) {
                String code = field.getCode();
                Object obj = assembledMap.get(code);
                if (obj instanceof String) {
                    assembledMap.put(code, policyFactory.sanitize((String) obj));
                }
            }
        }
        return assembledMap;
    }

    public List<String> getUrlsFromMap(Map<String, Object> map) {
        List<String> list = new ArrayList<>();
        for (Field field : getFieldList()) {
            Object obj = map.get(field.getCode());
            if (obj instanceof String) {
                switch (field.getType()) {
                    case Model.TYPE_RICH_EDITOR:
                        list.addAll(HtmlParserUtils.getUrls((String) obj));
                        break;
                    case Model.TYPE_IMAGE_UPLOAD:
                    case Model.TYPE_VIDEO_UPLOAD:
                    case Model.TYPE_FILE_UPLOAD:
                        list.add((String) obj);
                        break;
                    default:
                }
            }
        }
        return list;
    }

    public void disassembleMap(Map<String, Object> map, Consumer<String> mainsConsumer, Consumer<String> clobsConsumer) {
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
            if (!mainsMap.isEmpty()) {
                mainsConsumer.accept(MAPPER.writeValueAsString(mainsMap));
            }
            if (!clobsMap.isEmpty()) {
                clobsConsumer.accept(MAPPER.writeValueAsString(clobsMap));
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @JsonIgnore
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
    public static final String TYPE_FILE_UPLOAD = "fileUpload";
    public static final String TYPE_RICH_EDITOR = "richEditor";
    public static final String TYPE_MARKDOWN_EDITOR = "markdownEditor";

    public static final String DATA_TYPE_STRING = "string";
    public static final String DATA_TYPE_NUMBER = "number";
    public static final String DATA_TYPE_DATETIME = "datetime";
    public static final String DATA_TYPE_BOOLEAN = "boolean";

    public static final String NOT_FOUND = "Model not found. ID: ";
}