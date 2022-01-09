package com.ofwise.util.db;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * MyBatis 工具类
 *
 * @author PONY
 */
public class MyBatis {
    /**
     * 将 MyBatis page helper 的 Page 对象转换成 spring data 的 Page对象。
     */
    public static <T> Page<T> toPage(com.github.pagehelper.Page<T> page) {
        // spring data page number 从 0 开始
        return new PageImpl<>(page.getResult(), PageRequest.of(page.getPageNum() - 1,
                page.getPageSize()), page.getTotal());
    }

    /**
     * 工具类不需要实例化
     */
    private MyBatis() {
    }
}
