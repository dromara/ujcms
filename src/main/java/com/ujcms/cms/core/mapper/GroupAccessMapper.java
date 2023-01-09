package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.GroupAccess;
import com.ujcms.util.query.QueryInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户组访问权限 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface GroupAccessMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(GroupAccess bean);

    /**
     * 删除数据
     *
     * @param groupId   用户组ID
     * @param channelId 栏目ID
     * @return 删除条数
     */
    int delete(@Param("groupId") Integer groupId, @Param("channelId") Integer channelId);

    /**
     * 根据查询条件获取列表
     *
     * @param queryInfo 查询条件
     * @return 数据列表
     */
    List<GroupAccess> selectAll(@Nullable @Param("queryInfo") QueryInfo queryInfo);

    /**
     * 根据 用户组ID 查询栏目ID列表
     *
     * @param groupId 用户组ID
     * @param siteId  站点ID
     * @return 栏目ID列表
     */
    List<Integer> listChannelByGroupId(@Param("groupId") Integer groupId, @Nullable @Param("siteId") Integer siteId);

    /**
     * 根据 栏目ID 查询用户组ID列表
     *
     * @param channelId 栏目ID
     * @param siteId    站点ID
     * @return 用户组ID列表
     */
    List<Integer> listGroupByChannelId(@Param("channelId") Integer channelId, @Nullable @Param("siteId") Integer siteId);

    /**
     * 根据 栏目ID 删除数据
     *
     * @param channelId 栏目ID
     * @return 删除条数
     */
    int deleteByChannelId(@Param("channelId") Integer channelId);

    /**
     * 根据 用户组ID 删除数据
     *
     * @param groupId 用户组ID
     * @param siteId  站点ID
     * @return 删除条数
     */
    int deleteByGroupId(@Param("groupId") Integer groupId, @Nullable @Param("siteId") Integer siteId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(Integer siteId);
}