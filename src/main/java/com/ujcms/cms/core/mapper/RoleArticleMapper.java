package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.RoleArticle;
import com.ujcms.util.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 角色文章权限 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface RoleArticleMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(RoleArticle bean);

    /**
     * 删除数据
     *
     * @param channelId 栏目ID
     * @param roleId    角色ID
     * @return 删除条数
     */
    int delete(@Param("channelId") Integer channelId, @Param("roleId") Integer roleId);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<RoleArticle> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据 角色ID 查询栏目ID列表
     *
     * @param roleId 角色ID
     * @param siteId 站点ID
     * @return 栏目ID列表
     */
    List<Integer> listChannelByRoleId(@Param("roleId") Integer roleId, @Nullable @Param("siteId") Integer siteId);

    /**
     * 根据 栏目ID 查询角色ID列表
     *
     * @param channelId 栏目ID
     * @param siteId    站点ID
     * @return 角色ID列表
     */
    List<Integer> listRoleByChannelId(@Param("channelId") Integer channelId, @Nullable @Param("siteId") Integer siteId);

    /**
     * 根据 角色ID列表 查询栏目ID列表
     *
     * @param roleIds 角色ID列表
     * @param siteId  站点ID
     * @return 栏目ID列表
     */
    List<Integer> listChannelByRoleIds(@Param("roleIds") Collection<Integer> roleIds,
                                       @Nullable @Param("siteId") Integer siteId);

    /**
     * 根据 栏目ID 删除数据
     *
     * @param channelId 栏目ID
     * @return 删除条数
     */
    int deleteByChannelId(@Param("channelId") Integer channelId);

    /**
     * 根据 角色ID 删除数据
     *
     * @param roleId 角色ID
     * @param siteId 站点ID
     * @return 删除条数
     */
    int deleteByRoleId(@Param("roleId") Integer roleId, @Nullable @Param("siteId") Integer siteId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Integer siteId);
}