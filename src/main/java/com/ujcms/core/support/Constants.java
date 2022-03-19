package com.ujcms.core.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.lang.Nullable;

/**
 * 常量类
 *
 * @author PONY
 */
public final class Constants {
    /**
     * 共享模板路径
     */
    public static final String TEMPLATE_SHARE = "share";

    //以下非final常量可以被配置文件覆盖

    /**
     * 模板文件后缀
     */
    public static final String TEMPLATE_SUFFIX = ".html";
    /**
     * 模板资源路径（css、js、img等）
     */
    public static final String TEMPLATE_FILES = "_files";
    /**
     * 默认每页条数
     */
    public static final int DEFAULT_PAGE_SIZE = 10;
    /**
     * 默认每页最大条数。oracle 中 in 的最大个数是 1000。
     * 考虑到有可能使用 lucene 检索出 ID，再到数据库中获取数据，每页最大条数为 1000 是比较合适的。
     */
    public static final int MAX_PAGE_SIZE = 1000;

    public static int validPageSize(@Nullable Integer pageSize) {
        return validPageSize(pageSize, DEFAULT_PAGE_SIZE);
    }

    public static int validPageSize(@Nullable Integer pageSize, int defaultPageSize) {
        if (pageSize == null || pageSize < 1) {
            return defaultPageSize;
        }
        if (pageSize > MAX_PAGE_SIZE) {
            return MAX_PAGE_SIZE;
        }
        return pageSize;
    }

    public static int validPage(@Nullable Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    public static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .addModule(new JavaTimeModule())
            .build();

    /**
     * 常量类不允许创建对象
     */
    private Constants() {
    }
}
