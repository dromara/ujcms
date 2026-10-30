package com.ujcms.cms.core.mapper;

import com.ujcms.cms.core.domain.GroupContribute;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户组投稿权限 Mapper
 *
 * @author PONY
 */
@Mapper
@Repository
public interface GroupContributeMapper {
    /**
     * 插入数据
     *
     * @param bean 实体对象
     * @return 插入条数
     */
    int insert(GroupContribute bean);

    /**
     * 根据 栏目ID 查询用户组ID列表
     *
     * @param channelId 栏目ID
     * @return 用户组ID列表
     */
    List<Long> listGroupByChannelId(@Param("channelId") Long channelId);

    /**
     * 根据 用户组ID 查询栏目ID列表
     *
     * @param groupId 用户组ID
     * @param siteId  站点ID
     * @return 栏目ID列表
     */
    List<Long> listChannelByGroupId(@Param("groupId") Long groupId, @Nullable @Param("siteId") Long siteId);

    /**
     * 站点下的全部投稿用户组行，供一次装入整站的栏目 × 用户组矩阵（可投栏目列表不逐栏目查询）
     *
     * @param siteId 站点ID
     * @return 投稿用户组行
     */
    List<GroupContribute> listBySiteId(@Param("siteId") Long siteId);

    /**
     * 根据 栏目ID 删除数据
     *
     * @param channelId 栏目ID
     * @return 删除条数
     */
    int deleteByChannelId(@Param("channelId") Long channelId);

    /**
     * 根据 用户组ID 删除数据
     *
     * @param groupId 用户组ID
     * @return 删除条数
     */
    int deleteByGroupId(@Param("groupId") Long groupId);

    /**
     * 根据站点ID删除数据
     *
     * @param siteId 站点ID
     * @return 被删除的数据条数
     */
    int deleteBySiteId(@Param("siteId") Long siteId);
}
