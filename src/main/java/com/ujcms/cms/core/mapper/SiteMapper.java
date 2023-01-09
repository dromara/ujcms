package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.util.db.tree.TreeEntityMapper;
import com.ujcms.util.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 站点 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface SiteMapper extends TreeEntityMapper<Site> {
    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo        查询条件
     * @param customsCondition 自定义字段查询条件
     * @param parentId         上级ID
     * @param fullOrgId        组织ID。属于该组织的上级或下级的站点都符合条件
     * @return 数据列表
     */
    List<Site> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo,
                         @Nullable @Param("customsCondition") List<QueryInfo.WhereCondition> customsCondition,
                         @Nullable @Param("parentId") Integer parentId,
                         @Nullable @Param("fullOrgId") Integer fullOrgId);

    /**
     * 根据父站点ID获取子站点列表
     *
     * @param parentId 父站点ID
     * @return 子站点列表
     */
    List<Site> listChildren(Integer parentId);

    /**
     * 根据组织ID查找站点
     *
     * @param orgId 组织ID
     * @return 站点列表
     */
    List<Site> listByOrgId(Integer orgId);

    /**
     * 根据组织ID查找站点ID
     *
     * @param orgId 组织ID
     * @return 站点ID列表
     */
    List<Integer> listIdByOrgId(Integer orgId);

    /**
     * 根据域名查找站点
     *
     * @param domain 域名
     * @return 站点
     */
    @Nullable
    Site findByDomain(String domain);

    /**
     * 根据子目录查找站点
     *
     * @param subDir 别名
     * @return 站点
     */
    @Nullable
    Site findBySubDir(String subDir);
}