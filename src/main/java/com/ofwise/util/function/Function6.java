package com.ofwise.util.function;

/**
 * 六个参数的函数接口
 *
 * @author PONY
 * @see java.util.function.Function
 * @see java.util.function.BiFunction
 */
@FunctionalInterface
public interface Function6<T, U, V, X, Y, Z, R> {
    /**
     * 执行函数
     *
     * @param t 第一个参数
     * @param u 第二个参数
     * @param v 第三个参数
     * @param x 第四个参数
     * @param y 第五个参数
     * @param z 第六个参数
     * @return 函数返回值
     */
    R apply(T t, U u, V v, X x, Y y, Z z);
}
