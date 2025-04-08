package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.support.Constants;
import com.ujcms.commons.freemarker.Freemarkers;
import com.ujcms.commons.query.QueryParser;
import com.ujcms.commons.query.QueryUtils;
import com.ujcms.commons.web.PageUrlResolver;
import freemarker.core.Environment;
import freemarker.ext.servlet.FreemarkerServlet;
import freemarker.ext.servlet.HttpRequestHashModel;
import freemarker.template.AdapterTemplateModel;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;

import javax.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.*;

import static com.ujcms.cms.core.support.Frontends.*;
import static com.ujcms.commons.query.QueryUtils.QUERY_PREFIX;

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

    public static Sort getSort(Map<String, ?> params) {
        String orderBy = getString(params, ORDER_BY);
        if (StringUtils.isBlank(orderBy)) {
            return Sort.unsorted();
        }
        String[] orders = StringUtils.split(orderBy, ',');
        List<Sort.Order> orderList = new ArrayList<>(orders.length);
        for (String order : orders) {
            int index = order.indexOf('_');
            Sort.Direction direction = Sort.Direction.ASC;
            String property = order;
            if (index != -1) {
                if ("desc".equalsIgnoreCase(order.substring(index + 1))) {
                    direction = Sort.Direction.DESC;
                }
                property = order.substring(0, index);
            }
            orderList.add(new Sort.Order(direction, property));
        }
        return Sort.by(orderList.toArray(new Sort.Order[0]));
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

    public static int getOffset(Map<String, ?> params) {
        Integer offset = getInteger(params, OFFSET);
        if (offset != null && offset > 0) {
            return offset;
        }
        return 0;
    }

    /**
     * 最大数据条数。总共获取几条数据。
     */
    public static final String LIMIT = "limit";

    public static int getLimit(Map<String, ?> params) {
        Integer limit = getInteger(params, LIMIT);
        if (limit != null && limit > 0 && limit <= Constants.MAX_PAGE_SIZE) {
            return limit;
        }
        return Constants.MAX_PAGE_SIZE;
    }

    public static Map<String, Object> getQueryMap(Map<String, TemplateModel> params) throws TemplateModelException {
        Map<String, Object> queryMap = new HashMap<>(16);
        for (Map.Entry<String, TemplateModel> param : params.entrySet()) {
            String name = param.getKey();
            TemplateModel value = param.getValue();
            if (name.equals(QUERY_PREFIX)) {
                queryMap.putAll(Freemarkers.getStringMap(params.get(QUERY_PREFIX)));
            } else if (name.startsWith(QUERY_PREFIX)) {
                // Freemarker 标签参数不支持 @ 和 -，用 $$ 代替 @ ，用 __ 代替 -
                name = name.substring(QUERY_PREFIX.length())
                        .replace("$$", "@").replace("__", "-");
                queryMap.put(name, Freemarkers.getString(value));
            }
        }
        return queryMap;
    }

    /**
     * 自定义字段
     */
    public static final String CUSTOMS = "customs";

    public static Map<String, String> getCustomsQueryMap(Map<String, TemplateModel> params)
            throws TemplateModelException {
        return Freemarkers.getStringMap(params.get(CUSTOMS));
    }

    @Nullable
    public static String getString(Map<String, ?> params, String name) {
        Object obj = params.get(name);
        if (obj instanceof TemplateModel) {
            try {
                return Freemarkers.getString((TemplateModel) obj);
            } catch (TemplateModelException e) {
                throw new IllegalStateException(e);
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
                throw new IllegalStateException(e);
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
                throw new IllegalStateException(e);
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
                throw new IllegalStateException(e);
            }
        }
        return QueryUtils.parseNumbers(obj, targetClass);
    }

    @Nullable
    public static Float getFloat(Map<String, ?> params, String name) {
        return getNumber(params, name, Float.class);
    }

    public static Float getFloat(Map<String, ?> params, String name, Float defaultValue) {
        return Optional.ofNullable(getFloat(params, name)).orElse(defaultValue);
    }

    @Nullable
    public static Double getDouble(Map<String, ?> params, String name) {
        return getNumber(params, name, Double.class);
    }

    public static Double getDouble(Map<String, ?> params, String name, Double defaultValue) {
        return Optional.ofNullable(getDouble(params, name)).orElse(defaultValue);
    }

    @Nullable
    public static Short getShort(Map<String, ?> params, String name) {
        return getNumber(params, name, Short.class);
    }

    public static Short getShort(Map<String, ?> params, String name, Short defaultValue) {
        return Optional.ofNullable(getShort(params, name)).orElse(defaultValue);
    }

    public static Short getShortRequired(Map<String, ?> params, String name) {
        return getNumberRequired(params, name, Short.class);
    }

    /**
     * 获取短整形数组。参数不存在则返回null；参数存在但为空串，则返回长度为0的数组。
     */
    @Nullable
    public static Collection<Short> getShorts(Map<String, ?> params, String name) {
        return getNumbers(params, name, Short.class);
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

    public static Long getLong(Map<String, ?> params, String name, Long defaultValue) {
        return Optional.ofNullable(getLong(params, name)).orElse(defaultValue);
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
    public static Boolean getBooleanDefault(Map<String, ?> params, String name, boolean defaultValue) {
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
                throw new IllegalStateException(e);
            }
        }
        return QueryUtils.parseBoolean(obj);
    }

    public static Boolean getBoolean(Map<String, ?> params, String name, boolean defaultValue) {
        return Optional.ofNullable(getBoolean(params, name)).orElse(defaultValue);
    }

    @Nullable
    public static OffsetDateTime getOffsetDateTime(Map<String, ?> params, String name) {
        Object obj = params.get(name);
        if (obj instanceof TemplateModel) {
            try {
                return Freemarkers.getOffsetDateTime((TemplateModel) obj);
            } catch (TemplateModelException e) {
                throw new IllegalStateException(e);
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
    private static final ThreadLocal<Integer> TOTAL_PAGES_HOLDER = new ThreadLocal<>();

    /**
     * 栏目页分页生成静态页时，无法得到分页标签得到的总页数，需要通过此方法设置总页数，便于生成静态页代码获取。
     */
    public static void setTotalPages(Integer totalPages) {
        TOTAL_PAGES_HOLDER.set(totalPages);
    }

    public static Integer getTotalPages() {
        Integer totalPages = TOTAL_PAGES_HOLDER.get();
        if (totalPages == null || totalPages < 1) {
            totalPages = 1;
        }
        return totalPages;
    }

    public static void clearTotalPages() {
        TOTAL_PAGES_HOLDER.remove();
    }

    private Directives() {
    }
}
