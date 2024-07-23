package com.ujcms.commons.db;

import org.springframework.core.ConfigurableObjectInputStream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * MyBatis 工具类
 *
 * @author PONY
 */
public class MyBatis {
    /**
     * 将 MyBatis page helper 的 Page 对象转换成 spring data 的 Page对象。
     */
    public static <T> Page<T> springPage(com.github.pagehelper.Page<T> page) {
        // spring data page number 从 0 开始
        return new PageImpl<>(page.getResult(), PageRequest.of(page.getPageNum() - 1,
                page.getPageSize()), page.getTotal());
    }

    /**
     * 由于使用了devtools，反序列化要考虑ClassLoader的问题
     * <p>
     * https://docs.spring.io/spring-boot/docs/2.7.5/reference/htmlsingle/#using.devtools.restart.limitations
     */
    public static <T> T deserialize(final byte[] bytes) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (ObjectInputStream in = new ConfigurableObjectInputStream(new ByteArrayInputStream(bytes), classLoader)) {
            @SuppressWarnings("unchecked") final T obj = (T) in.readObject();
            return obj;
        } catch (final ClassNotFoundException | IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * 工具类不需要实例化
     */
    private MyBatis() {
    }
}
