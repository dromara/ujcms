package com.ujcms.commons.db.tree;

import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * 树形 Mapper
 *
 * @author PONY
 */
public interface TreeEntityMapper<T> {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(T bean);

    /**
     * 更新数据
     *
     * @param bean 实体对象
     * @return 更新条数
     */
    int update(T bean);

    /**
     * 删除数据
     *
     * @param id 主键ID
     * @return 删除条数
     */
    int delete(Long id);

    /**
     * 根据主键获取数据
     *
     * @param id 主键ID
     * @return 实体对象。没有找到数据，则返回 {@code null}
     */
    @Nullable
    T select(Long id);

    /**
     * 更新树形结构深度
     *
     * @param id    节点ID
     * @param depth 深度
     * @return 更新条数
     */
    int updateDepth(@Param("id") Long id, @Param("depth") short depth);

    /**
     * 更新上级节点
     *
     * @param id       节点ID
     * @param parentId 上级节点ID
     * @return 更新条数
     */
    int updateParentId(@Param("id") Long id, @Param("parentId") @Nullable Long parentId);

    /**
     * 获取最大的序号值
     *
     * @param parentId 上级ID。可以为 {@code null}
     * @param siteId   站点ID。可以为 {@code null}
     * @return 最大序号值。可能为 {@code null}
     */
    @Nullable
    Integer maxOrder(@Param("parentId") @Nullable Long parentId, @Param("siteId") @Nullable Long siteId);

    /**
     * 更新序号值
     *
     * @param begin  开始序号（包含自身）
     * @param end    结束序号（包含自身）
     * @param num    变更数值。可以为负数
     * @param siteId 站点ID。可以为 {@code null}
     * @return 更新条数
     */
    int updateOrder(@Param("begin") int begin, @Param("end") int end, @Param("num") int num, @Param("siteId") @Nullable Long siteId);

    /**
     * 批量更新序号值和深度
     *
     * @param list 待更新数据列表
     * @return 更新条数
     */
    int batchUpdateOrderAndDepth(List<? extends TreeEntity> list);

    /**
     * 更新序号值
     *
     * @param parentId 上级ID
     * @param num      变更数值。可以为负数
     * @return 更新条数
     */
    int updateOrderByParentId(@Param("parentId") Long parentId, @Param("num") int num);
}
