package com.ujcms.core.web.support;

import com.ofwise.util.freemarker.Freemarkers;
import com.ofwise.util.query.QueryParser;
import com.ofwise.util.query.QueryUtils;
import com.ofwise.util.web.PageUrlResolver;
import com.ujcms.core.support.Constants;
import freemarker.core.Environment;
import freemarker.ext.servlet.FreemarkerServlet;
import freemarker.ext.servlet.HttpRequestHashModel;
import freemarker.template.AdapterTemplateModel;
import freemarker.template.TemplateHashModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import freemarker.template.TemplateModelIterator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import javax.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.ofwise.util.query.QueryUtils.CUSTOMS_PREFIX;
import static com.ofwise.util.query.QueryUtils.QUERY_PREFIX;
import static com.ujcms.core.support.Frontends.*;

/**
 * 标签 工具类
 *
 * @author PONY
 */
public class Directives {
    public static String getUrl(Environment env) throws TemplateModelException {
        String url = Freemarkers.getString(env.getDataModel().get(URL));
        return url == null ? "" : url;
    }

    @Nullable
    public static String getQueryString(Environment env) throws TemplateModelException {
        return Freemarkers.getString(env.getDataModel().get(QUERY_STRING));
    }

    @Nullable
    public static PageUrlResolver getPageUrlResolver(Environment env) throws TemplateModelException {
        TemplateModel model = env.getDataModel().get(PAGE_URL_RESOLVER);
        if (model instanceof AdapterTemplateModel) {
            return (PageUrlResolver) ((AdapterTemplateModel) model)
                    .getAdaptedObject(PageUrlResolver.class);
        }
        return null;
    }

    @Nullable
    public static HttpServletRequest findRequest(Environment env) throws TemplateModelException {
        TemplateModel model = env.getDataModel().get(FreemarkerServlet.KEY_REQUEST);
        if (model instanceof HttpRequestHashModel) {
            return ((HttpRequestHashModel) model).getRequest();
        }
        return null;
    }

    public static HttpServletRequest getRequest(Environment env) throws TemplateModelException {
        return Optional.ofNullable(findRequest(env)).orElseThrow(() ->
                new TemplateModelException("'" + FreemarkerServlet.KEY_REQUEST + "' not found in DataModel"));
    }

    /**
     * 排序
     */
    public static final String ORDER_BY = "orderBy";

    public static void handleOrderBy(Map<String, Object> queryMap, Map<String, ?> params, String defaultOrderBy) {
        queryMap.put(QueryParser.ORDER_BY, getString(params, ORDER_BY, defaultOrderBy));
    }

    public static void handleOrderBy(Map<String, Object> queryMap, Map<String, ?> params) {
        queryMap.put(QueryParser.ORDER_BY, getString(params, ORDER_BY));
    }

    public static int getPage(Map<String, TemplateModel> params, Environment env) throws TemplateModelException {
        Integer page = Freemarkers.getInteger(params.get(PAGE));
        if (page == null) {
            page = Freemarkers.getInteger(env.getDataModel().get(PAGE));
        }
        return Constants.validPage(page);
    }

    public static int getPage(Map<String, String> params) {
        Integer page = QueryUtils.parseInteger(params.get(PAGE));
        return Constants.validPage(page);
    }

    public static int getPageSize(Map<String, TemplateModel> params, Environment env, @Nullable Integer defaultPageSize)
            throws TemplateModelException {
        Integer pageSize = Freemarkers.getInteger(params.get(PAGE_SIZE));
        if (pageSize == null) {
            pageSize = Freemarkers.getInteger(env.getDataModel().get(PAGE_SIZE));
        }
        if (pageSize == null) {
            return Constants.validPageSize(defaultPageSize);
        }
        return Constants.validPageSize(pageSize);
    }

    public static int getPageSize(Map<String, TemplateModel> params, Environment env) throws TemplateModelException {
        return getPageSize(params, env, null);
    }

    public static int getPageSize(Map<String, String> params) {
        Integer pageSize = QueryUtils.parseInteger(params.get(PAGE_SIZE));
        return Constants.validPageSize(pageSize);
    }

