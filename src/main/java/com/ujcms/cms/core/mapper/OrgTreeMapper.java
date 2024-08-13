package com.ujcms.cms.core.mapper;

import com.ujcms.commons.db.tree.TreeRelationMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 组织树形结构 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface OrgTreeMapper extends TreeRelationMapper {
    /**
     * 查询用户组织ID和站点组织ID是否存在上下级关系
     *
     * @param userOrgId 用户组织ID
     * @param siteOrgId 站点组织ID
     * @return 匹配数量。匹配数量大于0，代表存在上下级关系
     */
    int countByOrgId(@Param("userOrgId") Long userOrgId, @Param("siteOrgId") Long siteOrgId);

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