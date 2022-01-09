package com.ofwise.util.function;

/**
 * 三个参数的函数接口
 *
 * @author PONY
 * @see java.util.function.Function
 * @see java.util.function.BiFunction
 */
@FunctionalInterface
public interface Function3<T, U, V, R> {
    /**
     * 执行函数
     *
     * @param t 第一个参数
     * @param u 第二个参数
     * @param v 第三个参数
     * @return 函数返回值
     */
    R apply(T t, U u, V v);
}
