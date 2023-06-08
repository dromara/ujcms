package com.ujcms.commons.function;

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
     * Returns a composed {@code Consumer3} that performs, in sequence, this
     * operation followed by the {@code after} operation. If performing either
     * operation throws an exception, it is relayed to the caller of the
     * composed operation.  If performing this operation throws an exception,
     * the {@code after} operation will not be performed.
     *
     * @param after the operation to perform after this operation
     * @return a composed {@code Consumer3} that performs in sequence this
     * operation followed by the {@code after} operation
     * @throws NullPointerException if {@code after} is null
     */
    default Consumer3<T, U, V> andThen(Consumer3<? super T, ? super U, ? super V> after) {
        Objects.requireNonNull(after);

        return (t, u, v) -> {
            accept(t, u, v);
            after.accept(t, u, v);
        };
    }
}
