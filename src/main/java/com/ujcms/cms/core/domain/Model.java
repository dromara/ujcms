package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ujcms.cms.core.domain.base.ModelBase;
import com.ujcms.cms.core.domain.support.CustomBean;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.util.function.Consumer3;
import com.ujcms.util.web.HtmlParserUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.owasp.html.PolicyFactory;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ujcms.cms.core.domain.support.EntityConstants.SCOPE_GLOBAL;
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

    public Map<String, Object> assembleCustoms(List<? extends CustomBean> customList) {
        Map<String, Model.Field> fieldMap = getFieldMap();
        Map<String, Object> customs = new HashMap<>(16);
        fieldMap.forEach((name, field) -> {
            String type = field.getType();
            List<CustomBean> list = customList.stream().filter(
                    item -> Objects.equals(item.getName(), name) && compatibleType(item.getType(), type))
                    .collect(Collectors.toList());
            List<CustomBean> keyList = customList.stream().filter(
                    item -> Objects.equals(item.getName(), name + KEY_SUFFIX) && compatibleType(item.getType(), type))
                    .collect(Collectors.toList());
            String value = list.isEmpty() ? null : list.iterator().next().getValue();
            String valueKey = keyList.isEmpty() ? null : keyList.iterator().next().getValue();
            switch (field.getType()) {
                case Model.TYPE_NUMBER:
                case Model.TYPE_SLIDER:
                    if (NumberUtils.isCreatable(value)) {
                        if (field.getPrecision() != null && field.getPrecision() > 0) {
                            customs.put(name, new BigDecimal(value));
                        } else {
                            customs.put(name, Long.valueOf(value));
                        }
                    }
                    break;
                case Model.TYPE_DATE:
                    if (value != null) {
                        customs.put(name, ISO_OFFSET_DATE_TIME.parse(value, OffsetDateTime::from));
                    }
                    break;
                case Model.TYPE_SWITCH:
                    if (value != null) {
                        customs.put(name, Boolean.valueOf(value));
                    }
                    break;
                case Model.TYPE_CHECKBOX:
                case Model.TYPE_MULTIPLE_SELECT:
                    customs.put(name, list.stream().map(CustomBean::getValue).filter(Objects::nonNull)
                            .collect(Collectors.toList()));
                    customs.put(name + KEY_SUFFIX, keyList.stream().map(CustomBean::getValue).filter(Objects::nonNull)
                            .collect(Collectors.toList()));
                    break;
                default:
                    if (value != null) {
                        customs.put(name, value);
                    }
                    if (valueKey != null) {
                        customs.put(name + KEY_SUFFIX, valueKey);
                    }
            }
        });
        return customs;
    }

    public void disassembleCustoms(Map<String, Object> customs, Consumer3<String, String, String> consumer) {
        customs.forEach((key, value) -> {
            String origKey = key.endsWith(KEY_SUFFIX) ? key.substring(0, key.lastIndexOf(KEY_SUFFIX)) : key;
            String type = Optional.ofNullable(getFieldMap().get(origKey)).map(Field::getType).orElse(null);
            if (type == null) {
                return;
            }
            if (value instanceof ArrayList) {
                ((ArrayList<?>) value).forEach(val -> consumer.accept(key, type, String.valueOf(val)));
                return;
            }
            Optional.ofNullable(value).map(String::valueOf).filter(StringUtils::isNotBlank)
                    .ifPresent(it -> consumer.accept(key, type, it));
        });
    }

    public void sanitizeCustoms(Map<String, Object> customs, PolicyFactory policyFactory) {
        Set<String> toBeRemoved = new HashSet<>();
        customs.forEach((key, value) -> {
            String origKey = key.endsWith(KEY_SUFFIX) ? key.substring(0, key.lastIndexOf(KEY_SUFFIX)) : key;
            String type = Optional.ofNullable(getFieldMap().get(origKey)).map(Field::getType).orElse(null);
            if (type == null) {
                toBeRemoved.add(key);
                return;
            }
            if (TYPE_RICH_EDITOR.equals(type)) {
                customs.put(type, policyFactory.sanitize(String.valueOf(value)));
            }
        });
        toBeRemoved.forEach(customs::remove);
    }


    public void handleCustoms(List<? extends CustomBean> customList, CustomHandle handle) {
        Map<String, Model.Field> fieldMap = getFieldMap();
        customList.forEach(custom -> {
            String value = custom.getValue();
            if (value == null) {
                return;
            }
            String name = custom.getName();
            Model.Field field = fieldMap.get(name);
            if (field == null) {
                return;
            }
            handle.execute(name, value, field);
        });
    }

    @SuppressWarnings("unchecked")
    public void handleCustoms(Map<String, Object> customs, CustomHandle handle) {
        Map<String, Model.Field> fieldMap = getFieldMap();
        customs.forEach((name, obj) -> {
            Model.Field field = fieldMap.get(name);
            if (field == null) {
                return;
            }
            if (obj instanceof List) {
                List<String> list = (List<String>) obj;
                list.forEach(value -> {
                    if (StringUtils.isBlank(value)) {
                        return;
                    }
                    handle.execute(name, value, field);
                });
            } else {
                if (obj != null) {
                    handle.execute(name, String.valueOf(obj), field);
                }
            }
        });
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
            throw new RuntimeException(e);
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

    public static class GetUrlsHandle implements CustomHandle {
        private final List<String> urls;

        public GetUrlsHandle(List<String> urls) {
            this.urls = urls;
        }

        @Override
        public void execute(String name, String value, Field field) {
            switch (field.getType()) {
                case Model.TYPE_RICH_EDITOR:
                    urls.addAll(HtmlParserUtils.getUrls(value));
                    break;
                case Model.TYPE_IMAGE_UPLOAD:
                case Model.TYPE_VIDEO_UPLOAD:
                case Model.TYPE_FILE_UPLOAD:
                    urls.add(value);
                    break;
                default:
            }
        }
    }

    public static final class Field {
        private String code = "";
        private String name = "";
        private String type = "";
        @Nullable
        private Integer precision;
        @Nullable
        private Integer dictTypeId;

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
        public Integer getDictTypeId() {
            return dictTypeId;
        }

        public void setDictTypeId(@Nullable Integer dictTypeId) {
            this.dictTypeId = dictTypeId;
        }
    }

    public static final String KEY_SUFFIX = "_key";

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

    /**
     * 每一个数组代表一组兼容的数据类型。数值和字符串都作为兼容数据。
     */
    private static final String[][] TYPE_GROUPS = {
            {TYPE_TEXT, TYPE_TEXTAREA, TYPE_RADIO, TYPE_CHECKBOX, TYPE_SELECT, TYPE_MULTIPLE_SELECT,
                    TYPE_NUMBER, TYPE_SLIDER}};

    private boolean compatibleType(String type1, String type2) {
        if (Objects.equals(type1, type2)) {
            return true;
        }
        for (String[] group : TYPE_GROUPS) {
            if (ArrayUtils.contains(group, type1) && ArrayUtils.contains(group, type2)) {
                return true;
            }
        }
        return false;
    }

}