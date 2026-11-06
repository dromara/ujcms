package com.ujcms.common.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * 内存列表分页工具类。
 * <p>
 * 用于来源不是数据库、无法下推分页条件的列表（如存储点的目录列表）：先取全量再切片，返回与 MyBatis 分页同形的
 * Spring Data {@link Page}，前端按同一套 {@code content} / {@code page.totalElements} 读取。
 *
 * @author PONY
 */
public final class Pages {
    /**
     * 把完整列表切成一页。
     *
     * @param list     已排序的完整列表
     * @param page     页码，从 1 开始（与 MyBatis PageHelper 一致）
     * @param pageSize 每页条数，须大于 0
     * @return 该页内容；越界的页码返回空内容，总数不变
     */
    public static <T> Page<T> of(List<T> list, int page, int pageSize) {
        if (page < 1 || pageSize < 1) {
            throw new IllegalArgumentException("page and pageSize must be positive: " + page + ", " + pageSize);
        }
        int total = list.size();
        // 用 long 计算偏移：页码不设上限，int 乘法会溢出
        int from = (int) Math.min((long) (page - 1) * pageSize, total);
        int to = (int) Math.min((long) from + pageSize, total);
        return new PageImpl<>(new ArrayList<>(list.subList(from, to)), PageRequest.of(page - 1, pageSize), total);
    }

    /**
     * 工具类不需要实例化
     */
    private Pages() {
    }
}
