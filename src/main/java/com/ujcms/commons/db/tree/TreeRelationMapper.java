package com.ujcms.commons.db.tree;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 树形关系 Mapper
 *
 * @author PONY
 */
public interface TreeRelationMapper {
    /**
     * 插入数据
     *
     * @param ancestorId   祖先ID
     * @param descendantId 后代ID
     * @return 插入条数
     */
    int save(@Param("ancestorId") Long ancestorId, @Param("descendantId") Long descendantId);

    /**
     * 批量插入数据
     *
     * @param list 树形关系列表
     * @return 插入条数
     */
    int saveBatch(@Param("list") List<? extends TreeRelation> list);

    /**
     * 删除数据
     *
     * @param ancestorId   祖先ID
     * @param descendantId 后代ID
     * @return 删除条数
     */
    int delete(@Param("ancestorId") Long ancestorId, @Param("descendantId") Long descendantId);

    /**
     * 删除数据
     *
     * @param ancestorId 祖先ID
     * @return 删除条数
     */
    int deleteByAncestorId(@Param("ancestorId") Long ancestorId);

    /**
     * 删除所有关系
     *
     * @return 删除条数
     */
    int deleteAll();

    /**
     * 根据站点ID删除关系
     *
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteBySiteId(@Param("siteId") Long siteId);

    /**
     * 移出数据
     *
     * @param id 待移出的ID
     * @return 删除条数
     */
    int move(Long id);

    /**
     * 移入数据
     *
     * @param id       待移入的ID
     * @param parentId 移动到父节点ID
     * @return 插入条数
     */
    int append(@Param("id") Long id, @Param("parentId") Long parentId);

    /**
     * 加入数据
     *
     * @param id       待加入的ID
     * @param parentId 加入到父节点ID
     * @return 插入条数
     */
    int add(@Param("id") Long id, @Param("parentId") Long parentId);
}