    /**
     * 数据偏移量。从第几条数据开始获取。从 0 开始计数。
     */
    public static final String OFFSET = "offset";

    @Nullable
    public static Integer findOffset(Map<String, ?> params) {
        Integer offset = getInteger(params, OFFSET);
        if (offset != null && offset < 1) {
            return null;
        }
        return offset;
    }

    public static int getOffset(Map<String, ?> params) {
        return Optional.ofNullable(findOffset(params)).orElse(0);
    }

    /**
     * 最大数据条数。总共获取几条数据。
     */
    public static final String LIMIT = "limit";

    @Nullable
    public static Integer findLimit(Map<String, ?> params) {
        Integer limit = getInteger(params, LIMIT);
        if (limit != null) {
            // limit 非 null 时的合法处理与 pageSize 一致
            return Constants.validPageSize(limit);
        }
        return limit;
    }

    public static int getLimit(Map<String, ?> params) {
        return Optional.ofNullable(findLimit(params)).orElse(Constants.MAX_PAGE_SIZE);
    }

    public static Map<String, Object> getQueryMap(Map<String, TemplateModel> params) throws TemplateModelException {
        return getQueryMap(params, QUERY_PREFIX);
    }

    /**
     * 自定义字段
     */
    public static final String CUSTOMS = "customs";

