package com.ujcms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ujcms.core.domain.base.ModelBase;
import com.ujcms.core.domain.support.CustomBean;
import com.ujcms.core.support.Constants;
import com.ofwise.util.web.HtmlUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;

/**
 * 模型 实体类
 *
 * @author PONY
 */
public class Model extends ModelBase implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(Model.class);

    public Map<String, Object> assembleCustoms(List<? extends CustomBean> customList) {
        Map<String, Model.Field> fieldMap = getFieldMap();
        Map<String, Object> customs = new HashMap<>(16);
        fieldMap.forEach((name, field) -> {
            List<CustomBean> list = customList.stream().filter(item -> item.getName().equals(name)).collect(Collectors.toList());
            String value = list.isEmpty() ? null : list.iterator().next().getValue();
            switch (field.getType()) {
                case Model.TYPE_NUMBER:
                case Model.TYPE_SLIDER:
                    if (value != null) {
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
                    customs.put(name, list.stream().map(CustomBean::getValue).filter(Objects::nonNull).collect(Collectors.toList()));
                    break;
                default:
                    if (value != null) {
                        customs.put(name, value);
                    }
            }
        });
        return customs;
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
            logger.error("Model customs json read error.", e);
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
        private List<String> urls;

        public GetUrlsHandle(List<String> urls) {
            this.urls = urls;
        }

        @Override
        public void execute(String name, String value, Field field) {
            switch (field.getType()) {
                case Model.TYPE_TINY_EDITOR:
                    urls.addAll(HtmlUtils.getUrls(value));
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
    public static final String TYPE_TINY_EDITOR = "tinyEditor";
}