package com.ujcms.cms.core.mapper;

import com.ujcms.commons.db.tree.TreeRelationMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 站点树形结构 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface SiteTreeMapper extends TreeRelationMapper {
    /**
     * 获取后代ID
     *
     * @param ancestorId 祖先ID
     * @return 后代ID列表
     */
    List<Long> listByAncestorId(Long ancestorId);
}