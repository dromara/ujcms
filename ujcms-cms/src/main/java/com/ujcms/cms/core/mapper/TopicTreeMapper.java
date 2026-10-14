package com.ujcms.cms.core.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import com.ujcms.common.db.tree.TreeRelationMapper;

/**
 * 专题树形结构 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface TopicTreeMapper extends TreeRelationMapper {
    /**
     * 查询是否存在上下级关系
     *
     * @param ancestorId   祖先ID
     * @param descendantId 后代ID
     * @return 上下级关系数量
     */
    int countByAncestorId(@Param("ancestorId") Long ancestorId, @Param("descendantId") Long descendantId);

    /**
     * 获取后代ID
     *
     * @param ancestorId 祖先ID
     * @return 后代ID列表
     */
    List<Long> listByAncestorId(@Param("ancestorId") Long ancestorId);
}
