package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Org;
import com.ujcms.commons.db.tree.TreeEntityMapper;
import com.ujcms.commons.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 组织 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface OrgMapper extends TreeEntityMapper<Org> {
    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo          查询条件
     * @param ancestorId         上级ID
     * @param isQueryHasChildren 是否查询包含子组织
     * @return 数据列表
     */
    List<Org> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo,
                        @Nullable @Param("ancestorId") Long ancestorId,
                        @Param("isQueryHasChildren") boolean isQueryHasChildren);

    /**
     * 获取有权限的 组织ID 列表
     *
     * @param roleIds 角色ID 列表
     * @param orgIds  组织ID 列表
     * @return 栏目ID 列表
     */
    List<Long> listPermissions(@Param("roleIds") Collection<Long> roleIds,
                               @Param("orgIds") Collection<Long> orgIds);

    /**
     * 根据父组织ID获取子组织列表
     *
     * @param parentId 父组织ID
     * @return 子组织列表
     */
    List<Org> listChildren(@Param("parentId") Long parentId);

    /**
     * 根据用户ID获取扩展组织列表
     *
     * @param userId 用户ID
     * @return 数据列表
     */
    List<Org> listByUserId(@Param("userId") Long userId);

    /**
     * 获取所有组织。用于整理树形结构，只获取必须字段
     *
     * @return 数据列表
     */
    List<Org> listForTidy();

    /**
     * 根据 组织 ID 列表 查询文章权限是否存在
     *
     * @param channelId 栏目 ID
     * @param orgIds   组织 ID 列表
     * @return 1:存在; 0:不存在
     */
    int existsByArticleOrgId(@Param("channelId") Long channelId, @Param("orgIds") Collection<Long> orgIds);

}