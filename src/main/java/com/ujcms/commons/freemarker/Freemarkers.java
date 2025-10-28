package com.ujcms.commons.freemarker;

import com.ujcms.commons.query.QueryUtils;
import com.ujcms.commons.web.Dates;
import freemarker.template.AdapterTemplateModel;
import freemarker.template.TemplateBooleanModel;
import freemarker.template.TemplateCollectionModelEx;
import freemarker.template.TemplateDateModel;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateHashModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateModelIterator;
import freemarker.template.TemplateNumberModel;
import freemarker.template.TemplateScalarModel;
import freemarker.template.TemplateSequenceModel;
import no.api.freemarker.java8.time.TemporalDialerAdapter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.NumberUtils;

import java.time.OffsetDateTime;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * FreeMarker帮助类
 * <p>
 * 参考freemarker官方文档示例：http://freemarker.org/docs/pgui_datamodel_directive.html
 * <p>
 * Map<String, TemplateModel> params 如果标签参数值为 null，则 key 存在，但 value 为 null。
 * 如 [@ArticleList foo=bar /] 其中 bar 是一个不存在值，则 key=foo,value=null。
 * 如 [@ArticleList foo=bar! /] 其中 bar 是一个不存在值，则 key=foo,value=DefaultToExpression.EmptyStringAndSequenceAndHash
 * <p>
 * 同样的 Method 的 List args 也可以为 null。
 * <p>
 * 所以 TemplateModel model 参数允许为 null 是必要的。
 *
 * @author liufang
 */
public class Freemarkers {
    /**
     * 分隔符
     */
    public static final String SPLIT = ",";

    @SuppressWarnings("unchecked")
    @Nullable
    public static <T> T getObject(@Nullable TemplateModel model, Class<T> targetClass)
            throws TemplateModelException {
        if (model == null) {
            return null;
        }
        if (model instanceof AdapterTemplateModel) {
            return (T) ((AdapterTemplateModel) model).getAdaptedObject(targetClass);
        }
        throw new TemplateModelException(String.format(NOT_MATCH, targetClass.getName(), model));
    }

    @Nullable
    public static <T> T getObject(Map<String, TemplateModel> params, String name, Class<T> targetClass)
            throws TemplateModelException {
        return getObject(params.get(name), targetClass);
    }

