package com.ujcms.common.function;

import java.util.Objects;

/**
 * 三个参数的消费函数接口
 *
 * @author PONY
 * @see java.util.function.Consumer
 * @see java.util.function.BiConsumer
 */
@FunctionalInterface
public interface Consumer3<T, U, V> {
    /**
     * 执行函数
     *
     * @param t 第一个参数
     * @param u 第二个参数
     * @param v 第三个参数
     */
    void accept(T t, U u, V v);

    /**
     * 组合两个消费函数：先执行当前函数，再用同一组参数执行 {@code after}。
     * 当前函数抛出异常时不再执行 {@code after}，异常原样抛给调用方。
     *
     * @param after 随后执行的函数，不能为 null
     * @return 依次执行两个函数的组合函数
     */
    default Consumer3<T, U, V> andThen(Consumer3<? super T, ? super U, ? super V> after) {
        Objects.requireNonNull(after);

        return (t, u, v) -> {
            accept(t, u, v);
            after.accept(t, u, v);
        };
    }
}
