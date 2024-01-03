package com.ujcms.commons.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.FatalBeanException;
import org.springframework.util.Assert;

import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * 实体类工具。用于 Controller 拷贝属性。
 *
 * @author PONY
 * @see org.springframework.beans.BeanUtils#copyProperties(Object, Object)
 */
public final class Entities {
    public static void emptyToNull(Object bean) {
        Assert.notNull(bean, "Bean must not be null");
        PropertyDescriptor[] targetPds = BeanUtils.getPropertyDescriptors(bean.getClass());
        for (PropertyDescriptor targetPd : targetPds) {
            Method writeMethod = targetPd.getWriteMethod();
            Method readMethod = targetPd.getReadMethod();
            if (writeMethod != null && readMethod != null && readMethod.getReturnType().equals(String.class)) {
                try {
                    if ("".equals(readMethod.invoke(bean))) {
                        writeMethod.invoke(bean, (Object) null);
                    }
                } catch (Exception ex) {
                    throw new FatalBeanException(
                            "Could not copy property '" + targetPd.getName() + "' from source to target", ex);
                }
            }
        }
    }

    public static <T> void copy(T source, T target, String... excludes) {
        copy(source, target, Collections.emptyList(), Arrays.asList(excludes));
    }

    public static <T> void copy(T source, T target, List<String> excludes) {
        copy(source, target, Collections.emptyList(), excludes);
    }

    public static <T> void copyIncludes(T source, T target, String... includes) {
        copy(source, target, Arrays.asList(includes), Collections.emptyList());
    }

    public static <T> void copyIncludes(T source, T target, List<String> includes) {
        copy(source, target, includes, Collections.emptyList());
    }

    public static <T> void copy(T source, T target, Collection<String> includes, Collection<String> excludes) {
        Assert.notNull(source, "Source must not be null");
        Assert.notNull(target, "Target must not be null");
        PropertyDescriptor[] sourcePds = BeanUtils.getPropertyDescriptors(source.getClass());
        for (PropertyDescriptor sourcePd : sourcePds) {
            String sourcePdName = sourcePd.getName();
            PropertyDescriptor targetPd = BeanUtils.getPropertyDescriptor(target.getClass(), sourcePdName);
            if (targetPd == null) {
                continue;
            }
            Method readMethod = sourcePd.getReadMethod();
            Method writeMethod = targetPd.getWriteMethod();
            boolean isNeedCopy = writeMethod != null && readMethod != null &&
                    (includes.isEmpty() || includes.contains(sourcePdName)) && !excludes.contains(sourcePdName)
                    && BeanUtils.isSimpleValueType(readMethod.getReturnType());
            if (isNeedCopy) {
                try {
                    Object value = readMethod.invoke(source);
                    // 将空串转为 null。因为 oracle 的 varchar2 会自动把空串转为 null，在此统一各数据库行为。
                    writeMethod.invoke(target, "".equals(value) ? null : value);
                } catch (Exception ex) {
                    throw new FatalBeanException(
                            "Could not copy property '" + sourcePdName + "' from source to target", ex);
                }
            }
        }
    }

    public static <T> void copy(Map<String, Object> fields, T bean,
                                Collection<String> includes, Collection<String> excludes) {
        Map<String, Object> validFields = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            // 只拷贝基础类型，List 和 Map 里都可以进一步包含对象，可以修改 Entity 中其它对象，
            // 在 JPA 中可能导致数据库数据被修改，造成安全漏洞。
            boolean simpleField = (includes.isEmpty() || includes.contains(name))
                    && (excludes.isEmpty() || !excludes.contains(name))
                    && !(value instanceof Collection || value instanceof Map);
            if (simpleField) {
                // 将空串转为 null。因为 oracle 的 varchar2 会自动把空串转为 null，在此统一各数据库行为。
                if (value instanceof String && "".equals(value)) {
                    value = null;
                }
                validFields.put(name, value);
            }
        }
        if (!validFields.isEmpty()) {
            try {
                // 增加 JAVA8 日期 的处理、忽略未识别的属性。
                ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                String json = mapper.writeValueAsString(validFields);
                mapper.readerForUpdating(bean).readValue(json);
            } catch (IOException e) {
                throw new IllegalStateException("copy fields to entity properties error.", e);
            }
        }
    }

    public static <T> void copy(Map<String, Object> fields, T bean) {
        copy(fields, bean, Collections.emptyList(), Collections.emptyList());
    }

    public static <T> void copy(Map<String, Object> fields, T bean, String... excludes) {
        copy(fields, bean, Collections.emptyList(), Arrays.asList(excludes));
    }

    public static <T> void copyIncludes(Map<String, Object> fields, T bean, String... includes) {
        copy(fields, bean, Arrays.asList(includes), Collections.emptyList());
    }

    private Entities() {
        throw new IllegalStateException("Utility class");
    }
}