    @Nullable
    private static List<String> getList(TemplateModel model) throws TemplateModelException {
        if (model instanceof TemplateSequenceModel seqModel) {
            int length = seqModel.size();
            List<String> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(getString(seqModel.get(i)));
            }
            return list;
        }
        if (model instanceof TemplateCollectionModelEx collectionModel) {
            List<String> list = new ArrayList<>(collectionModel.size());
            TemplateModelIterator it = collectionModel.iterator();
            while (it.hasNext()) {
                list.add(getString(it.next()));
            }
            return list;
        }
        return null;
    }

    @Nullable
    public static String getString(@Nullable TemplateModel model) throws TemplateModelException {
        if (model == null) {
            return null;
        }
        if (model instanceof TemplateScalarModel scalarModel) {
            return scalarModel.getAsString();
        }
        if (model instanceof TemplateNumberModel numberModel) {
            // 如果是数字，也转换成字符串
            return numberModel.getAsNumber().toString();
        }
        List<String> list = getList(model);
        if (list != null) {
            return QueryUtils.joinByComma(list);
        }
        throw new TemplateModelException(String.format(NOT_MATCH, "String", model));
    }

    public static <T> T required(@Nullable T value, String name) {
        boolean blankString = value instanceof String string && StringUtils.isBlank(string);
        if (value == null || blankString) {
            throw new IllegalArgumentException(String.format(REQUIRED, name));
        }
        return value;
    }

    public static String getStringRequired(@Nullable TemplateModel model, String name) throws TemplateModelException {
        return required(getString(model), name);
    }

    @Nullable
    public static List<String> getStrings(@Nullable TemplateModel model) throws TemplateModelException {
        if (model == null) {
            return null;
        }
        List<String> list = getList(model);
        if (list != null) {
            return list;
        }
        return Optional.ofNullable(StringUtils.split(getString(model), SPLIT)).map(Arrays::asList).orElse(null);
    }

    public static <T> Collection<T> required(@Nullable Collection<T> list, String name) {
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException(String.format(REQUIRED, name));
        }
        return list;
    }

    public static Collection<String> getStringsRequired(@Nullable TemplateModel model, String name)
            throws TemplateModelException {
        return required(getStrings(model), name);
    }

    @Nullable
    public static <T extends Number> T getNumber(@Nullable TemplateModel model, Class<T> targetClass)
            throws TemplateModelException {
        if (model == null) {
            return null;
        }
        if (model instanceof TemplateNumberModel numberModel) {
            Number number = numberModel.getAsNumber();
            return NumberUtils.convertNumberToTargetClass(number, targetClass);
        }
        if (model instanceof TemplateScalarModel scalarModel) {
            String text = scalarModel.getAsString();
            if (StringUtils.isNotBlank(text)) {
                try {
                    return NumberUtils.parseNumber(text, targetClass);
                } catch (NumberFormatException e) {
                    throw new TemplateModelException(String.format(NOT_MATCH, "Number", model), e);
                }
            }
            return null;
        }
        throw new TemplateModelException(String.format(NOT_MATCH, "Number", model));
    }

    public static <T extends Number> T getNumberRequired
            (@Nullable TemplateModel model, String name, Class<T> targetClass) throws TemplateModelException {
        return required(getNumber(model, targetClass), name);
    }

    @Nullable
    public static <T extends Number> List<T> getNumbers(@Nullable TemplateModel model, Class<T> targetClass)
            throws TemplateModelException {
        if (model == null) {
            return null;
        }
        if (model instanceof TemplateSequenceModel seqModel) {
            int length = seqModel.size();
            List<T> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(getNumber(seqModel.get(i), targetClass));
            }
            return list;
        }
        if (model instanceof TemplateCollectionModelEx collectionModel) {
            List<T> list = new ArrayList<>(collectionModel.size());
            TemplateModelIterator it = collectionModel.iterator();
            while (it.hasNext()) {
                list.add(getNumber(it.next(), targetClass));
            }
            return list;
        }
        String text = getString(model);
        if (text == null) {
            return null;
        }
        String[] strings = StringUtils.split(text, SPLIT);
        List<T> list = new ArrayList<>(strings.length);
        try {
            for (String s : strings) {
                list.add(NumberUtils.parseNumber(s, targetClass));
            }
        } catch (NumberFormatException e) {
            throw new TemplateModelException(String.format(NOT_MATCH, "Number Array", model));
        }
        return list;
    }

    @Nullable
    public static Long getLong(@Nullable TemplateModel model) throws TemplateModelException {
        return getNumber(model, Long.class);
    }

    public static Long getLongRequired(@Nullable TemplateModel model, String name) throws TemplateModelException {
        return getNumberRequired(model, name, Long.class);
    }

    @Nullable
    public static List<Long> getLongs(@Nullable TemplateModel model) throws TemplateModelException {
        return getNumbers(model, Long.class);
    }

    @Nullable
    public static Integer getInteger(@Nullable TemplateModel model) throws TemplateModelException {
        return getNumber(model, Integer.class);
    }

    public static Integer getIntegerRequired(@Nullable TemplateModel model, String name) throws TemplateModelException {
        return getNumberRequired(model, name, Integer.class);
    }

    @Nullable
    public static List<Integer> getIntegers(@Nullable TemplateModel model) throws TemplateModelException {
        return getNumbers(model, Integer.class);
    }

    @Nullable
    public static Boolean getBoolean(@Nullable TemplateModel model) throws TemplateModelException {
        if (model == null) {
            return null;
        }
        if (model instanceof TemplateBooleanModel booleanModel) {
            return booleanModel.getAsBoolean();
        }
        if (model instanceof TemplateScalarModel scalarModel) {
            String text = scalarModel.getAsString();
            if (StringUtils.isNotBlank(text)) {
                return Boolean.valueOf(text);
            }
            return null;
        }
        throw new TemplateModelException(String.format(NOT_MATCH, "Boolean", model));
    }

    public static Boolean getBoolean(@Nullable TemplateModel model, boolean defaultValue)
            throws TemplateModelException {
        return Optional.ofNullable(getBoolean(model)).orElse(defaultValue);
    }

    public static Boolean getBooleanRequired(@Nullable TemplateModel model, String name)
            throws TemplateModelException {
        return required(getBoolean(model), name);
    }

    @Nullable
    public static Temporal getTemporal(@Nullable TemplateModel model) throws TemplateModelException {
        if (model == null) {
            return null;
        }
        if (model instanceof TemporalDialerAdapter temporalDialerAdapter) {
            return temporalDialerAdapter.getObject();
        }
        throw new TemplateModelException(String.format(NOT_MATCH, "Temporal", model));
    }

    public static Temporal getTemporalRequired(@Nullable TemplateModel model, String name) throws TemplateModelException {
        return required(getTemporal(model), name);
    }

    @Nullable
    public static OffsetDateTime getOffsetDateTime(@Nullable TemplateModel model) throws TemplateModelException {
        if (model == null) {
            return null;
        }
        if (model instanceof TemporalDialerAdapter temporalDialerAdapter) {
            Temporal temporal = temporalDialerAdapter.getObject();
            return Dates.from(temporal);
        }
        if (model instanceof TemplateDateModel dateModel) {
            Date date = dateModel.getAsDate();
            return Dates.ofDate(date);
        }
        if (model instanceof TemplateScalarModel scalarModel) {
            String text = scalarModel.getAsString();
            if (StringUtils.isBlank(text)) {
                return null;
            }
            return Dates.parse(text);
        }
        throw new TemplateModelException(String.format(NOT_MATCH, "OffsetDateTime", model));
    }

    public static OffsetDateTime getOffsetDateTimeRequired(@Nullable TemplateModel model, String name)
            throws TemplateModelException {
        return required(getOffsetDateTime(model), name);
    }

    public static void requireLoopVars(TemplateModel[] loopVars) throws TemplateModelException {
        if (loopVars.length < 1) {
            throw new TemplateModelException("Loop variable is required.");
        }
    }

    public static void requireBody(@Nullable TemplateDirectiveBody body) {
        if (body == null) {
            throw new IllegalStateException("Missing body.");
        }
    }

    public static Map<String, Object> getMap(@Nullable TemplateModel model) throws TemplateModelException {
        Map<String, Object> map = new HashMap<>(16);
        fillMap(model, map::put);
        return map;
    }

    public static Map<String, String> getStringMap(@Nullable TemplateModel model) throws TemplateModelException {
        Map<String, String> map = new HashMap<>(16);
        fillMap(model, map::put);
        return map;
    }

    private static void fillMap(@Nullable TemplateModel model, BiConsumer<String, String> consumer)
            throws TemplateModelException {
        if (model instanceof TemplateHashModelEx hashModel) {
            TemplateModelIterator it = hashModel.keys().iterator();
            while (it.hasNext()) {
                String key = Freemarkers.getString(it.next());
                String value = Freemarkers.getString(hashModel.get(key));
                if (StringUtils.isNotBlank(value)) {
                    consumer.accept(key, value);
                }
            }
        }
    }

    private static final String REQUIRED = "The '%s' parameter is required";
    private static final String NOT_MATCH = "The '%s' parameter cannot convert a '%s':";

    private Freemarkers() {
        throw new IllegalStateException("Utility class");
    }
}