    public static Map<String, String> getCustomsQueryMap(Map<String, TemplateModel> params)
            throws TemplateModelException {
        Map<String, String> queryMap = getQueryMap(params, CUSTOMS_PREFIX).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toString()));
        TemplateModel model = params.get(CUSTOMS);
        if (model instanceof TemplateHashModelEx) {
            TemplateHashModelEx hashModel = (TemplateHashModelEx) model;
            TemplateModelIterator it = hashModel.keys().iterator();
            while (it.hasNext()) {
                String key = Freemarkers.getString(it.next());
                String value = Freemarkers.getString(hashModel.get(key));
                if (StringUtils.isNotBlank(value)) {
                    queryMap.put(key, value);
                }
            }
        }
        return queryMap;
    }

    private static Map<String, Object> getQueryMap(Map<String, TemplateModel> params, String prefix)
            throws TemplateModelException {
        Map<String, Object> queryMap = new HashMap<>(16);
        for (Map.Entry<String, TemplateModel> param : params.entrySet()) {
            String name = param.getKey();
            TemplateModel value = param.getValue();
            if (name.startsWith(prefix)) {
                queryMap.put(name.substring(prefix.length()), Freemarkers.getString(value));
            }
        }
        return queryMap;
    }

    @Nullable
    public static String getString(Map<String, ?> params, String name) {
        Object obj = params.get(name);
        if (obj instanceof TemplateModel) {
            try {
                return Freemarkers.getString((TemplateModel) obj);
            } catch (TemplateModelException e) {
                throw new RuntimeException(e);
            }
        }
        return QueryUtils.parseString(obj);
    }

    public static String getString(Map<String, ?> params, String name, String defaultValue) {
        String value = getString(params, name);
        return value == null ? defaultValue : value;
    }

    public static String getStringRequired(Map<String, ?> params, String name) {
        return Freemarkers.required(getString(params, name), name);
    }

    @Nullable
    public static Collection<String> getStrings(Map<String, ?> params, String name) {
        Object obj = params.get(name);
        if (obj instanceof TemplateModel) {
            try {
                return Freemarkers.getStrings((TemplateModel) obj);
            } catch (TemplateModelException e) {
                throw new RuntimeException(e);
            }
        }
        return QueryUtils.parseStrings(obj);
    }

    public static Collection<String> getStringsRequired(Map<String, ?> params, String name) {
        return Freemarkers.required(getStrings(params, name), name);
    }

    @Nullable
    public static <T extends Number> T getNumber(Map<String, ?> params, String name, Class<T> targetClass) {
        Object obj = params.get(name);
        if (obj instanceof TemplateModel) {
            try {
                return Freemarkers.getNumber((TemplateModel) obj, targetClass);
            } catch (TemplateModelException e) {
                throw new RuntimeException(e);
            }
        }
        return QueryUtils.parseNumber(obj, targetClass);
    }

    public static <T extends Number> T getNumberRequired(Map<String, ?> params, String name, Class<T> targetClass) {
        return Freemarkers.required(getNumber(params, name, targetClass), name);
    }

    @Nullable
    public static <T extends Number> Collection<T> getNumbers
            (Map<String, ?> params, String name, Class<T> targetClass) {
        Object obj = params.get(name);
        if (obj instanceof TemplateModel) {
            try {
                return Freemarkers.getNumbers((TemplateModel) obj, targetClass);
            } catch (TemplateModelException e) {
                throw new RuntimeException(e);
            }
        }
        return QueryUtils.parseNumbers(obj, targetClass);
    }

    @Nullable
    public static Integer getInteger(Map<String, ?> params, String name) {
        return getNumber(params, name, Integer.class);
    }

    public static Integer getInteger(Map<String, ?> params, String name, Integer defaultValue) {
        return Optional.ofNullable(getInteger(params, name)).orElse(defaultValue);
    }

    public static Integer getIntegerRequired(Map<String, ?> params, String name) {
        return getNumberRequired(params, name, Integer.class);
    }

    /**
     * 获取整数数组。参数不存在则返回null；参数存在但为空串，则返回长度为0的数组。
     */
    @Nullable
    public static Collection<Integer> getIntegers(Map<String, ?> params, String name) {
        return getNumbers(params, name, Integer.class);
    }


    @Nullable
    public static Long getLong(Map<String, ?> params, String name) {
        return getNumber(params, name, Long.class);
    }

    public static Long getLongRequired(Map<String, ?> params, String name) {
        return getNumberRequired(params, name, Long.class);
    }

    @Nullable
    public static Collection<Long> getLongs(Map<String, ?> params, String name) {
        return getNumbers(params, name, Long.class);
    }

    /**
     * 空值为默认值，all 则为 null ，同时支持 false 和 true
     */
    @Nullable
    public static Boolean getDefaultBoolean(Map<String, ?> params, String name, boolean defaultValue) {
        String value = getString(params, name);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        String all = "all";
        if (all.equalsIgnoreCase(value)) {
            return null;
        }
        return Boolean.parseBoolean(value);
    }


    @Nullable
    public static Boolean getBoolean(Map<String, ?> params, String name) {
        Object obj = params.get(name);
        if (obj instanceof TemplateModel) {
            try {
                return Freemarkers.getBoolean((TemplateModel) obj);
            } catch (TemplateModelException e) {
                throw new RuntimeException(e);
            }
        }
        return QueryUtils.parseBoolean(obj);
    }

    public static Boolean getBoolean(Map<String, ?> params, String name, Boolean defaultValue) {
        Boolean value = getBoolean(params, name);
        return value == null ? defaultValue : value;
    }

    public static Boolean getBooleanRequired(Map<String, ?> params, String name) {
        return Freemarkers.required(getBoolean(params, name), name);
    }

    @Nullable
    public static OffsetDateTime getOffsetDateTime(Map<String, ?> params, String name) {
        Object obj = params.get(name);
        if (obj instanceof TemplateModel) {
            try {
                return Freemarkers.getOffsetDateTime((TemplateModel) obj);
            } catch (TemplateModelException e) {
                throw new RuntimeException(e);
            }
        }
        return QueryUtils.parseDate(obj);
    }

    public static OffsetDateTime getOffsetDateTimeRequired(Map<String, ?> params, String name) {
        return Freemarkers.required(getOffsetDateTime(params, name), name);
    }

    /**
     * 页数线程变量
     */
    private static ThreadLocal<Integer> totalPagesHolder = new ThreadLocal<>();

    /**
     * 栏目页分页生成静态页时，无法得到分页标签得到的总页数，需要通过此方法设置总页数，便于生成静态页代码获取。
     */
    public static void setTotalPages(Integer totalPages) {
        totalPagesHolder.set(totalPages);
    }

    public static Integer getTotalPages() {
        Integer totalPages = totalPagesHolder.get();
        if (totalPages == null || totalPages < 1) {
            totalPages = 1;
        }
        return totalPages;
    }

    public static void clearTotalPages() {
        totalPagesHolder.remove();
    }

    private Directives() {
    }
}
