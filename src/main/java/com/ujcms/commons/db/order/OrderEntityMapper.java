package com.ujcms.commons.db.order;

import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;

/**
 * 可排序实体类 Mapper
 *
 * @author PONY
 */
public interface OrderEntityMapper {
    /**
     * 根据主键获取数据
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    OrderEntity select(Long id);

    /**
     * 向上移动
     *
     * @param from 从某个order值
     * @param to   到某个order值
     * @return 更新条数
     */
    int moveUp(@Param("from") long from, @Param("to") long to);

    /**
     * 向下移动
     *
     * @param from 从某个order值
     * @param to   到某个order值
     * @return 更新条数
     */
    int moveDown(@Param("from") long from, @Param("to") long to);

    /**
     * 更新排序
     *
     * @param id    ID
     * @param order 排序值
     * @return 更新条数
     */
    int updateOrder(@Param("id") Long id, @Param("order") long order);
}
